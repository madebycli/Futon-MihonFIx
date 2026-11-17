// Adapted from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at f32242d649471627d8906a523511bc69217897eb:
// app/src/test/kotlin/org/skepsun/kototoro/extensions/runtime/ExternalExtensionManagerRuntimeTest.kt
package io.github.landwarderer.futon.mihon.extensions.runtime

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ExternalExtensionManagerRuntimeTest {

    private fun newRuntime() =
        ExternalExtensionManagerRuntime<FakeResult, FakeSuccess, FakeError, FakeSource, FakeWrappedSource>(
            context = mock(Context::class.java),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

    @Test
    fun loadExtensionsUpdatesStateAndCachesProcessedResults() = runBlocking {
        val source = FakeSource(1L)
        val wrapped = FakeWrappedSource(1L)
        val runtime = newRuntime()

        assertFalse(runtime.isLoading.value)
        assertTrue(runtime.installedExtensions.value.isEmpty())
        assertTrue(runtime.failedExtensions.value.isEmpty())

        runtime.loadExtensions(
            loadResults = { listOf(FakeResult.Ok) },
            processResults = {
                assertEquals(listOf(FakeResult.Ok), it)
                ProcessedExternalExtensions(
                    successful = listOf(FakeSuccess("pkg.ok")),
                    failed = listOf(FakeError("pkg.err")),
                    sourceById = mapOf(source.id to source),
                    wrappedSourceById = mapOf(wrapped.id to wrapped),
                    untrustedPackages = listOf("pkg.untrusted"),
                )
            },
        )

        assertFalse(runtime.isLoading.value)
        assertEquals(listOf(FakeSuccess("pkg.ok")), runtime.installedExtensions.value)
        assertEquals(listOf(FakeError("pkg.err")), runtime.failedExtensions.value)
        assertSame(source, runtime.getSourceById(1L))
        assertSame(wrapped, runtime.getWrappedSourceById(1L))
        assertEquals(listOf(wrapped), runtime.getWrappedSources())
        assertEquals(listOf(wrapped), runtime.wrappedSources.value)
        assertEquals(1, runtime.getSourceCount())
        assertTrue(runtime.hasExtensions())
        assertNull(runtime.getSourceById(99L))
        runtime.awaitInitialLoad()
    }

    @Test
    fun loadExtensionsQueuesARefreshRequestedWhileAnotherLoadIsRunning() = runBlocking {
        val runtime = newRuntime()
        val firstLoadStarted = CompletableDeferred<Unit>()
        val releaseFirstLoad = CompletableDeferred<Unit>()

        val firstLoad = async(Dispatchers.Default) {
            runtime.loadExtensions(
                loadResults = {
                    firstLoadStarted.complete(Unit)
                    releaseFirstLoad.await()
                    listOf(FakeResult.Ok)
                },
                processResults = { processedExtensions("pkg.old") },
            )
        }
        firstLoadStarted.await()
        val refreshLoad = async(Dispatchers.Default) {
            runtime.loadExtensions(
                loadResults = { listOf(FakeResult.Ok) },
                processResults = { processedExtensions("pkg.new") },
            )
        }

        releaseFirstLoad.complete(Unit)
        firstLoad.await()
        refreshLoad.await()

        assertEquals(listOf(FakeSuccess("pkg.new")), runtime.installedExtensions.value)
        assertEquals(2, runtime.changes.value)
    }

    private fun processedExtensions(packageName: String) = ProcessedExternalExtensions(
        successful = listOf(FakeSuccess(packageName)),
        failed = emptyList<FakeError>(),
        sourceById = emptyMap<Long, FakeSource>(),
        wrappedSourceById = emptyMap<Long, FakeWrappedSource>(),
        untrustedPackages = emptyList(),
    )

    private sealed interface FakeResult {
        data object Ok : FakeResult
    }

    private data class FakeSuccess(val pkgName: String)

    private data class FakeError(val pkgName: String)

    private data class FakeSource(val id: Long)

    private data class FakeWrappedSource(val id: Long)
}
