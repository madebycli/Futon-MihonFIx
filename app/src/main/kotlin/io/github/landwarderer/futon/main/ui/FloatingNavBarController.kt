package io.github.landwarderer.futon.main.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.core.view.updateLayoutParams
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.NavItem
import io.github.landwarderer.futon.core.ui.widgets.FloatingNavRow
import io.github.landwarderer.futon.core.ui.widgets.SlidingBottomNavigationView
import org.koitharu.kotatsu.parsers.model.Manga
import kotlin.math.roundToInt
import com.google.android.material.R as materialR

/**
 * The design of the bottom navigation bar, from the settings of Appearance → Main screen:
 *
 *  - docked: the Material bar along the bottom edge, as before; optionally on a raised rounded surface
 *    (Kototoro's "layered surface": 16 dp corners, surfaceContainer, 6 dp shadow, 12/6 dp margin);
 *  - floating: Kototoro's compact rounded bar ([FloatingNavRow]) above the bottom edge.
 *
 * In both designs the round continue button ([RoundResumeButton]) sits at the end of the bar, the size of the bar,
 * as Kototoro's ContinueReadingFab next to its bottom bar. The sections, the selection and the badges stay those of
 * the [SlidingBottomNavigationView]; the floating bar only shows them.
 */
class FloatingNavBarController(
	private val bottomNav: SlidingBottomNavigationView,
	private val settings: AppSettings,
	onResumeClick: View.OnClickListener,
) {

	private val context = bottomNav.context
	private val density = context.resources.displayMetrics.density
	private val defaultBackground = bottomNav.background
	private val defaultElevation = bottomNav.elevation
	private val defaultPaddingTop = bottomNav.paddingTop
	private val defaultIconTint = bottomNav.itemIconTintList
	private val defaultTextColor = bottomNav.itemTextColor
	private val resumeButton = RoundResumeButton(context).apply { setOnClickListener(onResumeClick) }
	private var floatingRow: FloatingNavRow? = null
	private var insetLeft = 0
	private var insetRight = 0
	private var insetBottom = 0

	/** Whether the round continue button is shown now; [MainActivity] decides it from the settings and the state. */
	var isResumeVisible: Boolean = false
		set(value) {
			if (field != value) {
				field = value
				apply()
			}
		}

	init {
		bottomNav.onMenuStateChanged = { syncEntries() }
		bottomNav.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
			val isResized = bottom - top != oldBottom - oldTop || right - left != oldRight - oldLeft
			if (isResized && bottomNav.floatingRow == null) {
				bottomNav.post { placeDockedResumeButton() }
			}
		}
	}

	fun setResumeManga(manga: Manga?) {
		resumeButton.setManga(manga)
	}

	fun onInsets(left: Int, right: Int, bottom: Int) {
		insetLeft = left
		insetRight = right
		insetBottom = bottom
		apply()
	}

	/** Applies the current settings; switching the design resets everything the other design changed. */
	fun apply() {
		if (settings.isNavFloating) applyFloating() else applyDocked()
	}

	private fun applyFloating() {
		val row = floatingRow ?: FloatingNavRow(context).also {
			it.onItemClick = { id -> bottomNav.selectedItemId = id }
			floatingRow = it
		}
		row.setStyle(
			FloatingNavRow.Style(
				labelMode = settings.navLabelMode,
				isCapsuleEnabled = settings.isNavCapsuleEnabled,
				isFullWidth = settings.isNavFullWidth,
				contentHeight = settings.navFloatingHeight,
				cornerRadius = settings.navCornerRadius,
				opacity = settings.navOpacity / 100f,
				isAccentColored = settings.isNavAccentColored,
			),
		)
		resetMenuViewMargin()
		row.setAdjacentView(resumeButton.takeIf { isResumeVisible })
		resumeButton.isVisible = isResumeVisible
		bottomNav.floatingRow = row
		syncEntries()
		bottomNav.background = null
		bottomNav.elevation = 0f
		// The shadows of the bar and of the button fall below this view, into the gap above the screen edge.
		bottomNav.clipChildren = false
		bottomNav.clipToPadding = false
		// Kototoro keeps the floating bar clear of the system navigation and the screen edges. The gap is padding,
		// not a margin, so the shadows of the bar and of the button are drawn inside this view instead of being cut
		// off by its parent; touches in the gap still go through to the content (see dispatchTouchEvent).
		bottomNav.setPadding(insetLeft, 0, insetRight, dp(settings.navSpacing) + insetBottom)
		bottomNav.updateLayoutParams<MarginLayoutParams> {
			marginStart = 0
			marginEnd = 0
			bottomMargin = 0
		}
	}

	private fun applyDocked() {
		floatingRow?.setAdjacentView(null)
		bottomNav.floatingRow = null
		bottomNav.clipChildren = true
		// The round button's shadow may fall into the padding of the system navigation.
		bottomNav.clipToPadding = false
		if (settings.isNavLayeredSurface) {
			bottomNav.updateLayoutParams<MarginLayoutParams> {
				marginStart = dp(LAYERED_MARGIN_HORIZONTAL_DP) + insetLeft
				marginEnd = dp(LAYERED_MARGIN_HORIZONTAL_DP) + insetRight
				bottomMargin = dp(LAYERED_MARGIN_VERTICAL_DP)
			}
			bottomNav.setPadding(0, defaultPaddingTop, 0, insetBottom)
			bottomNav.background = MaterialShapeDrawable(
				ShapeAppearanceModel.builder().setAllCornerSizes(dp(settings.navCornerRadius).toFloat()).build(),
			).apply {
				fillColor = ColorStateList.valueOf(MaterialColors.getColor(bottomNav, materialR.attr.colorSurfaceContainer))
				alpha = opacityAlpha()
			}
			bottomNav.elevation = if (opacityAlpha() < 255) 0f else dp(LAYERED_ELEVATION_DP).toFloat()
		} else {
			bottomNav.updateLayoutParams<MarginLayoutParams> {
				marginStart = 0
				marginEnd = 0
				bottomMargin = 0
			}
			bottomNav.setPadding(insetLeft, defaultPaddingTop, insetRight, insetBottom)
			bottomNav.background = defaultBackground?.constantState?.newDrawable(context.resources)?.mutate()?.apply {
				alpha = opacityAlpha()
			} ?: defaultBackground
			bottomNav.elevation = if (opacityAlpha() < 255) 0f else defaultElevation
		}
		bottomNav.dockedHeightOverride = settings.navHeight?.let { dp(it) }
		applyDockedColors()
		if (isResumeVisible) {
			if (resumeButton.parent !== bottomNav) {
				(resumeButton.parent as? android.view.ViewGroup)?.removeView(resumeButton)
				bottomNav.addView(resumeButton)
			}
			resumeButton.isVisible = true
			placeDockedResumeButton()
		} else {
			if (resumeButton.parent === bottomNav) {
				bottomNav.removeView(resumeButton)
			}
			resetMenuViewMargin()
		}
	}

	private fun opacityAlpha() = (settings.navOpacity * 255 / 100).coerceIn(0, 255)

	/** The selected section of the docked bar in the accent color, or the colors of the bar's style. */
	private fun applyDockedColors() {
		if (settings.isNavAccentColored) {
			val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
			val accent = MaterialColors.getColor(bottomNav, androidx.appcompat.R.attr.colorPrimary)
			val normal = MaterialColors.getColor(bottomNav, materialR.attr.colorOnSurfaceVariant)
			bottomNav.itemIconTintList = ColorStateList(states, intArrayOf(accent, normal))
			bottomNav.itemTextColor = ColorStateList(states, intArrayOf(accent, normal))
		} else {
			bottomNav.itemIconTintList = defaultIconTint
			bottomNav.itemTextColor = defaultTextColor
		}
	}

	/**
	 * The docked bar gives the button its end: the sections make room, and the button sits 12 dp from the edge, like
	 * the Row of Kototoro's docked bar with the button beside it. Kototoro makes it as high as the bar (80 dp); here it
	 * stops at 64 dp, which keeps it clear of the bar's edges.
	 */
	private fun placeDockedResumeButton() {
		if (resumeButton.parent !== bottomNav || bottomNav.floatingRow != null) return
		val menuView = bottomNav.getChildAt(0) ?: return
		val size = (menuView.height.takeIf { it > 0 } ?: dp(DOCKED_RESUME_FALLBACK_DP))
			.coerceIn(dp(DOCKED_RESUME_MIN_DP), dp(DOCKED_RESUME_MAX_DP))
		resumeButton.updateLayoutParams<FrameLayout.LayoutParams> {
			width = size
			height = size
			gravity = Gravity.END or Gravity.CENTER_VERTICAL
			marginEnd = dp(DOCKED_RESUME_MARGIN_DP)
		}
		// The menu view lays its items out over the width it is given and centres itself, so it gets an exact,
		// narrower width at the start instead of a margin (with a margin the items ran off the start edge).
		val reserved = size + dp(DOCKED_RESUME_MARGIN_DP + DOCKED_RESUME_GAP_DP)
		val available = bottomNav.width - bottomNav.paddingLeft - bottomNav.paddingRight
		if (available <= reserved) return
		menuView.updateLayoutParams<FrameLayout.LayoutParams> {
			if (menuViewParams == null) {
				menuViewParams = FrameLayout.LayoutParams(this)
			}
			width = available - reserved
			gravity = Gravity.START or (menuViewParams!!.gravity and Gravity.VERTICAL_GRAVITY_MASK)
		}
	}

	/** The layout params the menu view had before the docked button took its end. */
	private var menuViewParams: FrameLayout.LayoutParams? = null

	private fun resetMenuViewMargin() {
		val original = menuViewParams ?: return
		bottomNav.getChildAt(0)?.updateLayoutParams<FrameLayout.LayoutParams> {
			width = original.width
			gravity = original.gravity
		}
		menuViewParams = null
	}

	/** Copies the visible sections, the selection and the badges of the docked bar into the floating bar. */
	private fun syncEntries() {
		val row = floatingRow ?: return
		val entries = ArrayList<FloatingNavRow.Entry>(bottomNav.menu.size())
		for (item in bottomNav.menu) {
			if (!item.isVisible) continue
			val navItem = NavItem.entries.firstOrNull { it.id == item.itemId } ?: continue
			val badge = bottomNav.getBadge(item.itemId)?.takeIf { it.isVisible }
			entries += FloatingNavRow.Entry(
				id = item.itemId,
				title = item.title ?: context.getString(navItem.title),
				icon = navItem.icon,
				badge = badge?.let { if (it.hasNumber()) it.number else 0 },
			)
		}
		row.setEntries(entries, bottomNav.selectedItemId)
	}

	private fun dp(value: Int) = (value * density).roundToInt()

	private companion object {

		const val LAYERED_MARGIN_HORIZONTAL_DP = 12
		const val LAYERED_MARGIN_VERTICAL_DP = 6
		const val LAYERED_ELEVATION_DP = 6
		const val DOCKED_RESUME_MARGIN_DP = 12
		const val DOCKED_RESUME_GAP_DP = 8
		const val DOCKED_RESUME_MIN_DP = 48
		const val DOCKED_RESUME_MAX_DP = 64
		const val DOCKED_RESUME_FALLBACK_DP = 56
	}
}
