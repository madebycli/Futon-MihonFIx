package io.github.landwarderer.futon.main.ui

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.navigation.NavigationBarView
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.explore.ui.ExploreFragment
import io.github.landwarderer.futon.history.ui.HistoryListFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RapidTabSelectionTest {

	@get:Rule val hilt = HiltAndroidRule(this)

	@Test
	fun latestSelectionOwnsTheOnlyResumedTab() {
		hilt.inject()
		ActivityScenario.launch(MainActivity::class.java).use { scenario ->
			scenario.onActivity { it.navigation().selectedItemId = R.id.nav_history }
			assertCurrent(scenario, R.id.nav_history, HistoryListFragment::class.java)
			for ((id, target) in listOf(
				R.id.nav_history to HistoryListFragment::class.java,
				R.id.nav_explore to ExploreFragment::class.java,
			)) {
				scenario.onActivity {
					it.navigation().selectedItemId = R.id.nav_favorites
					it.navigation().selectedItemId = id
				}
				assertCurrent(scenario, id, target)
			}
			scenario.recreate()
			assertCurrent(scenario, R.id.nav_explore, ExploreFragment::class.java)
		}
	}

	private fun MainActivity.navigation(): NavigationBarView = bottomNav ?: findViewById(R.id.navRail)

	private fun assertCurrent(scenario: ActivityScenario<MainActivity>, id: Int, target: Class<*>) {
		val deadline = SystemClock.elapsedRealtime() + 5000
		var settled = false
		while (!settled && SystemClock.elapsedRealtime() < deadline) {
			scenario.onActivity {
				val tabs = it.supportFragmentManager.fragments.filter { f -> f.tag?.startsWith("primary:") == true }
				val current = tabs.singleOrNull { f -> !f.isHidden }
				assertEquals(tabs.size, tabs.distinctBy { f -> f.tag }.size)
				settled = target.isInstance(current) && current?.lifecycle?.currentState == Lifecycle.State.RESUMED &&
					tabs.count { f -> f.lifecycle.currentState == Lifecycle.State.RESUMED } == 1 &&
					it.navigation().selectedItemId == id
			}
			if (!settled) SystemClock.sleep(25)
		}
		assertTrue("Expected exactly one visible, resumed tab for ${target.simpleName}", settled)
	}
}
