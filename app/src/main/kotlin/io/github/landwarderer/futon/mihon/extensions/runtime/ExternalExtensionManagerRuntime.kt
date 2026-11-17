// Ported and adapted from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at
// f32242d649471627d8906a523511bc69217897eb: app/.../extensions/runtime/ExternalExtensionManagerRuntime.kt
package io.github.landwarderer.futon.mihon.extensions.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExternalExtensionManagerRuntime<ResultT, SuccessT, ErrorT, SourceT, WrappedSourceT>(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ecosystem: String = "external",
) {

    private val _installedExtensions = MutableStateFlow<List<SuccessT>>(emptyList())
    val installedExtensions: StateFlow<List<SuccessT>> = _installedExtensions.asStateFlow()

    private val _failedExtensions = MutableStateFlow<List<ErrorT>>(emptyList())
    val failedExtensions: StateFlow<List<ErrorT>> = _failedExtensions.asStateFlow()

    private val _wrappedSources = MutableStateFlow<List<WrappedSourceT>>(emptyList())
    val wrappedSources: StateFlow<List<WrappedSourceT>> = _wrappedSources.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Counts completed loads, so observers can tell that the source set may have changed. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val initialLoadComplete = CompletableDeferred<Unit>()

    /**
     * Loads run one after another. A package change that arrives during a load waits for it and then loads again,
     * instead of being dropped, so the source set never stays behind the installed extensions.
     */
    private val loadMutex = Mutex()

    /** Replaced as a whole after each load: readers on other threads never see a half-built set. */
    @Volatile
    private var sourceSnapshot = SourceSnapshot<SourceT, WrappedSourceT>()

    @Volatile
    private var isPackageObserverRegistered = false

    fun initialize(loadAction: suspend () -> Unit) {
        registerPackageObserver(loadAction)
        scope.launchInRuntime(loadAction)
    }

    suspend fun loadExtensions(
        loadResults: suspend (Context) -> List<ResultT>,
        processResults: (List<ResultT>) -> ProcessedExternalExtensions<SuccessT, ErrorT, SourceT, WrappedSourceT>,
    ) {
        loadMutex.withLock {
            _isLoading.value = true
            try {
                val loadStartedAt = System.nanoTime()
                val results = loadResults(context)
                val loadMs = (System.nanoTime() - loadStartedAt) / 1_000_000
                val processed = processResults(results)
                sourceSnapshot = SourceSnapshot(
                    sourceById = processed.sourceById,
                    wrappedSourceById = processed.wrappedSourceById,
                )
                _installedExtensions.value = processed.successful
                _failedExtensions.value = processed.failed
                _wrappedSources.value = processed.wrappedSourceById.values.toList()
                _changes.value++
                // Plain JVM unit tests have no Android logging.
                runCatching {
                    Log.i(
                        "ExtensionRuntime",
                        "$ecosystem results=${results.size} load_ms=$loadMs " +
                            "total_ms=${(System.nanoTime() - loadStartedAt) / 1_000_000}",
                    )
                }
            } finally {
                _isLoading.value = false
                initialLoadComplete.complete(Unit)
            }
        }
    }

    suspend fun awaitInitialLoad() {
        initialLoadComplete.await()
    }

    fun getInstalledExtensions(): List<SuccessT> = installedExtensions.value

    fun getSourceById(sourceId: Long): SourceT? = sourceSnapshot.sourceById[sourceId]

    fun getWrappedSourceById(sourceId: Long): WrappedSourceT? = sourceSnapshot.wrappedSourceById[sourceId]

    fun getWrappedSources(): List<WrappedSourceT> = sourceSnapshot.wrappedSourceById.values.toList()

    fun getSourceCount(): Int = sourceSnapshot.sourceById.size

    fun hasExtensions(): Boolean = installedExtensions.value.isNotEmpty()

    private fun registerPackageObserver(loadAction: suspend () -> Unit) {
        if (isPackageObserverRegistered) return
        registerExternalExtensionPackageObserver(context) {
            loadAction()
        }
        isPackageObserverRegistered = true
    }

    private fun CoroutineScope.launchInRuntime(loadAction: suspend () -> Unit) {
        launch {
            loadAction()
        }
    }

    private data class SourceSnapshot<SourceT, WrappedSourceT>(
        val sourceById: Map<Long, SourceT> = emptyMap(),
        val wrappedSourceById: Map<Long, WrappedSourceT> = emptyMap(),
    )
}
