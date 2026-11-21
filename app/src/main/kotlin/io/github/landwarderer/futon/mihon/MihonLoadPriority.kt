package io.github.landwarderer.futon.mihon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Loads the user waits for go first. Some extensions read a chapter list through one WebView, one manga at a time,
 * which takes minutes for a long list; the background check for new chapters could keep that WebView busy, and a
 * manga the user opened then waited behind it with only its cover and title on screen.
 *
 * When the user starts a load for a source (details or pages), background loads of that source (marked with
 * [Background]) that are running are cancelled ([PreemptedByUser]; the check simply does that manga on its next run),
 * and background loads that have not started yet wait until no load of the user is running for that source.
 */
object MihonLoadPriority {

	/** Marks a load that nobody waits for on screen, such as the check for new chapters. */
	class Background : AbstractCoroutineContextElement(Key) {
		companion object Key : CoroutineContext.Key<Background>
	}

	/** A background load gave way to a load of the user. */
	class PreemptedByUser : CancellationException("A load the user started for this source goes first")

	private val userLoads = ConcurrentHashMap<Long, Int>()
	private val backgroundLoads = ConcurrentHashMap<Long, MutableSet<Job>>()

	suspend fun <T> run(sourceId: Long, block: suspend () -> T): T {
		val context = currentCoroutineContext()
		if (context[Background] != null) {
			if ((userLoads[sourceId] ?: 0) > 0) {
				MihonLoadLog.i("background load waits for the user source=$sourceId")
				while ((userLoads[sourceId] ?: 0) > 0) {
					delay(WAIT_STEP_MS)
				}
			}
			val job = context[Job] ?: return block()
			val running = backgroundLoads.computeIfAbsent(sourceId) { ConcurrentHashMap.newKeySet() }
			running.add(job)
			try {
				return block()
			} finally {
				running.remove(job)
			}
		}
		userLoads.merge(sourceId, 1, Int::plus)
		try {
			backgroundLoads[sourceId]?.toList()?.takeIf { it.isNotEmpty() }?.let { running ->
				MihonLoadLog.i("user load cancels ${running.size} background load(s) source=$sourceId")
				running.forEach { it.cancel(PreemptedByUser()) }
			}
			return block()
		} finally {
			userLoads.compute(sourceId) { _, count -> if (count == null || count <= 1) null else count - 1 }
		}
	}

	private const val WAIT_STEP_MS = 500L
}
