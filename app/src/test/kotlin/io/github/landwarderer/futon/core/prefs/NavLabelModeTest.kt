package io.github.landwarderer.futon.core.prefs

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class NavLabelModeTest {

	@Test
	fun withoutAStoredChoiceTheLegacySwitchDecides() {
		assertEquals(NavLabelMode.ALL, NavLabelMode.read(stored = null, legacyLabels = true))
		assertEquals(NavLabelMode.NONE, NavLabelMode.read(stored = null, legacyLabels = false))
	}

	@Test
	fun aStoredChoiceWinsOverTheLegacySwitch() {
		assertEquals(NavLabelMode.SELECTED, NavLabelMode.read(stored = "SELECTED", legacyLabels = false))
		assertEquals(NavLabelMode.NONE, NavLabelMode.read(stored = "NONE", legacyLabels = true))
		assertEquals(NavLabelMode.SELECTED_BESIDE, NavLabelMode.read(stored = "SELECTED_BESIDE", legacyLabels = true))
	}

	@Test
	fun anUnknownStoredValueFallsBackToTheLegacySwitch() {
		assertEquals(NavLabelMode.ALL, NavLabelMode.read(stored = "SOMETHING_NEW", legacyLabels = true))
	}

	@Test
	fun migrationWritesTheLegacyValueOnceAndKeepsTheSwitch() {
		val prefs = InMemoryPreferences()
		prefs.edit().putBoolean(AppSettings.KEY_NAV_LABELS, true).commit()
		val settings = AppSettings(mock(Context::class.java), prefs)

		settings.migrateNavLabelMode()

		assertEquals("ALL", prefs.getString(AppSettings.KEY_NAV_LABEL_MODE, null))
		assertTrue(prefs.getBoolean(AppSettings.KEY_NAV_LABELS, false))
		assertEquals(NavLabelMode.ALL, settings.navLabelMode)
	}

	@Test
	fun migrationOfAFreshInstallKeepsTheBarWithoutLabels() {
		val prefs = InMemoryPreferences()
		val settings = AppSettings(mock(Context::class.java), prefs)

		settings.migrateNavLabelMode()

		assertEquals("NONE", prefs.getString(AppSettings.KEY_NAV_LABEL_MODE, null))
		assertEquals(NavLabelMode.NONE, settings.navLabelMode)
	}

	@Test
	fun migrationKeepsAnExistingChoice() {
		val prefs = InMemoryPreferences()
		prefs.edit()
			.putBoolean(AppSettings.KEY_NAV_LABELS, true)
			.putString(AppSettings.KEY_NAV_LABEL_MODE, "SELECTED")
			.commit()
		val settings = AppSettings(mock(Context::class.java), prefs)

		settings.migrateNavLabelMode()

		assertEquals(NavLabelMode.SELECTED, settings.navLabelMode)
	}

	@Test
	fun theLegacySwitchFollowsTheChoiceForADowngrade() {
		val prefs = InMemoryPreferences()
		val settings = AppSettings(mock(Context::class.java), prefs)
		prefs.edit().putString(AppSettings.KEY_NAV_LABEL_MODE, "SELECTED").commit()

		settings.syncLegacyNavLabels()
		assertTrue(prefs.getBoolean(AppSettings.KEY_NAV_LABELS, false))

		prefs.edit().putString(AppSettings.KEY_NAV_LABEL_MODE, "NONE").commit()
		settings.syncLegacyNavLabels()
		assertFalse(prefs.getBoolean(AppSettings.KEY_NAV_LABELS, true))
	}

	@Test
	fun floatingBarValuesStayInTheirRange() {
		val prefs = InMemoryPreferences()
		val settings = AppSettings(mock(Context::class.java), prefs)
		assertEquals(52, settings.navFloatingHeight)
		assertEquals(28, settings.navCornerRadius)
		assertEquals(16, settings.navSpacing)
		prefs.edit()
			.putInt(AppSettings.KEY_NAV_FLOATING_HEIGHT, 500)
			.putInt(AppSettings.KEY_NAV_CORNER_RADIUS, -3)
			.putInt(AppSettings.KEY_NAV_SPACING, 99)
			.commit()
		assertEquals(84, settings.navFloatingHeight)
		assertEquals(0, settings.navCornerRadius)
		assertEquals(32, settings.navSpacing)
	}
}

class NavBarSettingsTest {

	@Test
	fun theDockedHeightIsUnsetUntilItIsChosen() {
		val prefs = InMemoryPreferences()
		val settings = AppSettings(mock(Context::class.java), prefs)
		assertEquals(null, settings.navHeight)
		prefs.edit().putInt(AppSettings.KEY_NAV_HEIGHT, 64).commit()
		assertEquals(64, settings.navHeight)
		prefs.edit().putInt(AppSettings.KEY_NAV_HEIGHT, 10).commit()
		assertEquals(AppSettings.NAV_HEIGHT_MIN, settings.navHeight)
	}

	@Test
	fun opacityAndAccentHaveTheOldLookByDefault() {
		val prefs = InMemoryPreferences()
		val settings = AppSettings(mock(Context::class.java), prefs)
		assertEquals(100, settings.navOpacity)
		assertFalse(settings.isNavAccentColored)
		prefs.edit().putInt(AppSettings.KEY_NAV_OPACITY, 5).commit()
		assertEquals(AppSettings.NAV_OPACITY_MIN, settings.navOpacity)
	}
}

/** A plain map behind the SharedPreferences interface, enough for the settings that are read and written here. */
private class InMemoryPreferences : SharedPreferences {

	private val values = HashMap<String, Any?>()

	override fun getAll(): Map<String, *> = values.toMap()
	override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
	@Suppress("UNCHECKED_CAST")
	override fun getStringSet(key: String, defValues: Set<String>?) = values[key] as? Set<String> ?: defValues
	override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
	override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
	override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
	override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
	override fun contains(key: String) = values.containsKey(key)
	override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
	override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

	override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
		private val pending = HashMap<String, Any?>()
		private val removed = HashSet<String>()
		private var clear = false
		override fun putString(key: String, value: String?) = apply { pending[key] = value }
		override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
		override fun putInt(key: String, value: Int) = apply { pending[key] = value }
		override fun putLong(key: String, value: Long) = apply { pending[key] = value }
		override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
		override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
		override fun remove(key: String) = apply { removed += key }
		override fun clear() = apply { clear = true }
		override fun commit(): Boolean {
			if (clear) values.clear()
			removed.forEach { values.remove(it) }
			values.putAll(pending)
			return true
		}

		override fun apply() {
			commit()
		}
	}
}
