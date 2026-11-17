package io.github.landwarderer.futon.details.ui.pager

/**
 * The toolbar replaces menu items on invalidation. Defer replacement while an action view owns the
 * sheet lock, otherwise its collapse callback can be lost and the tabs stay hidden.
 */
internal class ExpandedMenuRefreshGate(
    private val onLock: () -> Unit,
    private val onUnlock: () -> Unit,
    private val onRefresh: () -> Unit,
) {
    private var expanded = false
    private var refreshPending = false
    private var disposed = false
    val isOpen: Boolean get() = expanded
    val isDisposed: Boolean get() = disposed

    fun open() {
        if (disposed || expanded) return
        expanded = true
        onLock()
    }

    fun requestRefresh() {
        if (disposed) return
        if (expanded) refreshPending = true else onRefresh()
    }

    fun close() {
        if (!expanded) return
        expanded = false
        onUnlock()
        if (refreshPending && !disposed) {
            refreshPending = false
            onRefresh()
        }
    }

    fun dispose() {
        disposed = true
        refreshPending = false
        close()
    }
}
