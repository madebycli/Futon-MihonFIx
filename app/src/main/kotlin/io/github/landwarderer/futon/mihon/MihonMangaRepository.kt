// Repository compatibility behavior adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon

import androidx.collection.LruCache
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.parser.CachingMangaRepository
import io.github.landwarderer.futon.mihon.compat.MihonRequestContext
import io.github.landwarderer.futon.mihon.model.snapshot
import io.github.landwarderer.futon.mihon.state.MihonChapterSnapshotStore
import io.github.landwarderer.futon.mihon.state.MihonSnapshotPersistence
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.model.asContentPage
import io.github.landwarderer.futon.mihon.model.getPublicContentUrl
import io.github.landwarderer.futon.mihon.model.toContent
import io.github.landwarderer.futon.mihon.model.toContentChapter
import io.github.landwarderer.futon.mihon.model.toContentListFilter
import io.github.landwarderer.futon.mihon.model.toDomainContent
import io.github.landwarderer.futon.mihon.model.toManga
import io.github.landwarderer.futon.mihon.model.toMangaListFilterOptions
import io.github.landwarderer.futon.mihon.model.toMangaPage
import io.github.landwarderer.futon.mihon.model.toMihonChapter
import io.github.landwarderer.futon.mihon.model.toMihonManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder as ContentSortOrder

/**
 * Repository that adapts a Mihon CatalogueSource to Futon's MangaRepository interface.
 *
 * Keep the original Mihon model objects around while they cross Futon's domain model. Current
 * extensions can store source-specific state in SManga/SChapter fields that cannot be represented
 * by Futon's Manga/MangaChapter types. Reconstructing those objects from scratch loses that state
 * and can break subsequent details, chapter and page calls.
 */
