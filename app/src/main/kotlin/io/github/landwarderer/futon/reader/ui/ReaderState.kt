package io.github.landwarderer.futon.reader.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import io.github.landwarderer.futon.core.model.MangaHistory
import io.github.landwarderer.futon.details.data.MangaDetails
import io.github.landwarderer.futon.details.domain.hasMergedChapterGroups
import org.koitharu.kotatsu.parsers.model.Manga

@Parcelize
data class ReaderState(
	val chapterId: Long,
	val page: Int,
	val scroll: Int,
) : Parcelable {

	constructor(history: MangaHistory) : this(
		chapterId = history.chapterId,
		page = history.page,
		scroll = history.scroll,
	)

	constructor(manga: Manga, branch: String?) : this(
		chapterId = (
			(if (manga.hasMergedChapterGroups()) MangaDetails(manga).chapterListForBranch(null) else manga.getChapters(branch))
				.firstOrNull() ?: manga.chapters?.firstOrNull()
			)?.id ?: error("Cannot find first chapter"),
		page = 0,
		scroll = 0,
	)
}
