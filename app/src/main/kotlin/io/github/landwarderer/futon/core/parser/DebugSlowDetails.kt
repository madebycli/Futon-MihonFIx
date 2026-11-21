package io.github.landwarderer.futon.core.parser

import io.github.landwarderer.futon.BuildConfig
import kotlinx.coroutines.delay

/**
 * Test aid for debug builds only: makes every details load of a source take [delayMs] longer, to check how the app
 * behaves with a slow source (the emulator checks set it through the preference "debug_details_delay_ms"). Has no
 * effect in release builds.
 */
object DebugSlowDetails {

	@Volatile
	var delayMs: Long = 0L

	suspend fun apply() {
		if (BuildConfig.DEBUG && delayMs > 0) {
			delay(delayMs)
		}
	}
}
