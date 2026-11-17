package io.github.landwarderer.futon.settings.sources.manage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SourceListStatusTest {
    @Test fun emptySourcesDuringScanRemainLoading() {
        assertEquals(SourceListStatus.LOADING, sourceListStatus(true, false, true))
    }
    @Test fun noSourcesAfterScanOfferTheCatalog() {
        assertEquals(SourceListStatus.EMPTY, sourceListStatus(true, false, false))
    }
    @Test fun loadedSourcesStayUsableWhileScanning() {
        assertEquals(SourceListStatus.READY, sourceListStatus(false, false, true))
    }
    @Test fun aSearchWithoutMatchesUsesTheExistingSearchEmptyRow() {
        assertEquals(SourceListStatus.READY, sourceListStatus(true, true, false))
    }
    @Test fun aFailedLoadCanBeRetriedWithoutResettingData() = runTest {
        var attempts = 0
        suspend fun load() = loadSourceList {
            attempts++
            if (attempts == 1) throw IllegalStateException("fixture")
            listOf("source-a")
        }
        assertTrue(load().isFailure)
        assertEquals(listOf("source-a"), load().getOrThrow())
    }
    @Test fun cancellationIsNotConvertedToAnError() = runTest {
        try {
            loadSourceList<List<String>> { throw CancellationException("cancelled") }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected.
        }
    }
}
