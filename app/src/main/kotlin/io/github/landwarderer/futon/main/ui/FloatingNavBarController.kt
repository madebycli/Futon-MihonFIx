package io.github.landwarderer.futon.main.ui

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.updateLayoutParams
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.ui.widgets.SlidingBottomNavigationView
import com.google.android.material.R as materialR

/**
 * The design of the bottom navigation bar: docked to the screen edge, or floating as a rounded bar with a gap around
 * it, and the round continue button beside it. The design follows the floating bottom bar of Kototoro (a rounded bar on a
 * surface container color with the continue button as a round button with the cover next to it); only the look is
 * taken over, the entries of the bar stay those of this app.
 */
class FloatingNavBarController(
	private val bottomNav: SlidingBottomNavigationView,
	private val roundFab: View?,
	private val settings: AppSettings,
) {

	private val density = bottomNav.resources.displayMetrics.density
	private val defaultBackground = bottomNav.background
	private val defaultElevation = bottomNav.elevation
	private val defaultPaddingTop = bottomNav.paddingTop
	private var insetLeft = 0
	private var insetRight = 0
	private var insetBottom = 0

	/** Whether the round continue button is shown now. */
	var isRoundFabVisible: Boolean = false
		set(value) {
			if (field != value) {
				field = value
				apply()
			}
		}

	fun onInsets(left: Int, right: Int, bottom: Int) {
		insetLeft = left
		insetRight = right
		insetBottom = bottom
		apply()
	}

	fun apply() {
		if (settings.isNavFloating) applyFloating() else applyDocked()
		alignRoundFab()
	}

	private fun applyFloating() {
		val spacing = dp(settings.navSpacing)
		val buttonSpace = if (isRoundFabVisible) dp(ROUND_FAB_SIZE_DP + ROUND_FAB_GAP_DP) else 0
		bottomNav.updateLayoutParams<MarginLayoutParams> {
			marginStart = spacing + insetLeft
			marginEnd = spacing + insetRight + buttonSpace
			bottomMargin = spacing + insetBottom
		}
		bottomNav.setPadding(0, defaultPaddingTop, 0, 0)
		val shape = ShapeAppearanceModel.builder()
			.setAllCornerSizes(settings.navCornerRadius * density)
			.build()
		bottomNav.background = MaterialShapeDrawable(shape).apply {
			fillColor = ColorStateList.valueOf(MaterialColors.getColor(bottomNav, materialR.attr.colorSurfaceContainer))
			initializeElevationOverlay(bottomNav.context)
		}
		bottomNav.elevation = dp(FLOATING_ELEVATION_DP).toFloat()
	}

	private fun applyDocked() {
		bottomNav.updateLayoutParams<MarginLayoutParams> {
			marginStart = 0
			marginEnd = 0
			bottomMargin = 0
		}
		bottomNav.setPadding(insetLeft, defaultPaddingTop, insetRight, insetBottom)
		bottomNav.background = defaultBackground
		bottomNav.elevation = defaultElevation
	}

	/** Puts the round button level with the bar: beside it when the bar floats, above its end when it is docked. */
	fun alignRoundFab() {
		val fab = roundFab ?: return
		val barHeight = bottomNav.height
		val margin = dp(ROUND_FAB_MARGIN_DP)
		fab.updateLayoutParams<MarginLayoutParams> {
			if (settings.isNavFloating) {
				val spacing = dp(settings.navSpacing)
				marginEnd = spacing + insetRight
				bottomMargin = spacing + insetBottom + ((barHeight - dp(ROUND_FAB_SIZE_DP)) / 2).coerceAtLeast(0)
			} else {
				marginEnd = margin + insetRight
				bottomMargin = barHeight + margin
			}
		}
	}

	private fun dp(value: Int) = (value * density).toInt()

	private companion object {

		const val ROUND_FAB_SIZE_DP = 56
		const val ROUND_FAB_GAP_DP = 10
		const val ROUND_FAB_MARGIN_DP = 12
		const val FLOATING_ELEVATION_DP = 6
	}
}
