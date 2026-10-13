package io.github.landwarderer.futon.reader.ui

import io.github.landwarderer.futon.details.domain.chapterListForBranch
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import io.github.landwarderer.futon.core.model.MangaHistory
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
		chapterId = (manga.chapterListForBranch(branch).firstOrNull() ?: manga.chapters?.firstOrNull())?.id ?: error("Cannot find first chapter"),
		page = 0,
		scroll = 0,
	)
}
