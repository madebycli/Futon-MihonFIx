package io.github.landwarderer.futon.details.ui.pager

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.landwarderer.futon.bookmarks.domain.BookmarksRepository
import io.github.landwarderer.futon.core.model.toChipModel
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.observeAsStateFlow
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.ui.util.ReversibleAction
import io.github.landwarderer.futon.core.util.LocaleStringComparator
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.combine
import io.github.landwarderer.futon.core.util.ext.requireValue
import io.github.landwarderer.futon.core.util.ext.sortedWithSafe
import io.github.landwarderer.futon.details.data.MangaDetails
import io.github.landwarderer.futon.details.domain.DetailsInteractor
import io.github.landwarderer.futon.details.domain.ChapterUpdateMarker
import io.github.landwarderer.futon.details.domain.newChapterRepresentativeIds
import io.github.landwarderer.futon.details.ui.DetailsActivity
import io.github.landwarderer.futon.details.ui.DetailsViewModel
import io.github.landwarderer.futon.details.ui.mapChapters
import io.github.landwarderer.futon.details.ui.model.ChapterListItem
import io.github.landwarderer.futon.download.ui.worker.DownloadTask
import io.github.landwarderer.futon.download.ui.worker.DownloadWorker
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.list.domain.ListFilterOption
import io.github.landwarderer.futon.local.domain.DeleteLocalMangaUseCase
import io.github.landwarderer.futon.local.domain.model.LocalManga
import io.github.landwarderer.futon.reader.ui.ReaderActivity
import io.github.landwarderer.futon.reader.ui.ReaderState
import io.github.landwarderer.futon.reader.ui.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import okio.FileNotFoundException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaState

