// Ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0):
// app/src/main/kotlin/org/skepsun/kototoro/core/model/ContentChapterExt.kt
// as of madebycli/Kototoro 404e596c79e4170f5aa30ea0cccf286dc76b53e8.
package io.github.landwarderer.futon.details.domain

import io.github.landwarderer.futon.core.model.isNovel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter

const val DEFAULT_MERGE_REPEATED_CHAPTERS = true

/** The identity of a chapter across groups: volume and number, else the title, else the chapter itself. */
fun MangaChapter.getMergeKey(): String = if (number > 0f) {
    val volKey = if (volume > 0) volume else null
    "num_${volKey}_$number"
} else {
    val titleKey = title?.lowercase()?.trim()
    if (!titleKey.isNullOrBlank()) {
        "title_$titleKey"
    } else {
        "unique_${id}_$url"
    }
}

private fun String.isLocalChapterUrl(): Boolean =
    startsWith("file:") || startsWith("zip:") || startsWith("file+zip:") || startsWith("content:") ||
        startsWith("epub:") || startsWith("localepub:")

/**
 * One representative per repeated chapter (same [getMergeKey]), in the order the keys first appear.
 * The representative is the most recent upload, then a downloaded copy, then one with a group name, then the
 * longest title. Nothing is deleted: the other uploads stay in the chapter groups.
 */
fun List<MangaChapter>.mergeRepeated(): List<MangaChapter> {
    if (this.isEmpty()) return this
    val groups = this.groupBy { it.getMergeKey() }
    return groups.map { (_, groupList) ->
        if (groupList.size == 1) {
            groupList.first()
        } else {
            groupList.maxWithOrNull(
                compareBy<MangaChapter> { it.uploadDate }
                    .thenBy { it.url.isLocalChapterUrl() }
                    .thenBy { it.scanlator?.isNotBlank() == true }
                    .thenBy { it.title?.length ?: 0 },
            ) ?: groupList.first()
        }
    }
}

/**
 * Where [chapter] sits in this list: its own index, or else the index of the representative of the same chapter
 * (same [getMergeKey]) when the list shows another upload of it. -1 when the chapter is not in the list at all.
 */
fun List<MangaChapter>.indexOfChapter(chapter: MangaChapter): Int {
    val index = indexOfFirst { it.id == chapter.id }
    if (index >= 0) return index
    val key = chapter.getMergeKey()
    return indexOfFirst { it.getMergeKey() == key }
}

/** Whether the groups of this manga are shown as one list: the setting is on and there are several groups. */
fun Manga.hasMergedChapterGroups(mergeRepeatedChapters: Boolean = DEFAULT_MERGE_REPEATED_CHAPTERS): Boolean =
    mergeRepeatedChapters && !isNovel &&
    chapters.orEmpty().mapTo(HashSet()) { it.branch }.size > 1

/** Tracker counts refer to one original branch ending at the recorded last chapter. */
data class ChapterUpdateMarker(val count: Int = 0, val lastChapterId: Long? = null)

fun io.github.landwarderer.futon.details.data.MangaDetails.newChapterRepresentativeIds(
    marker: ChapterUpdateMarker,
): Set<Long> {
    if (marker.count <= 0) return emptySet()
    val anchor = identityChapters.firstOrNull { it.id == marker.lastChapterId } ?: return emptySet()
    val originalBranch = identityChapters.filter { it.branch == anchor.branch }
    val anchorIndex = originalBranch.indexOfFirst { it.id == anchor.id }
    if (anchorIndex < 0) return emptySet()
    val originalNew = originalBranch.take(anchorIndex + 1).takeLast(marker.count)
    if (!isMerged) return originalNew.mapTo(HashSet()) { it.id }
    val keys = originalNew.mapTo(HashSet()) { it.getMergeKey() }
    return chapterListForBranch(null).filter { it.getMergeKey() in keys }.mapTo(HashSet()) { it.id }
}
