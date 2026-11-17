package io.github.landwarderer.futon.details.data

import io.github.landwarderer.futon.details.domain.DEFAULT_MERGE_REPEATED_CHAPTERS
import io.github.landwarderer.futon.details.domain.mergeRepeated
import io.github.landwarderer.futon.core.model.getLocale
import io.github.landwarderer.futon.core.model.isLocal
import io.github.landwarderer.futon.core.model.isNovel
import io.github.landwarderer.futon.core.model.withOverride
import io.github.landwarderer.futon.core.ui.model.MangaOverride
import io.github.landwarderer.futon.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import io.github.landwarderer.futon.reader.data.filterChapters
import java.util.Locale

data class MangaDetails(
    private val manga: Manga,
    private val localManga: LocalManga?,
    private val override: MangaOverride?,
    val description: CharSequence?,
    val isLoaded: Boolean,
    val mergeRepeatedChapters: Boolean = DEFAULT_MERGE_REPEATED_CHAPTERS,
    val identityChaptersSource: List<MangaChapter>? = null,
    val downloadedChaptersSource: List<MangaChapter>? = null,
    val remoteChaptersSource: List<MangaChapter>? = null,
    val projectedBranch: String? = null,
) {

    constructor(manga: Manga) : this(
        manga = manga,
        localManga = null,
        override = null,
        description = null,
        isLoaded = false,
    )

    val id: Long
        get() = manga.id

    val allChapters: List<MangaChapter> by lazy { mergeChapters() }

    /** Original identities survive a reader projection that hides alternative uploads. */
    val identityChapters: List<MangaChapter>
        get() = identityChaptersSource ?: allChapters

    val storedChapters: List<MangaChapter>
        get() = downloadedChaptersSource ?: local?.manga?.chapters.orEmpty()

    private val originalBranchCount by lazy { identityChapters.mapTo(HashSet()) { it.branch }.size }

    /** The chapters per branch (group) in this reading projection. */
    private val chaptersByBranch: Map<String?, List<MangaChapter>> by lazy { allChapters.groupBy { it.branch } }

    /** The number of chapter groups of the source; the merge option is only offered for several. */
    val branchCount: Int
        get() = originalBranchCount

    /** One list of all groups, repeated chapters shown once; see [mergeRepeated]. Computed once. */
    private val mergedChapters: List<MangaChapter> by lazy {
        val unique = chaptersByBranch.values.flatten().mergeRepeated()
        // The groups are concatenated; bring them in reading order. Volumes only matter when the source has some.
        val hasVolumes = unique.any { it.volume > 0 }
        val comparator = if (hasVolumes) {
            compareBy<MangaChapter>({ it.volume }, { it.number })
        } else {
            compareBy<MangaChapter> { it.number }
        }
        unique.sortedWith(comparator)
    }

    /**
     * Whether the groups are shown as one list. This is the "merge chapter groups" setting, and it only applies to
     * a manga with several groups; it never applies to novels.
     */
    val isMerged: Boolean
        get() = mergeRepeatedChapters && !isNovel && branchCount > 1

    val chapters: Map<String?, List<MangaChapter>>
        get() = if (isMerged) mapOf(null to mergedChapters) else chaptersByBranch

    fun chapterListForBranch(branch: String?): List<MangaChapter> =
        if (isMerged) mergedChapters else chaptersByBranch[branch].orEmpty()

    val isLocal
        get() = manga.isLocal

    val local: LocalManga?
        get() = localManga ?: if (manga.isLocal) LocalManga(manga) else null

    val coverUrl: String?
        get() = override?.coverUrl
            .ifNullOrEmpty { manga.largeCoverUrl }
            .ifNullOrEmpty { manga.coverUrl }
            .ifNullOrEmpty { localManga?.manga?.coverUrl }
            ?.nullIfEmpty()

    val isRestricted: Boolean
        get() = manga.state == MangaState.RESTRICTED

    val isNovel: Boolean
        get() = manga.isNovel

    private val mergedManga by lazy {
        if (localManga == null) {
            // fast path
            manga.withOverride(override)
        } else {
            manga.copy(
                title = override?.title.ifNullOrEmpty { manga.title },
                coverUrl = override?.coverUrl.ifNullOrEmpty { manga.coverUrl },
                largeCoverUrl = override?.coverUrl.ifNullOrEmpty { manga.largeCoverUrl },
                contentRating = override?.contentRating ?: manga.contentRating,
                chapters = allChapters,
            )
        }
    }

    fun toManga() = mergedManga

    fun getLocale(): Locale? {
        findAppropriateLocale(chaptersByBranch.keys.singleOrNull())?.let {
            return it
        }
        return manga.source.getLocale()
    }

    fun filterChapters(branch: String?): MangaDetails = if (isMerged) {
        val listed = mergedChapters
        val listedIds = listed.mapTo(HashSet(listed.size)) { it.id }
        // Reader next/previous uses the one list; the other uploads stay in the details' own chapter groups.
        copy(
            manga = manga.copy(chapters = listed),
            identityChaptersSource = identityChapters,
            downloadedChaptersSource = storedChapters,
            remoteChaptersSource = remoteChaptersSource ?: manga.chapters.orEmpty(),
            projectedBranch = branch,
            localManga = localManga?.run {
                copy(manga = manga.copy(chapters = manga.chapters?.filter { it.id in listedIds }))
            },
        )
    } else copy(
        manga = manga.copy(chapters = identityChapters).filterChapters(branch),
        identityChaptersSource = identityChapters,
        downloadedChaptersSource = storedChapters,
        localManga = localManga?.run {
            copy(manga = manga.copy(chapters = storedChapters).filterChapters(branch))
        },
    )


    /** Rebuild cached projections after a completed download or deletion, using original remote URLs. */
    fun withLocalManga(updated: LocalManga): MangaDetails {
        val wasProjected = remoteChaptersSource != null
        val raw = copy(
            manga = if (isLocal) updated.manga else manga.copy(chapters = remoteChaptersSource ?: manga.chapters),
            localManga = if (isLocal) null else updated,
            identityChaptersSource = null,
            downloadedChaptersSource = null,
            remoteChaptersSource = null,
            projectedBranch = null,
        )
        return if (wasProjected) raw.filterChapters(projectedBranch) else raw
    }

    private fun mergeChapters(): List<MangaChapter> {
        val chapters = manga.chapters
        val localChapters = local?.manga?.chapters.orEmpty()
        if (chapters.isNullOrEmpty()) {
            return localChapters
        }
        val localMap = if (localChapters.isNotEmpty()) {
            localChapters.associateByTo(LinkedHashMap(localChapters.size)) { it.id }
        } else {
            null
        }
        val result = ArrayList<MangaChapter>(chapters.size)
        for (chapter in chapters) {
            val local = localMap?.remove(chapter.id)
            result += local ?: chapter
        }
        if (!localMap.isNullOrEmpty()) {
            result.addAll(localMap.values)
        }
        return result
    }

    private fun findAppropriateLocale(name: String?): Locale? {
        if (name.isNullOrEmpty()) {
            return null
        }
        return Locale.getAvailableLocales().find { lc ->
            name.contains(lc.getDisplayName(lc), ignoreCase = true) ||
                name.contains(lc.getDisplayName(Locale.ENGLISH), ignoreCase = true) ||
                name.contains(lc.getDisplayLanguage(lc), ignoreCase = true) ||
                name.contains(lc.getDisplayLanguage(Locale.ENGLISH), ignoreCase = true)
        }
    }
}
