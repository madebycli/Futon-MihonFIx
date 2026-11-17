package io.github.landwarderer.futon.list.ui.preview

import android.text.Html
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.plus
import io.github.landwarderer.futon.details.domain.renderDescription
import io.github.landwarderer.futon.core.model.getPreferredBranch
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.require
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.list.domain.MangaListMapper
import io.github.landwarderer.futon.list.domain.ReadingProgress.Companion.PROGRESS_NONE
import javax.inject.Inject

@HiltViewModel
class PreviewViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaListMapper: MangaListMapper,
	private val repositoryFactory: MangaRepository.Factory,
	private val historyRepository: HistoryRepository,
	private val imageGetter: Html.ImageGetter,
) : BaseViewModel() {

	val manga = MutableStateFlow(
		savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga,
	)

	val footer = combine(
		manga,
		historyRepository.observeOne(manga.value.id),
		manga.flatMapLatest { historyRepository.observeShouldSkip(it) }.distinctUntilChanged(),
	) { m, history, incognito ->
		if (m.chapters == null) {
			return@combine null
		}
		val b = m.getPreferredBranch(history)
		val chapters = m.getChapters(b)
		FooterInfo(
			percent = history?.percent ?: PROGRESS_NONE,
			currentChapter = history?.chapterId?.let {
				chapters.indexOfFirst { x -> x.id == it }
			} ?: -1,
			totalChapters = chapters.size,
			isIncognito = incognito,
		)
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, null)

	val description = manga
		.distinctUntilChangedBy { it.description.orEmpty() }
		.transformLatest {
			val description = it.description
			if (description.isNullOrEmpty()) {
				emit(null)
			} else {
				emit(renderDescription(description, null).trim())
				emit(renderDescription(description, imageGetter).trim())
			}
		}.combine(isLoading) { desc, loading ->
			if (loading) null else desc ?: ""
		}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.WhileSubscribed(5000), null)

	val tagsChips = manga.map {
		mangaListMapper.mapTags(it.tags)
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	init {
		launchLoadingJob(Dispatchers.IO) {
			val repo = repositoryFactory.create(manga.value.source)
			manga.value = repo.getDetails(manga.value)
		}
	}

	data class FooterInfo(
		val currentChapter: Int,
		val totalChapters: Int,
		val isIncognito: Boolean,
		val percent: Float,
	) {

		fun isInProgress() = currentChapter >= 0
	}
}
