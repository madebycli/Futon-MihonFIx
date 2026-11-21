package io.github.landwarderer.futon.download.ui.dialog

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import androidx.collection.MutableLongLongMap
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.model.getPreferredBranch
import io.github.landwarderer.futon.core.model.parcelable.ParcelableManga
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.parser.CachingMangaRepository
import io.github.landwarderer.futon.core.parser.MangaDataRepository
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.parser.ShownMangaDetails
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.DownloadFormat
import io.github.landwarderer.futon.core.ui.BaseViewModel
import io.github.landwarderer.futon.core.util.ext.MutableEventFlow
import io.github.landwarderer.futon.core.util.ext.call
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.futon.core.util.ext.require
import io.github.landwarderer.futon.download.data.repository.DownloadQueueRepository
import io.github.landwarderer.futon.download.ui.worker.DownloadTask
import io.github.landwarderer.futon.download.ui.worker.DownloadWorker
import io.github.landwarderer.futon.history.data.HistoryRepository
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import io.github.landwarderer.futon.local.data.LocalStorageManager
import io.github.landwarderer.futon.settings.storage.DirectoryModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.parsers.util.sizeOrZero
import javax.inject.Inject

@HiltViewModel
class DownloadDialogViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val scheduler: DownloadWorker.Scheduler,
	private val downloadQueueRepository: DownloadQueueRepository,
	private val localStorageManager: LocalStorageManager,
	private val localMangaRepository: LocalMangaRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val mangaDataRepository: MangaDataRepository,
	private val shownMangaDetails: ShownMangaDetails,
	private val historyRepository: HistoryRepository,
	private val settings: AppSettings,
) : BaseViewModel() {

	val manga = savedStateHandle.require<Array<ParcelableManga>>(AppRouter.KEY_MANGA).map {
		it.manga
	}
	/**
	 * The details the options and the downloads are made from. The chapters already known (just loaded, or stored)
	 * are used at once, so a slow source does not hold the dialog up; the update from the source runs as before and
	 * replaces them when it is done.
	 */
	private val mangaDetails = MutableStateFlow<List<Manga>?>(null)
	val onScheduled = MutableEventFlow<Boolean>()
	val defaultFormat = MutableStateFlow<DownloadFormat?>(null)
	val availableDestinations = MutableStateFlow(listOf(defaultDestination()))
	val chaptersSelectOptions = MutableStateFlow(
		ChapterSelectOptions(
			wholeManga = ChaptersSelectMacro.WholeManga(0),
			wholeBranch = null,
			firstChapters = null,
			unreadChapters = null,
		),
	)
	val isOptionsLoading = MutableStateFlow(true)

	init {
		launchJob(Dispatchers.IO) {
			defaultFormat.value = settings.preferredDownloadFormat
		}
		launchJob(Dispatchers.IO) {
			try {
				val known = coroutineScope {
					manga.map { m -> async { m.getKnownDetails() } }.awaitAll()
				}
				if (known.all { it != null }) {
					mangaDetails.value = known.filterNotNull()
					loadAvailableOptions()
					isOptionsLoading.value = false
				}
				val fresh = coroutineScope {
					manga.map { m -> async { m.getDetails() } }.awaitAll()
				}
				// A manga whose update failed keeps the chapters known before instead of losing them.
				mangaDetails.value = fresh.mapIndexed { i, m -> if (m.chapters.isNullOrEmpty()) known[i] ?: m else m }
				loadAvailableOptions()
			} finally {
				isOptionsLoading.value = false
			}
		}
		loadAvailableDestinations()
	}

	fun confirm(
		startNow: Boolean,
		chaptersMacro: ChaptersSelectMacro,
		format: DownloadFormat?,
		destination: DirectoryModel?,
		allowMetered: Boolean,
	) {
		launchLoadingJob(Dispatchers.IO) {
			val requiresCharging = settings.isDownloadOnlyOnChargingEnabled
			// "Whole manga" needs no chapter list here: with none known yet, the download starts at once and the
			// download itself takes the stored chapters, or loads them.
			val details = mangaDetails.value
				?: if (startNow && chaptersMacro is ChaptersSelectMacro.WholeManga) null else mangaDetails.filterNotNull().first()
			val tasks = details?.map { m ->
				val chapters = checkNotNull(m.chapters) { "Manga \"${m.title}\" cannot be loaded" }
				val chaptersIds = chaptersMacro.getChaptersIds(m.id, chapters)?.toLongArray() ?: longArrayOf()
				m to chaptersIds
			} ?: manga.map { m -> m to longArrayOf() }

			if (startNow) {
				android.util.Log.d("DownloadDialog", "Starting downloads immediately for ${tasks.size} manga")
				val downloadTasks = tasks.map { (m, chaptersIds) ->
					m to DownloadTask(
						mangaId = m.id,
						isPaused = false,
						isSilent = false,
						chaptersIds = chaptersIds,
						destination = destination?.file,
						format = format,
						allowMeteredNetwork = allowMetered,
						requiresCharging = requiresCharging,
					)
				}
				scheduler.schedule(downloadTasks)
			} else {
				android.util.Log.d("DownloadDialog", "Adding ${tasks.size} manga to download queue as paused")
				tasks.forEach { (m, chaptersIds) ->
					downloadQueueRepository.addToQueue(
						manga = m,
						chaptersIds = chaptersIds,
						wifiOnly = !allowMetered,
						chargingOnly = requiresCharging,
						offPeakOnly = settings.isDownloadOffPeakEnabled,
						isPaused = true
					)
				}
			}
			onScheduled.call(startNow)
		}
	}

	fun setSelectedBranch(branch: String?) {
		val snapshot = chaptersSelectOptions.value
		chaptersSelectOptions.value = snapshot.copy(
			wholeBranch = snapshot.wholeBranch?.copy(branch),
		)
	}

	fun setFirstChaptersCount(count: Int) {
		val snapshot = chaptersSelectOptions.value
		chaptersSelectOptions.value = snapshot.copy(
			firstChapters = snapshot.firstChapters?.copy(count),
		)
	}

	fun setUnreadChaptersCount(count: Int) {
		val snapshot = chaptersSelectOptions.value
		chaptersSelectOptions.value = snapshot.copy(
			unreadChapters = snapshot.unreadChapters?.copy(count),
		)
	}

	private fun defaultDestination() = DirectoryModel(
		title = null,
		titleRes = R.string.system_default,
		file = null,
		isRemovable = false,
		isChecked = true,
		isAvailable = true,
	)

	private var hasOptions = false

	private suspend fun loadAvailableOptions() {
		val details = mangaDetails.value ?: return
		var totalChapters = 0
		val branches = ArrayMap<String?, Int>()
		var maxChapters = 0
		var maxUnreadChapters = 0
		val preferredBranches = ArraySet<String?>(details.size)
		val currentChaptersIds = MutableLongLongMap(details.size)

		details.forEach { m ->
			val history = historyRepository.getOne(m)
			if (history != null) {
				currentChaptersIds[m.id] = history.chapterId
				val unreadChaptersCount = m.chapters?.dropWhile { it.id != history.chapterId }.sizeOrZero()
				maxUnreadChapters = maxOf(maxUnreadChapters, unreadChaptersCount)
			} else {
				maxUnreadChapters = maxOf(maxUnreadChapters, m.chapters.sizeOrZero())
			}
			maxChapters = maxOf(maxChapters, m.chapters.sizeOrZero())
			preferredBranches.add(m.getPreferredBranch(history))
			m.chapters?.forEach { c ->
				totalChapters++
				branches.increment(c.branch)
			}
		}
		// Called again when the update from the source arrives: what the user chose meanwhile is kept.
		val previous = if (hasOptions) chaptersSelectOptions.value else null
		hasOptions = true
		val defaultBranch = previous?.wholeBranch?.selectedBranch?.takeIf { it in branches }
			?: preferredBranches.firstOrNull()
		chaptersSelectOptions.value = ChapterSelectOptions(
			wholeManga = ChaptersSelectMacro.WholeManga(totalChapters),
			wholeBranch = if (branches.size > 1) {
				ChaptersSelectMacro.WholeBranch(
					branches = branches,
					selectedBranch = defaultBranch,
				)
			} else {
				null
			},
			firstChapters = if (maxChapters > 0) {
				ChaptersSelectMacro.FirstChapters(
					chaptersCount = previous?.firstChapters?.chaptersCount?.coerceIn(1, maxChapters) ?: minOf(5, maxChapters),
					maxAvailableCount = maxChapters,
					branch = defaultBranch,
				)
			} else {
				null
			},
			unreadChapters = if (currentChaptersIds.isNotEmpty()) {
				ChaptersSelectMacro.UnreadChapters(
					chaptersCount = previous?.unreadChapters?.chaptersCount?.coerceIn(0, maxUnreadChapters)
						?: minOf(5, maxUnreadChapters),
					maxAvailableCount = maxUnreadChapters,
					currentChaptersIds = currentChaptersIds,
				)
			} else {
				null
			},
		)
	}

	private fun loadAvailableDestinations() = launchJob(Dispatchers.IO) {
		val defaultDir = manga.mapToSet {
			localMangaRepository.getOutputDir(it, null)
		}.singleOrNull()
		val dirs = localStorageManager.getWriteableDirs()
		availableDestinations.value = buildList(dirs.size + 1) {
			if (defaultDir == null) {
				add(defaultDestination())
			} else if (defaultDir !in dirs) {
				add(
					DirectoryModel(
						title = localStorageManager.getDirectoryDisplayName(defaultDir, isFullPath = false),
						titleRes = 0,
						file = defaultDir,
						isChecked = true,
						isAvailable = true,
						isRemovable = false,
					),
				)
			}
			dirs.mapTo(this) { dir ->
				DirectoryModel(
					title = localStorageManager.getDirectoryDisplayName(dir, isFullPath = false),
					titleRes = 0,
					file = dir,
					isChecked = dir == defaultDir,
					isAvailable = true,
					isRemovable = false,
				)
			}
		}
	}

	/**
	 * The chapters already known: the ones the details screen shows, just loaded ones (memory cache) or the stored
	 * ones; null when there are none. Each place is tried on its own, so one failing does not hide the others.
	 */
	private suspend fun Manga.getKnownDetails(): Manga? {
		shownMangaDetails.get(id)?.let { return it }
		runCatchingCancellable {
			(mangaRepositoryFactory.create(source) as? CachingMangaRepository)?.peekDetails(this)
		}.getOrNull()?.takeUnless { it.chapters.isNullOrEmpty() }?.let { return it }
		return runCatchingCancellable {
			mangaDataRepository.findMangaById(id, withChapters = true)
		}.getOrNull()?.takeUnless { it.chapters.isNullOrEmpty() }
	}

	private suspend fun Manga.getDetails(): Manga = runCatchingCancellable {
		mangaRepositoryFactory.create(source).getDetails(this)
	}.onFailure { e ->
		e.printStackTraceDebug("DownloadDialogViewModel::Manga")
	}.getOrDefault(this)

	private fun <T> MutableMap<T, Int>.increment(key: T) {
		put(key, getOrDefault(key, 0) + 1)
	}
}
