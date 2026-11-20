package io.github.landwarderer.futon.mihon

import android.util.Log

/**
 * Logs the loads of extension sources the user waits for (start, end, duration, priority decisions), so a log from a
 * device shows where the time goes. Tag: MihonLoad. Does nothing in JVM unit tests, where Log is not available.
 */
internal object MihonLoadLog {

	private const val TAG = "MihonLoad"

	fun i(message: String) {
		try {
			Log.i(TAG, message)
		} catch (_: RuntimeException) {
			// android.util.Log is not mocked in JVM unit tests
		}
	}
}