abstract class ChaptersPagesViewModel(
	@JvmField protected val settings: AppSettings,
	@JvmField protected val interactor: DetailsInteractor,
	private val bookmarksRepository: BookmarksRepository,
	private val historyRepository: HistoryRepository,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val downloadQueueRepository: io.github.landwarderer.futon.download.data.repository.DownloadQueueRepository,
	private val addUnreadToQueueUseCase: io.github.landwarderer.futon.download.domain.usecase.AddUnreadToQueueUseCase,
	private val workManager: androidx.work.WorkManager,
	private val deleteLocalMangaUseCase: DeleteLocalMangaUseCase,
	private val localStorageChanges: SharedFlow<LocalManga?>,
) : BaseViewModel() {

	val mangaDetails = MutableStateFlow<MangaDetails?>(null)
	val readingState = MutableStateFlow<ReaderState?>(null)

	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onDownloadStarted = MutableEventFlow<Unit>()
	val onMangaRemoved = MutableEventFlow<Manga>()

	private val chaptersQuery = MutableStateFlow("")
	val selectedBranch = MutableStateFlow<String?>(null)

	val manga = mangaDetails.map { x -> x?.toManga() }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, null)

	val coverUrl = mangaDetails.map { x -> x?.coverUrl }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, null)

	val isChaptersReversed = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_REVERSE_CHAPTERS,
		valueProducer = { isChaptersReverse },
	)

	val isChaptersInGridView = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_GRID_VIEW_CHAPTERS,
		valueProducer = { isChaptersGridView },
	)

	val isMergeRepeatedChapters = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_MERGE_REPEATED_CHAPTERS,
		valueProducer = { isMergeRepeatedChapters },
	)

	/**
	 * The option is always offered for a manga (not for a novel), so it can be switched before the source reports
	 * several chapter groups; with a single group it simply changes nothing.
	 */
	val showMergeRepeatedChapters = mangaDetails.map { it != null && !it.isNovel }
		.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, false)

	val isDownloadedOnly = MutableStateFlow(false)

	private val chapterUpdates = mangaDetails.flatMapLatest { d ->
		if (d?.isLocal == false) interactor.observeChapterUpdates(d.toManga()) else flowOf(ChapterUpdateMarker())
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, ChapterUpdateMarker())

	val newChaptersCount = combine(mangaDetails, chapterUpdates, isMergeRepeatedChapters) { d, marker, enabled ->
		val projected = d?.copy(mergeRepeatedChapters = enabled)
		if (projected?.isMerged == true) projected.newChapterRepresentativeIds(marker).size else marker.count
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, 0)

	val emptyReason: StateFlow<EmptyMangaReason?> = combine(
		mangaDetails,
		isLoading,
		onError.onStart { emit(null) },
	) { details, loading, error ->
		when {
			details == null || loading -> null
			details.chapters.isNotEmpty() -> null
			details.toManga().state == MangaState.RESTRICTED -> EmptyMangaReason.RESTRICTED
			error != null -> EmptyMangaReason.LOADING_ERROR
			else -> EmptyMangaReason.NO_CHAPTERS
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.WhileSubscribed(), null)

	val bookmarks = mangaDetails.flatMapLatest {
		if (it != null) {
			bookmarksRepository.observeBookmarks(it.toManga()).withErrorHandling()
		} else {
			flowOf(emptyList())
		}
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Lazily, emptyList())

	/** The details again whenever the merge setting changes: the chapter lists depend on it. */
	private val detailsOnMergeChange = mangaDetails.combine(isMergeRepeatedChapters) { details, enabled ->
		details?.copy(mergeRepeatedChapters = enabled)
	}

	val chapters = combine(
		combine(
			detailsOnMergeChange,
			readingState.map { it?.chapterId ?: 0L }.distinctUntilChanged(),
			selectedBranch,
			chapterUpdates,
			bookmarks,
			isChaptersInGridView,
			isDownloadedOnly,
		) { manga, currentChapterId, branch, news, bookmarks, grid, downloadedOnly ->
			manga?.mapChapters(
				currentChapterId = currentChapterId,
				newCount = news.count,
				newChapterIds = if (manga.isMerged) manga.newChapterRepresentativeIds(news) else null,
				branch = branch,
				bookmarks = bookmarks,
				isGrid = grid,
				isDownloadedOnly = downloadedOnly,
			).orEmpty()
		},
		isChaptersReversed,
		chaptersQuery,
	) { list, reversed, query ->
		(if (reversed) list.asReversed() else list).filterSearch(query)
	}.stateIn(viewModelScope + Dispatchers.IO, SharingStarted.Eagerly, emptyList())

	val quickFilter = combine(
		detailsOnMergeChange,
		selectedBranch,
	) { details, branch ->
		val branches = details?.chapters?.toList()?.sortedWithSafe(
			compareBy(LocaleStringComparator()) { it.first },
		).orEmpty()
		if (branches.size > 1) {
			branches.map {
				val option = ListFilterOption.Branch(titleText = it.first, chaptersCount = it.second.size)
				option.toChipModel(isChecked = it.first == branch)
			}
		} else {
			emptyList()
		}
	}

	init {
		launchJob(Dispatchers.Default) {
			isMergeRepeatedChapters.collect { enabled ->
				mangaDetails.update { it?.copy(mergeRepeatedChapters = enabled) }
			}
		}
		launchJob(Dispatchers.IO) {
			localStorageChanges
				.collect { onDownloadComplete(it) }
		}
		launchJob(Dispatchers.Default) {
			// Merging the chapter groups (or undoing it) changes which groups exist: keep the selection valid.
			detailsOnMergeChange.collect { details ->
				val groups = details?.chapters ?: return@collect
				if (groups.isNotEmpty() && selectedBranch.value !in groups) {
					selectedBranch.value = groups.maxByOrNull { it.value.size }?.key
				}
			}
		}
	}

	fun setChaptersReversed(newValue: Boolean) {
		settings.isChaptersReverse = newValue
	}

	fun setMergeRepeatedChapters(newValue: Boolean) {
		settings.isMergeRepeatedChapters = newValue
	}

	fun setChaptersInGridView(newValue: Boolean) {
		settings.isChaptersGridView = newValue
	}

	fun setSelectedBranch(branch: String?) {
		selectedBranch.value = branch
	}

	fun performChapterSearch(query: String?) {
		chaptersQuery.value = query?.trim().orEmpty()
	}

	fun getMangaOrNull(): Manga? = mangaDetails.value?.toManga()

	fun requireManga() = mangaDetails.requireValue().toManga()

	fun markChapterAsCurrent(chapterId: Long) {
		launchJob(Dispatchers.IO) {
			val manga = mangaDetails.requireValue()
			val chapters = manga.chapterListForBranch(selectedBranch.value)
			val chapterIndex = chapters.indexOfFirst { it.id == chapterId }
			check(chapterIndex in chapters.indices) { "Chapter not found" }
			val percent = chapterIndex / chapters.size.toFloat()
			historyRepository.addOrUpdate(
				manga = manga.toManga(),
				chapterId = chapterId,
				page = 0,
				scroll = 0,
				percent = percent,
				force = true,
			)
		}
	}

	fun download(chaptersIds: Set<Long>?, allowMeteredNetwork: Boolean) {
		launchJob(Dispatchers.IO) {
			val manga = requireManga()
			android.util.Log.d("ChaptersPagesVM", "Manual download start for manga: ${manga.title}, chapters: ${chaptersIds?.size ?: "all"}")
			val task = DownloadTask(
				mangaId = manga.id,
				isPaused = false,
				isSilent = false,
				chaptersIds = chaptersIds?.toLongArray(),
				destination = null,
				format = null,
				allowMeteredNetwork = allowMeteredNetwork,
				requiresCharging = false,
			)
			downloadScheduler.schedule(setOf(manga to task))
			onDownloadStarted.call(Unit)
		}
	}

	fun scheduleDownload(
		chaptersIds: Set<Long>?,
		wifiOnly: Boolean,
		chargingOnly: Boolean,
		offPeakOnly: Boolean
	) {
		launchJob(Dispatchers.IO) {
			val manga = requireManga()
			android.util.Log.d("ChaptersPagesVM", "Scheduling download for manga: ${manga.title}, chapters: ${chaptersIds?.size ?: "unread"}")
			if (chaptersIds == null) {
				addUnreadToQueueUseCase(
					manga = manga,
					wifiOnly = wifiOnly,
					chargingOnly = chargingOnly,
					offPeakOnly = offPeakOnly
				)
			} else {
				downloadQueueRepository.addToQueue(
					manga = manga,
					chaptersIds = chaptersIds.toLongArray(),
					wifiOnly = wifiOnly,
					chargingOnly = chargingOnly,
					offPeakOnly = offPeakOnly
				)
			}
			onDownloadStarted.call(Unit)
		}
	}

	fun deleteLocal() {
		val m = mangaDetails.value?.local?.manga
		if (m == null) {
			errorEvent.call(FileNotFoundException())
			return
		}
		val isActuallyLocal = mangaDetails.value?.isLocal == true
		launchLoadingJob(Dispatchers.IO) {
			deleteLocalMangaUseCase(m)
			if (isActuallyLocal) {
				onMangaRemoved.call(m)
			}
		}
	}

	private fun List<ChapterListItem>.filterSearch(query: String): List<ChapterListItem> {
		if (query.isEmpty() || this.isEmpty()) {
			return this
		}
		return filter { it.contains(query) }
	}

	protected open suspend fun onDownloadComplete(downloadedManga: LocalManga?) {
		mangaDetails.update {
			if (downloadedManga == null) {
				it?.copy(localManga = null)
			} else if (it?.id == downloadedManga.manga.id) {
				interactor.updateLocal(it, downloadedManga)
			} else {
				it
			}
		}
	}

	class ActivityVMLazy(
		private val fragment: Fragment,
	) : Lazy<ChaptersPagesViewModel> {
		private var cached: ChaptersPagesViewModel? = null

		override val value: ChaptersPagesViewModel
			get() {
				val viewModel = cached
				return if (viewModel == null) {
					val activity = fragment.requireActivity()
					val vmClass = getViewModelClass(activity)
					ViewModelProvider.create(
						store = activity.viewModelStore,
						factory = activity.defaultViewModelProviderFactory,
						extras = activity.defaultViewModelCreationExtras,
					)[vmClass].also { cached = it }
				} else {
					viewModel
				}
			}

		override fun isInitialized(): Boolean = cached != null

		private fun getViewModelClass(activity: Activity) = when (activity) {
			is ReaderActivity -> ReaderViewModel::class.java
			is DetailsActivity -> DetailsViewModel::class.java
			else -> error("Wrong activity ${activity.javaClass.simpleName} for ${ChaptersPagesViewModel::class.java.simpleName}")
		}
	}
}
