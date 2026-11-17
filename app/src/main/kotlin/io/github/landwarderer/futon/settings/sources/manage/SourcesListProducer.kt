package io.github.landwarderer.futon.settings.sources.manage

import android.content.Context
import androidx.room.InvalidationTracker
import dagger.hilt.android.ViewModelLifecycle
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.LocalizedAppContext
import io.github.landwarderer.futon.core.db.TABLE_SOURCES
import io.github.landwarderer.futon.core.model.getTitle
import io.github.landwarderer.futon.core.model.isNsfw
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.lifecycleScope
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.explore.data.SourcesSortOrder
import io.github.landwarderer.futon.mihon.MihonExtensionManager
import io.github.landwarderer.futon.settings.sources.model.SourceConfigItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.parsers.util.mapToSet
import javax.inject.Inject

@ViewModelScoped
class SourcesListProducer @Inject constructor(
	lifecycle: ViewModelLifecycle,
	@LocalizedAppContext private val context: Context,
	private val repository: MangaSourcesRepository,
	private val settings: AppSettings,
	private val mihonExtensionManager: MihonExtensionManager,
) : InvalidationTracker.Observer(TABLE_SOURCES) {

	private val scope = lifecycle.lifecycleScope
	@Volatile private var query: String = ""
	val list = MutableStateFlow(emptyList<SourceConfigItem>())
	internal val status = MutableStateFlow(SourceListStatus.LOADING)

	private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

	init {
		scope.launch(Dispatchers.IO) {
			consumeSourceRefreshes(refreshRequests) { loadCurrentQuery() }
		}
		refreshRequests.trySend(Unit)
		combine(
			settings.observeChanges()
				.onStart { emit(AppSettings.KEY_TIPS_CLOSED) }
				.filter { it == AppSettings.KEY_TIPS_CLOSED || it == AppSettings.KEY_DISABLE_NSFW },
			mihonExtensionManager.installedExtensions,
			mihonExtensionManager.failedExtensions,
			mihonExtensionManager.isLoading,
		) { _, _, _, _ -> }
			.flowOn(Dispatchers.IO)
			.onEach { onInvalidated(emptySet()) }
			.launchIn(scope)
	}

	override fun onInvalidated(tables: Set<String>) {
		refreshRequests.trySend(Unit)
	}

	fun retry() {
		scope.launch(Dispatchers.IO) {
			if (loadSourceList { mihonExtensionManager.loadExtensions() }.isFailure) {
				status.value = SourceListStatus.ERROR
			} else {
				onInvalidated(emptySet())
			}
		}
	}

	fun setQuery(value: String) {
		if (this.query == value) return
		this.query = value
		onInvalidated(emptySet())
	}

	private suspend fun loadCurrentQuery() {
		val currentQuery = query
		if (list.value.isEmpty()) status.value = SourceListStatus.LOADING
		val result = loadSourceList { buildList(currentQuery) }
		// Do not publish a result for a search which has already changed.
		if (currentQuery != query) return
		val items = result.getOrElse {
			// Preserve the last usable rows; expose retry without resetting user choices.
			status.value = SourceListStatus.ERROR
			return
		}
		list.value = items
		status.value = sourceListStatus(items.isEmpty(), currentQuery.isNotEmpty(), mihonExtensionManager.isLoading.value)
	}

	private suspend fun buildList(query: String): List<SourceConfigItem> {
		val enabledSources = repository.getEnabledSources()
		val pinned = repository.getPinnedSources().mapToSet { it.name }
		val isNsfwDisabled = settings.isNsfwContentDisabled
		val isReorderAvailable = settings.sourcesSortOrder == SourcesSortOrder.MANUAL
		val isDisableAvailable = !settings.isAllSourcesEnabled
		val withTip = isReorderAvailable && settings.isTipEnabled(TIP_REORDER)
		val enabledSet = enabledSources.toSet()
		if (query.isNotEmpty()) {
			return enabledSources.mapNotNull {
				if (!it.getTitle(context).contains(query, ignoreCase = true)) {
					return@mapNotNull null
				}
				SourceConfigItem.SourceItem(
					source = it,
					isEnabled = it in enabledSet,
					isDraggable = false,
					isAvailable = !isNsfwDisabled || !it.isNsfw(),
					isPinned = it.name in pinned,
					isDisableAvailable = isDisableAvailable,
				)
			}.ifEmpty {
				listOf(SourceConfigItem.EmptySearchResult)
			}
		}
		val result = ArrayList<SourceConfigItem>(enabledSources.size + 1)
		if (enabledSources.isNotEmpty()) {
			if (withTip) {
				result += SourceConfigItem.Tip(
					TIP_REORDER,
					R.drawable.ic_tap_reorder,
					R.string.sources_reorder_tip,
				)
			}
			enabledSources.mapTo(result) {
				SourceConfigItem.SourceItem(
					source = it,
					isEnabled = true,
					isDraggable = isReorderAvailable,
					isAvailable = false,
					isPinned = it.name in pinned,
					isDisableAvailable = isDisableAvailable,
				)
			}
		}
		return result
	}

	companion object {

		const val TIP_REORDER = "src_reorder"
	}
}
