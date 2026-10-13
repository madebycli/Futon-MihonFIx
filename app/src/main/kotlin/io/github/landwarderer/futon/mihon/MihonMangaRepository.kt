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
import kotlinx.coroutines.Dispatchers
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

    override suspend fun getDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
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

        val update = try {
            fetchUpdate()
        } catch (e: Exception) {
            val ioException = when {
                e is java.io.IOException -> e
                e.cause is java.io.IOException -> e.cause as java.io.IOException
                else -> null
            }
            if (ioException != null) {
                kotlinx.coroutines.delay(500)
                fetchUpdate()
            } else {
                throw e
            }
        }

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

        pages.mapIndexed { index, page ->
            if (mihonSource !is HttpSource) {
                return@mapIndexed page.asContentPage(source, sChapter).toMangaPage()
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
                        httpSource.getImageUrl(Page(0, pageUrl))
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

    p…1547 tokens truncated…kotatsu.parsers.model.MangaSource
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/** Mihon/Keiyoushi network host with the same default-client ABI and Cloudflare semantics. */
class MihonNetworkHelper(
    baseClient: OkHttpClient,
    val cookieJar: CookieJar,
    private val webViewExecutor: WebViewExecutor? = null,
    private val clearanceSolver: WebViewClearanceSolver? = null,
    private val solveCoordinator: CloudflareSolveCoordinator? = null,
) : NetworkHelper() {

    private val zstdRuntimeDependency = Zstd

    override val client: OkHttpClient = run {
        val builder = baseClient.newBuilder().apply {
            interceptors().clear()
            networkInterceptors().clear()
            cookieJar(cookieJar)
        }

        // Keep the exact three Mihon/Keiyoushi default interceptors first.
        builder.addInterceptor(UncaughtExceptionInterceptor())
        builder.addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
        builder.addInterceptor(
            CloudflareInterceptor(
                delegate = Interceptor { chain -> chain.proceed(chain.request()) },
            ),
        )

        baseClient.interceptors.forEach { interceptor ->
            if (isCompatibleInterceptor(interceptor) && !isDefaultMihonInterceptor(interceptor)) {
                builder.addInterceptor(interceptor)
            }
        }
        baseClient.networkInterceptors.forEach { interceptor ->
            if (isCompatibleInterceptor(interceptor)) builder.addNetworkInterceptor(interceptor)
        }

        // Real Cloudflare handling. A genuine challenge is solved by Kototoro's current strategy:
        // a fresh off-screen WebView, shared per host, followed by one retry of the source request.
        builder.addInterceptor { chain ->
            val originalRequest = chain.request()
            val request = originalRequest
            val response = chain.proceed(request)
            val challengeUrl = request.toChallengeUrl()

            when (CloudFlareHelper.checkResponseForProtection(response)) {
                CloudFlareHelper.PROTECTION_BLOCKED -> response.closeThrowing(
                    CloudFlareBlockedException(
                        url = challengeUrl,
                        source = request.tag(ContentSource::class.java) as MangaSource?,
                    ),
                )

                CloudFlareHelper.PROTECTION_CAPTCHA -> {
                    val host = request.url.host.lowercase()
                    val previousClearance = getClearanceCookie(request)

                    if (tryResolveAutomatically(request, challengeUrl)) {
                        response.close()
                        val retryRequest = originalRequest
                        val retryClearance = getClearanceCookie(retryRequest)
                        Log.i(
                            TAG,
                            "Cloudflare offscreen solve completed host=$host, clearanceChanged=${retryClearance != null && retryClearance != previousClearance}; retrying source request",
                        )

                        val retryResponse = chain.proceed(retryRequest)
                        when (CloudFlareHelper.checkResponseForProtection(retryResponse)) {
                            CloudFlareHelper.PROTECTION_NOT_DETECTED -> {
                                recentChallengeAttempts.remove(host)
                                Log.i(TAG, "Cloudflare retry succeeded host=$host status=${retryResponse.code}")
                                return@addInterceptor retryResponse
                            }

                            CloudFlareHelper.PROTECTION_BLOCKED -> retryResponse.closeThrowing(
                                CloudFlareBlockedException(
                                    url = challengeUrl,
                                    source = retryRequest.tag(ContentSource::class.java) as MangaSource?,
                                ),
                            )

                            else -> {
                                Log.w(TAG, "Cloudflare retry still protected host=$host status=${retryResponse.code}")
                                throwUnresolvedChallenge(retryResponse, retryRequest, challengeUrl, host)
                            }
                        }
                    }

                    throwUnresolvedChallenge(response, request, challengeUrl, host)
                }

                else -> response
            }
        }

        // Diagnostics deliberately run after the challenge interceptor so device logs show the
        // extension-visible request/response and distinguish a CF challenge from API errors such
        // as Comix's JSON {"message":"Invalid token."} response.
        builder.addInterceptor { chain ->
            val request = chain.request()
            val requestCookies = cookieJar.loadForRequest(request.url)
            val cfClearanceCookie = requestCookies.firstOrNull { it.name == "cf_clearance" }?.value
            val cookieNames = requestCookies.joinToString(",") { it.name }
            Log.d(
                TAG,
                "RequestMeta: host=${request.url.host}, ua=${request.header("User-Agent")}, referer=${request.header("Referer")}, origin=${request.header("Origin")}, hasCfClearance=${cfClearanceCookie != null}, cfClearance=${maskCookieValue(cfClearanceCookie)}, cookies=[$cookieNames]",
            )
            Log.d(TAG, "Request: ${request.method} ${request.url}")

            val response = chain.proceed(request)
            val responseCode = response.code
            val contentType = response.header("Content-Type")
            Log.d(
                TAG,
                "Response: $responseCode, Content-Type: $contentType, cf-ray=${response.header("cf-ray")}, cf-mitigated=${response.header("cf-mitigated")}, server=${response.header("server")}, URL: ${request.url}",
            )

            if (!response.isSuccessful) {
                val source = response.body.source()
                source.request(200)
                val buffer = source.buffer.clone()
                val preview = buffer.readUtf8(minOf(200, buffer.size))
                Log.w(TAG, "Non-successful response ($responseCode) preview: $preview")
            }
            response
        }

        builder.build()
    }

    private fun isCompatibleInterceptor(interceptor: Interceptor): Boolean =
        interceptor !== BrotliInterceptor &&
            interceptor.javaClass.simpleName != "BrotliInterceptor" &&
            interceptor.javaClass.simpleName != "GZipInterceptor" &&
            interceptor.javaClass.simpleName != "IgnoreGzipInterceptor" &&
            interceptor !is FutonCloudFlareInterceptor

    private fun isDefaultMihonInterceptor(interceptor: Interceptor): Boolean =
        interceptor.javaClass.simpleName in MIHON_COMPAT_INTERCEPTOR_NAMES

    @Deprecated("The regular client handles Cloudflare by default")
    override val cloudflareClient: OkHttpClient = client.newBuilder()
        .addNetworkInterceptor(BrotliInterceptor)
        .build()

    override fun defaultUserAgentProvider(): String = UserAgents.CHROME_MOBILE

    private fun Response.closeThrowing(error: Throwable): Nothing {
        try {
            close()
        } catch (e: Exception) {
            error.addSuppressed(e)
        }
        throw error
    }

    private fun Request.toChallengeUrl(): String {
        val referer = header("Referer")?.toHttpUrlOrNull()
        if (referer != null && referer.host == url.host) {
            return referer.newBuilder().query(null).fragment(null).build().toString()
        }
        return url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
    }

    private fun getClearanceCookie(request: Request): String? =
        cookieJar.loadForRequest(request.url).firstOrNull { it.name == "cf_clearance" }?.value

    private fun tryResolveAutomatically(request: Request, challengeUrl: String): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Cloudflare automatic solve skipped on main thread")
            return false
        }

        val solver = clearanceSolver
        val coordinator = solveCoordinator
        if (solver != null && coordinator != null) {
            return runCatching {
                runBlocking {
                    coordinator.solve(request.url.host) { solver.solve(request) }
                }
            }.onFailure {
                Log.w(TAG, "Cloudflare offscreen solver failed for ${request.url.host}", it)
            }.getOrDefault(false)
        }

        // Compatibility-only fallback for tests or old construction sites. Production Mihon
        // wiring always supplies the dedicated solver above.
        val executor = webViewExecutor ?: return false
        val exception = CloudFlareProtectedException(
            url = challengeUrl,
            source = null,
            headers = request.headers,
        )
        return runCatching {
            runBlocking { executor.tryResolveCaptcha(exception, LEGACY_RESOLVE_TIMEOUT_MS) }
        }.onFailure {
            Log.w(TAG, "Legacy Cloudflare resolver failed for ${request.url.host}", it)
        }.getOrDefault(false)
    }

    private fun throwUnresolvedChallenge(
        response: Response,
        request: Request,
        challengeUrl: String,
        host: String,
    ): Nothing {
        val clearance = getClearanceCookie(request)
        if (shouldSkipInteractiveAction(host, clearance)) {
            Log.w(TAG, "Skip interactive action for host=$host: repeated challenge with same cf_clearance")
            response.closeThrowing(
                CloudFlareBlockedException(
                    url = challengeUrl,
                    source = request.tag(ContentSource::class.java),
                ),
            )
        }

        val source = request.tag(ContentSource::class.java)
        if (source == null) {
            Log.w(TAG, "Cloudflare challenge unresolved for host=$host and request has no ContentSource tag")
            response.closeThrowing(
                CloudFlareProtectedException(
                    url = challengeUrl,
                    source = null,
                    headers = request.headers,
                ),
            )
        }

        // Only after the dedicated background solver failed do we preserve Futon's existing
        // manual fallback contract for callers that can deliberately present interactive UI.
        response.closeThrowing(
            InteractiveActionRequiredException(
                source = source.toMangaSource(),
                url = challengeUrl,
            ),
        )
    }

    private fun maskCookieValue(value: String?): String {
        if (value.isNullOrEmpty()) return "<empty>"
        return if (value.length <= 8) "***" else "${value.take(4)}...${value.takeLast(4)}"
    }

    private fun shouldSkipInteractiveAction(host: String, clearance: String?): Boolean {
        if (clearance.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val last = recentChallengeAttempts[host]
        if (last == null || now - last.timestampMs > INTERACTIVE_RETRY_WINDOW_MS || last.clearance != clearance) {
            recentChallengeAttempts[host] = ChallengeAttempt(clearance, now, 1)
            return false
        }
        val nextCount = last.count + 1
        recentChallengeAttempts[host] = last.copy(timestampMs = now, count = nextCount)
        return nextCount >= 2
    }

    private data class ChallengeAttempt(
        val clearance: String,
        val timestampMs: Long,
        val count: Int,
    )

    companion object {
        private const val TAG = "MihonNetwork"
        private const val LEGACY_RESOLVE_TIMEOUT_MS = 20_000L
        private const val INTERACTIVE_RETRY_WINDOW_MS = 10 * 60 * 1000L
        private val recentChallengeAttempts = ConcurrentHashMap<String, ChallengeAttempt>()
        private val MIHON_COMPAT_INTERCEPTOR_NAMES = setOf(
            "UncaughtExceptionInterceptor",
            "UserAgentInterceptor",
            "CloudflareInterceptor",
        )
    }
}
