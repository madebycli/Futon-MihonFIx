package io.github.landwarderer.futon.details.ui

import android.content.Context
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.bookmarks.domain.Bookmark
import io.github.landwarderer.futon.details.data.MangaDetails
import io.github.landwarderer.futon.details.domain.ChapterSection
import io.github.landwarderer.futon.details.domain.getMergeKey
import io.github.landwarderer.futon.details.domain.mergeRepeated
import io.github.landwarderer.futon.details.domain.indexOfChapter
import io.github.landwarderer.futon.details.domain.withVolumeSections
import io.github.landwarderer.futon.details.ui.model.ChapterListItem
import io.github.landwarderer.futon.details.ui.model.toListItem
import io.github.landwarderer.futon.list.ui.model.ListHeader
import io.github.landwarderer.futon.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.util.mapToSet

fun MangaDetails.mapChapters(
	currentChapterId: Long,
	newCount: Int,
	branch: String?,
	bookmarks: List<Bookmark>,
	isGrid: Boolean,
	isDownloadedOnly: Boolean,
	newChapterIds: Set<Long>? = null,
): List<ChapterListItem> {
	val remoteChapters = chapterListForBranch(branch)
	val downloadedChapters = storedChapters
	if (isMerged && isDownloadedOnly) {
		val current = identityChapters.firstOrNull { it.id == currentChapterId }
		val currentIndex = current?.let(remoteChapters::indexOfChapter) ?: -1
		val bookmarkedIds = bookmarks.mapToSet { it.chapterId }
		val bookmarkKeys = identityChapters.filter { it.id in bookmarkedIds }.mapTo(HashSet()) { it.getMergeKey() }
		return downloadedChapters.mergeRepeated()
			.sortedBy { downloaded -> remoteChapters.indexOfChapter(downloaded).takeIf { it >= 0 } ?: Int.MAX_VALUE }
			.map { downloaded ->
				val index = remoteChapters.indexOfChapter(downloaded)
				downloaded.toListItem(
					isCurrent = index >= 0 && index == currentIndex,
					isUnread = currentIndex < 0 || index < 0 || index >= currentIndex,
					isNew = false,
					isDownloaded = true,
					isBookmarked = downloaded.id in bookmarkedIds || downloaded.getMergeKey() in bookmarkKeys,
					isGrid = isGrid,
				)
			}
	}
	val localChapters = if (isMerged) {
		// Downloads of another upload of a listed chapter are not listed again; downloads of chapters the source
		// no longer has still are.
		val remoteIds = remoteChapters.mapTo(HashSet(remoteChapters.size)) { it.id }
		val remoteKeys = remoteChapters.mapTo(HashSet(remoteChapters.size)) { it.getMergeKey() }
		local?.manga?.chapters.orEmpty().filter { it.id in remoteIds || it.getMergeKey() !in remoteKeys }
	} else {
		local?.manga?.getChapters(branch).orEmpty()
	}
	if (remoteChapters.isEmpty() && localChapters.isEmpty()) {
		return emptyList()
	}
	val bookmarked = bookmarks.mapToSet { it.chapterId }
	val bookmarkedKeys: Set<String> = if (isMerged) identityChapters.filter { it.id in bookmarked }.mapTo(HashSet()) { it.getMergeKey() } else emptySet()
	val newFrom = if (newCount == 0 || remoteChapters.isEmpty()) Int.MAX_VALUE else remoteChapters.size - newCount
	val ids = buildSet(maxOf(remoteChapters.size, localChapters.size)) {
		remoteChapters.mapTo(this) { it.id }
		localChapters.mapTo(this) { it.id }
	}
	val result = ArrayList<ChapterListItem>(ids.size)
	val localMap = if (localChapters.isNotEmpty()) {
		localChapters.associateByTo(LinkedHashMap(localChapters.size)) { it.id }
	} else {
		null
	}
	val currentId = if (isMerged && currentChapterId != 0L && currentChapterId !in ids) {
		// The history points to another upload of a listed chapter: that listed chapter is the current one.
		identityChapters.firstOrNull { it.id == currentChapterId }
			?.let { remoteChapters.indexOfChapter(it) }
			?.takeIf { it >= 0 }
			?.let { remoteChapters[it].id } ?: currentChapterId
	} else {
		currentChapterId
	}
	var isUnread = currentId !in ids
	if (!isDownloadedOnly || local?.manga?.chapters == null) {
		for (chapter in remoteChapters) {
			val local = localMap?.remove(chapter.id)
			if (chapter.id == currentId) {
				isUnread = true
			}
			result += (local ?: chapter).toListItem(
				isCurrent = chapter.id == currentId,
				isUnread = isUnread,
				isNew = isUnread && (newChapterIds?.contains(chapter.id) ?: (!isMerged && result.size >= newFrom)),
				isDownloaded = local != null,
				isBookmarked = chapter.id in bookmarked || (isMerged && chapter.getMergeKey() in bookmarkedKeys),
				isGrid = isGrid,
			)
		}
	}
	if (!localMap.isNullOrEmpty()) {
		for (chapter in localMap.values) {
			if (chapter.id == currentId) {
				isUnread = true
			}
			result += chapter.toListItem(
				isCurrent = chapter.id == currentId,
				isUnread = isUnread,
				isNew = false,
				isDownloaded = !isLocal,
				isBookmarked = chapter.id in bookmarked || (isMerged && chapter.getMergeKey() in bookmarkedKeys),
				isGrid = isGrid,
			)
		}
	}
	return result
}

/** Kototoro's volume/group sections: a header only where the volume changes or a new group name starts. */
fun List<ChapterListItem>.withVolumeHeaders(context: Context): MutableList<ListModel> =
	withVolumeSections(this, { it.chapter.volume }, { it.chapter.scanlator }).mapTo(
		ArrayList<ListModel>((size * 1.4).toInt()),
	) { section ->
		when (section) {
			is ChapterSection.Header -> ListHeader(
				section.customName ?: if (section.volume <= 0) {
					context.getString(R.string.volume_unknown)
				} else {
					context.getString(R.string.volume_, section.volume)
				},
				isChapterSection = true,
			)

			is ChapterSection.Item -> section.chapter
		}
	}
