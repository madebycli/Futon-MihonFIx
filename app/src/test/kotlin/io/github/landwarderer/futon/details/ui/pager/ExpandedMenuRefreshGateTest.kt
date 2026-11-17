package io.github.landwarderer.futon.details.ui.pager

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandedMenuRefreshGateTest {
    @Test
    fun menuChangesWaitUntilTheActionViewReleasesItsLock() {
        val events = mutableListOf<String>()
        val gate = ExpandedMenuRefreshGate({ events += "lock" }, { events += "unlock" }, { events += "refresh" })
        gate.requestRefresh()
        gate.open()
        gate.open()
        gate.requestRefresh()
        gate.requestRefresh()
        assertEquals(listOf("refresh", "lock"), events)
        gate.close()
        gate.close()
        assertEquals(listOf("refresh", "lock", "unlock", "refresh"), events)
    }

    @Test
    fun destroyingAViewReleasesItsOwnLockWithoutRefreshingTheDestroyedToolbar() {
        val events = mutableListOf<String>()
        val gate = ExpandedMenuRefreshGate({ events += "lock" }, { events += "unlock" }, { events += "refresh" })
        gate.open()
        gate.requestRefresh()
        gate.dispose()
        gate.close()
        gate.requestRefresh()
        gate.open()
        assertEquals(listOf("lock", "unlock"), events)
    }
}
