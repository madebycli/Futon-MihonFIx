package io.github.landwarderer.futon.mihon

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MihonLoadPriorityTest {

	@Test
	fun backgroundLoadWaitsForAUserLoadOfTheSameSource() = runTest {
		val userLoadRelease = CompletableDeferred<Unit>()
		var backgroundStarted = false
		val user = launch { MihonLoadPriority.run(SOURCE) { userLoadRelease.await() } }
		runCurrent()
		val background = async {
			withContext(MihonLoadPriority.Background()) {
				MihonLoadPriority.run(SOURCE) { backgroundStarted = true }
			}
		}
		advanceTimeBy(5_000)
		runCurrent()
		assertFalse(backgroundStarted)
		userLoadRelease.complete(Unit)
		user.join()
		advanceTimeBy(1_000)
		background.await()
		assertTrue(backgroundStarted)
	}

	@Test
	fun backgroundLoadOfAnotherSourceDoesNotWait() = runTest {
		val userLoadRelease = CompletableDeferred<Unit>()
		val user = launch { MihonLoadPriority.run(SOURCE) { userLoadRelease.await() } }
		runCurrent()
		val result = withContext(MihonLoadPriority.Background()) {
			MihonLoadPriority.run(OTHER_SOURCE) { 42 }
		}
		assertEquals(42, result)
		userLoadRelease.complete(Unit)
		user.join()
	}

	@Test
	fun userLoadsDoNotWaitForEachOther() = runTest {
		val first = CompletableDeferred<Unit>()
		val running = launch { MihonLoadPriority.run(SOURCE) { first.await() } }
		runCurrent()
		assertEquals("second", MihonLoadPriority.run(SOURCE) { "second" })
		first.complete(Unit)
		running.join()
	}

	private companion object {
		const val SOURCE = 7537715367149829912L
		const val OTHER_SOURCE = 1L
	}
}
