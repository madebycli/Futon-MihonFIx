package io.github.landwarderer.futon.settings.sources.manage

internal enum class SourceListStatus { LOADING, READY, EMPTY, ERROR }

/** Keep searching and extension discovery distinct from a truly empty enabled-source list. */
internal fun sourceListStatus(isEmpty: Boolean, isSearching: Boolean, isScanning: Boolean, hasFailures: Boolean): SourceListStatus = when {
    isScanning && isEmpty && !isSearching -> SourceListStatus.LOADING
    hasFailures -> SourceListStatus.ERROR
    !isEmpty || isSearching -> SourceListStatus.READY
    else -> SourceListStatus.EMPTY
}

/** Cancellation remains cancellation, while a retryable failure leaves current rows untouched. */
internal suspend fun <T> loadSourceList(load: suspend () -> T): Result<T> = try {
    Result.success(load())
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

/** Run at most one refresh at a time; a burst while it is running requests just one more pass. */
internal suspend fun consumeSourceRefreshes(
    requests: kotlinx.coroutines.channels.ReceiveChannel<Unit>,
    refresh: suspend () -> Unit,
) {
    for (request in requests) refresh()
}
