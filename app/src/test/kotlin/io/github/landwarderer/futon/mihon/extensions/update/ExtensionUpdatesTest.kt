package io.github.landwarderer.futon.mihon.extensions.update

import org.junit.Assert.*
import org.junit.Test

class ExtensionUpdatesTest {
    @Test
    fun onlyInstalledOlderPackagesProduceUpdates() {
        val installed = mapOf("older" to 5L, "equal" to 7L, "newer" to 9L)
        val available = listOf("older" to 6L, "older" to 8L, "equal" to 7L, "newer" to 8L, "unknown" to 20L)
        assertEquals(setOf("older"), newerInstalledPackages(installed, available))
    }

    @Test
    fun partialRepositoryFailuresDoNotFinishOrThrottleTheCheck() {
        assertTrue(canCompleteUpdateCheck(emptyList()))
        assertTrue(canCompleteUpdateCheck(listOf(null, null)))
        assertFalse(canCompleteUpdateCheck(listOf(null, "offline")))
        assertFalse(canCompleteUpdateCheck(listOf("offline")))
    }

    @Test
    fun successfulChecksAreThrottledButClockRollbackAndFirstRunAreNot() {
        assertTrue(shouldCheckForUpdates(1000L, 0L, 500L))
        assertFalse(shouldCheckForUpdates(1000L, 600L, 500L))
        assertTrue(shouldCheckForUpdates(1100L, 600L, 500L))
        assertTrue(shouldCheckForUpdates(100L, 600L, 500L))
    }

    @Test
    fun theSameUpdatesHaveTheSameSignatureWhateverTheirOrder() {
        assertEquals(updateSignature(listOf("a" to 2L, "b" to 3L)), updateSignature(listOf("b" to 3L, "a" to 2L)))
        assertNotEquals(updateSignature(listOf("a" to 2L)), updateSignature(listOf("a" to 3L)))
        assertNotEquals(updateSignature(listOf("a" to 2L)), updateSignature(listOf("a" to 2L, "b" to 1L)))
    }
}
