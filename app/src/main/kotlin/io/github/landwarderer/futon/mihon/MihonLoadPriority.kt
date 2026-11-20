package io.github.landwarderer.futon.mihon

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Loads the user waits for go first. Some extensions read a chapter list through one WebView, one manga at a time,
 * which takes minutes for a long list; the background check for new chapters could keep that WebView busy, and a
 * manga the user opened then waited behind it. While a load the user started for a source runs, background loads of
 * that source (marked with [Background]) wait before they start.
 */
object MihonLoadPriority {

	/** Marks a load that nobody waits for on screen, such as the check for new chapters. */
	class Background : AbstractCoroutineContextElement(Key) {
		companion object Key : CoroutineContext.Key<Background>
	}

	private val userLoads = ConcurrentHashMap<Long, Int>()

	suspend fun <T> run(sourceId: Long, block: suspend () -> T): T {
		if (currentCoroutineContext()[Background] != null) {
			while ((userLoads[sourceId] ?: 0) > 0) {
				delay(WAIT_STEP_MS)
			}
			return block()
		}
		userLoads.merge(sourceId, 1, Int::plus)
		try {
			return block()
		} finally {
			userLoads.compute(sourceId) { _, count -> if (count == null || count <= 1) null else count - 1 }
		}
	}

	private const val WAIT_STEP_MS = 500L
}