class MihonMangaRepository(
    override val source: MihonMangaSource,
    cache: MemoryContentCache,
    private val snapshotPersistence: MihonSnapshotPersistence? = null,
) : CachingMangaRepository(cache) {

    companion object {
        private const val TAG = "MihonMangaRepository"
        private const val MANGA_SNAPSHOT_CACHE_SIZE = 100
        private const val MAX_RELATED_QUERIES = 6
    }

    private var lastOffset = -1
    private var currentPage = 1
    private val mangaSnapshots = LruCache<String, SManga>(MANGA_SNAPSHOT_CACHE_SIZE)

    val mihonSource = source.catalogueSource

    override val sortOrders: Set<ContentSortOrder> = buildSet {
        add(ContentSortOrder.POPULARITY)
        if (mihonSource.supportsLatest) {
            add(ContentSortOrder.UPDATED)
        }
    }

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isMultipleTagsSupported = true,
            isSearchWithFiltersSupported = true,
        )

    override var defaultSortOrder: ContentSortOrder = ContentSortOrder.POPULARITY

    override suspend fun getList(
        offset: Int,
        order: ContentSortOrder?,
        filter: MangaListFilter?,
    ): List<Manga> = withContext(Dispatchers.IO) {
        if (offset == 0) {
            currentPage = 1
        } else if (offset > lastOffset) {
            currentPage++
        }
        lastOffset = offset

        val page = currentPage
        val query = filter?.query
        val hasFilters = filter?.let {
            it.query?.isNotBlank() == true || it.tags.isNotEmpty() || it.tagsExclude.isNotEmpty()
        } ?: false

        val mangasPage = rethrowMihonWrappedExceptions {
            withMihonSourceContext {
                when {
                    hasFilters -> mihonSource.getSearchManga(
                        page,
                        query ?: "",
                        filter?.toMihonFilterList() ?: FilterList(),
                    )
                    order == ContentSortOrder.UPDATED && mihonSource.supportsLatest -> {
                        mihonSource.getLatestUpdates(page)
                    }
                    else -> mihonSource.getPopularManga(page)
                }
            }
        }

        snapshotPersistence?.putMangas(source.sourceId, mangasPage.mangas)
        mangasPage.mangas.map { sContent ->
            rememberMihonManga(sContent)
            sContent.toDomainContent(
                source = source,
                publicUrl = getPublicUrl(sContent),
            ).toManga()
        }
    }

    /** Details loads in progress, by manga url. */
    private val detailsInFlight = ConcurrentHashMap<String, CompletableDeferred<Manga>>()

    /**
     * Some extensions (Comix reads the chapter list through a WebView) refuse a second [mihonSource] update of the same
     * manga while one is running. A second caller, such as the details screen opened again, joins the running load.
     */
    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val mine = CompletableDeferred<Manga>()
        val running = detailsInFlight.putIfAbsent(manga.url, mine)
        if (running != null) return running.await()
        try {
            val details = loadDetails(manga)
            mine.complete(details)
            return details
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            detailsInFlight.remove(manga.url, mine)
        }
    }

    private suspend fun loadDetails(manga: Manga): Manga = withContext(Dispatchers.IO) {
        val sContent = mangaSnapshots.get(manga.url)?.snapshot()
            ?: snapshotPersistence?.getManga(source.sourceId, manga.url)?.also(::rememberMihonManga)
            ?: manga.toContent(source).toMihonManga()
        val existingChapters = manga.chapters.orEmpty().map { chapter ->
            MihonChapterSnapshotStore.get(source.sourceId, chapter.url)
                ?: snapshotPersistence?.getChapter(source.sourceId, chapter.url)?.also(::rememberMihonChapter)
                ?: chapter.toContentChapter(source).toMihonChapter()
        }

        suspend fun fetchUpdate() = rethrowMihonWrappedExceptions {
            withMihonSourceContext {
                mihonSource.getMangaUpdate(
                    manga = sContent,
                    chapters = existingChapters,
                    fetchDetails = true,
                    fetchChapters = true,
                )
            }
        }

        val update = fetchUpdateWithRetry { fetchUpdate() }

        snapshotPersistence?.putChapters(source.sourceId, update.chapters)
        val details = update.manga
        val chapters = update.chapters.asReversed()
            .mapIndexed { index, sChapter ->
                val fallbackNumber = if (sChapter.chapter_number >= 0) {
                    sChapter.chapter_number
                } else {
                    (index + 1).toFloat()
                }
                sChapter.toContentChapter(source, fallbackNumber).also {
                    rememberMihonChapter(sChapter)
                }
            }
            // Preserve each source group even when its chapter range is complementary.
            // The user-controlled details projection decides whether those groups are merged.
            .sortedBy { it.number }

        details.applyDetailFallbacks(sContent)
        rememberMihonManga(details)
        snapshotPersistence?.putManga(source.sourceId, details)

        details.toDomainContent(
            source = source,
            chapters = chapters,
            publicUrl = getPublicUrl(details),
        ).copy(id = manga.id).toManga()
    }

    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val sChapter = MihonChapterSnapshotStore.get(source.sourceId, chapter.url)
            ?: snapshotPersistence?.getChapter(source.sourceId, chapter.url)?.also(::rememberMihonChapter)
            ?: chapter.toContentChapter(source).toMihonChapter()
        val pages = rethrowMihonWrappedExceptions {
            withMihonSourceContext {
                mihonSource.getPageList(sChapter)
            }
        }

        pages.map { page ->
            if (mihonSource !is HttpSource) {
                return@map page.asContentPage(source, sChapter).toMangaPage()
            }

            val headers = try {
                if (!page.imageUrl.isNullOrBlank()) {
                    val h = MihonRequestContext.withSourceBlocking(source) {
                        mihonSource.getPageHeaders(page)
                    }
                    buildMap {
                        for (i in 0 until h.size) {
                            put(h.name(i), h.value(i))
                        }
                    }
                } else {
                    emptyMap()
                }
            } catch (_: Exception) {
                emptyMap()
            }

            page.asContentPage(source, sChapter, headers).let { contentPage ->
                // The extension's own page index travels in the url: Comix and others resolve pages that share one
                // chapter url by Page.index (Kototoro 038b8826). The page id is a hashed cache id, not an index.
                val index = page.index
                val updatedPage = if (page.imageUrl.isNullOrBlank() && page.url.isNotBlank()) {
                    contentPage.copy(
                        url = "mihon://resolve?page_url=${java.net.URLEncoder.encode(page.url, "UTF-8")}&index=$index",
                    )
                } else if (!page.imageUrl.isNullOrBlank() && page.url.isNotBlank() && page.url != page.imageUrl) {
                    contentPage.copy(
                        url = "mihon://image?page_url=${java.net.URLEncoder.encode(page.url, "UTF-8")}&image_url=${java.net.URLEncoder.encode(page.imageUrl!!, "UTF-8")}&index=$index",
                    )
                } else {
                    contentPage
                }
                updatedPage.toMangaPage()
            }
        }
    }

    override suspend fun getPageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
        val url = page.url
        if (!url.startsWith("mihon://")) return@withContext url

        val uri = android.net.Uri.parse(url)
        if (url.startsWith("mihon://image")) {
            val imageUrl = uri.getQueryParameter("image_url")
            if (!imageUrl.isNullOrBlank()) return@withContext imageUrl
        } else if (url.startsWith("mihon://resolve")) {
            val pageUrl = uri.getQueryParameter("page_url")
            if (!pageUrl.isNullOrBlank()) {
                val httpSource = mihonSource as? HttpSource ?: return@withContext pageUrl
                return@withContext rethrowMihonWrappedExceptions {
                    withMihonSourceContext {
                        httpSource.getImageUrl(page.toMihonPage(imageUrl = null))
                    }
                }
            }
        }
        url
    }

    override suspend fun getFilterOptions(): MangaListFilterOptions {
        val mihonFilters = try {
            withMihonSourceContext {
                mihonSource.getFilterList()
            }
        } catch (_: Exception) {
            FilterList()
        }
        return MihonFilterMapper.mapOptions(mihonFilters, source).toMangaListFilterOptions()
    }

    private fun MangaListFilter.toMihonFilterList(): FilterList {
        val mihonFilters = try {
            MihonRequestContext.withSourceBlocking(source) {
                mihonSource.getFilterList()
            }
        } catch (_: Exception) {
            return FilterList()
        }
        MihonFilterMapper.updateMihonFilters(mihonFilters, this.toContentListFilter())
        return mihonFilters
    }

    fun getRequestHeaders(): Map<String, String> {
        val httpSource = mihonSource as? HttpSource ?: return emptyMap()
        val headers = MihonRequestContext.withSourceBlocking(source) { httpSource.headers }
        return buildMap {
            for (i in 0 until headers.size) {
                put(headers.name(i), headers.value(i))
            }
        }
    }

    fun getImageClient(): OkHttpClient? {
        val httpSource = mihonSource as? HttpSource ?: return null
        return MihonRequestContext.withSourceBlocking(source) { httpSource.client }
    }

    fun createPageRequest(pageUrl: String, page: MangaPage): Request {
        if (pageUrl.isBlank()) return defaultImageRequest("http://localhost")
        val httpSource = mihonSource as? HttpSource ?: return defaultImageRequest(pageUrl)
        val sPage = page.toMihonPage(pageUrl)
        return MihonRequestContext.withSourceBlocking(source) {
            httpSource.imageRequest(sPage)
        }
    }

    fun createCoverRequest(imageUrl: String): Request {
        val httpSource = mihonSource as? HttpSource ?: return defaultImageRequest(imageUrl)
        val request = try {
            MihonRequestContext.withSourceBlocking(source) {
                httpSource.imageRequest(Page(0, imageUrl = imageUrl))
            }
        } catch (_: Throwable) {
            return defaultImageRequest(imageUrl)
        }
        if (
            request.header("Referer") == null &&
            (imageUrl.contains("hitomi.la") || imageUrl.contains("gold-usergeneratedcontent.net"))
        ) {
            return request.newBuilder().header("Referer", "https://hitomi.la/").build()
        }
        return request
    }

    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> {
        if (mihonSource.supportsRelatedMangas && !mihonSource.disableRelatedMangas) {
            val manga = mangaSnapshots.get(seed.url)?.snapshot()
                ?: snapshotPersistence?.getManga(source.sourceId, seed.url)?.also(::rememberMihonManga)
                ?: seed.toContent(source).toMihonManga()
            val related = rethrowMihonWrappedExceptions {
                withMihonSourceContext {
                    mihonSource.fetchRelatedMangaList(manga)
                }
            }
            snapshotPersistence?.putMangas(source.sourceId, related)
            return related.map { relatedManga ->
                rememberMihonManga(relatedManga)
                relatedManga.toDomainContent(
                    source = source,
                    publicUrl = getPublicUrl(relatedManga),
                ).toManga()
            }
        }

        if (mihonSource.disableRelatedMangasBySearch) return emptyList()
        return findRelatedBySearch(seed)
    }

    suspend fun getFavicons(): org.koitharu.kotatsu.parsers.model.Favicons {
        return org.koitharu.kotatsu.parsers.model.Favicons(emptyList(), "")
    }

    private suspend fun findRelatedBySearch(seed: Manga): List<Manga> {
        val queries = buildRelatedQueries(seed)
        var best: List<Manga>? = null
        for (query in queries) {
            val candidates = try {
                getList(
                    offset = 0,
                    order = defaultSortOrder,
                    filter = MangaListFilter(query = query),
                ).filter { candidate ->
                    candidate.id != seed.id &&
                        candidate.url != seed.url &&
                        candidate.publicUrl != seed.publicUrl &&
                        (
                            candidate.title.contains(query, ignoreCase = true) ||
                                candidate.altTitles.any { it.contains(query, ignoreCase = true) }
                            )
                }
            } catch (_: Exception) {
                emptyList()
            }
            if (candidates.isNotEmpty() && (best == null || candidates.size < best.size)) {
                best = candidates
            }
        }
        return best.orEmpty()
    }

    private fun buildRelatedQueries(seed: Manga): List<String> {
        return linkedSetOf<String>().apply {
            add(seed.title)
            addAll(seed.altTitles)
            addAll(seed.title.split(Regex("\\s+")).filter { it.length > 1 })
            seed.altTitles.forEach { title ->
                addAll(title.split(Regex("\\s+")).filter { it.length > 1 })
            }
        }.map(String::trim)
            .filter(String::isNotBlank)
            .take(MAX_RELATED_QUERIES)
    }

    private fun getPublicUrl(manga: SManga): String {
        val httpSource = mihonSource as? HttpSource ?: return ""
        return MihonRequestContext.withSourceBlocking(source) {
            httpSource.getPublicContentUrl(manga)
        }
    }

    private suspend fun <T> withMihonSourceContext(block: suspend () -> T): T {
        return MihonRequestContext.withSource(source, block)
    }

    private fun rememberMihonManga(manga: SManga) {
        val url = manga.readMihonField("") { url }.takeIf(String::isNotBlank) ?: return
        mangaSnapshots.put(url, manga.snapshot())
    }

    private fun rememberMihonChapter(chapter: eu.kanade.tachiyomi.source.model.SChapter) {
        MihonChapterSnapshotStore.put(source.sourceId, chapter.url, chapter)
    }

    private fun SManga.applyDetailFallbacks(original: SManga) {
        val originalUrl = original.readMihonField("") { url }
        url = originalUrl

        if (readMihonField("") { title }.isBlank()) {
            title = original.readMihonField("Unknown") { title }.ifBlank { "Unknown" }
        }

        val detailsThumbnail = readMihonField<String?>(null) { thumbnail_url }
        val originalThumbnail = original.readMihonField<String?>(null) { thumbnail_url }
        if (
            (detailsThumbnail.isNullOrBlank() || detailsThumbnail == originalUrl) &&
            !originalThumbnail.isNullOrBlank()
        ) {
            thumbnail_url = originalThumbnail
        }

        val detailsMemo = readMihonField(JsonObject(emptyMap())) { memo }
        if (detailsMemo.isEmpty()) {
            val originalMemo = original.readMihonField(JsonObject(emptyMap())) { memo }
            if (originalMemo.isNotEmpty()) {
                memo = originalMemo
            }
        }
    }

    private fun SManga.snapshot(): SManga = SManga.create().also { snapshot ->
        snapshot.url = readMihonField("") { url }
        snapshot.title = readMihonField("") { title }
        snapshot.artist = readMihonField<String?>(null) { artist }
        snapshot.author = readMihonField<String?>(null) { author }
        snapshot.description = readMihonField<String?>(null) { description }
        snapshot.genre = readMihonField<String?>(null) { genre }
        snapshot.status = readMihonField(SManga.UNKNOWN) { status }
        snapshot.thumbnail_url = readMihonField<String?>(null) { thumbnail_url }
        snapshot.update_strategy = readMihonField(snapshot.update_strategy) { update_strategy }
        snapshot.initialized = readMihonField(false) { initialized }
        snapshot.genres = readMihonField(emptyList()) { genres }
        snapshot.altTitles = readMihonField(emptyList()) { altTitles }
        snapshot.banner = readMihonField<String?>(null) { banner }
        snapshot.contentRating = readMihonField(SManga.ContentRating.SAFE) { contentRating }
        snapshot.score = readMihonField<Int?>(null) { score }
        snapshot.readingMode = readMihonField<SManga.ReadingMode?>(null) { readingMode }
        snapshot.memo = readMihonField(JsonObject(emptyMap())) { memo }
    }

    private fun MangaPage.toMihonPage(imageUrl: String?): Page {
        var pageIndex = id.toInt()
        var originalPageUrl = url
        var originalImageUrl = imageUrl
        if (url.startsWith("mihon://")) {
            val uri = android.net.Uri.parse(url)
            // Wrapped pages keep the extension's index separately from the chapter-scoped cache id.
            pageIndex = wrappedMihonPageIndex(url) ?: 0
            uri.getQueryParameter("page_url")?.takeIf(String::isNotBlank)?.let {
                originalPageUrl = it
            }
            if (url.startsWith("mihon://image")) {
                uri.getQueryParameter("image_url")?.takeIf(String::isNotBlank)?.let {
                    originalImageUrl = it
                }
            }
        }
        return Page(
            index = pageIndex,
            url = originalPageUrl,
            imageUrl = originalImageUrl,
        )
    }

    private fun defaultImageRequest(url: String): Request = Request.Builder().url(url).build()

    private inline fun <T> SManga.readMihonField(defaultValue: T, getter: SManga.() -> T): T {
        return try {
            getter()
        } catch (_: UninitializedPropertyAccessException) {
            defaultValue
        } catch (_: AbstractMethodError) {
            defaultValue
        } catch (_: NoSuchMethodError) {
            defaultValue
        }
    }

    private inline fun <T> rethrowMihonWrappedExceptions(block: () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            throw unwrapMihonFailure(e)
        }
    }
}

/** The extension's own page index carried by a wrapped `mihon://` page url, or null for any other url. */
internal fun wrappedMihonPageIndex(url: String): Int? {
    if (!url.startsWith("mihon://")) return null
    return Regex("""[?&]index=(-?\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
}

/**
 * Runs [load] again after a short pause when it failed for a reason that usually passes: a network error, or an
 * extension that stopped reading a paged chapter list early ("Chapter list is incomplete").
 */
internal suspend fun <T> fetchUpdateWithRetry(maxRetries: Int = 2, load: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (attempt >= maxRetries || !e.isTransientDetailsFailure()) throw e
            attempt++
            delay(500L * attempt)
        }
    }
}

internal fun Throwable.isTransientDetailsFailure(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 5) {
        if (current is java.io.IOException || current.message?.contains("incomplete", ignoreCase = true) == true) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}
