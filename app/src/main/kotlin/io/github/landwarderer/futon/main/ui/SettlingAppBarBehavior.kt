package io.github.landwarderer.futon.main.ui

import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

/**
 * The app bar behaviour of the main screen. Material's snap is replaced by [AppBarSettler]: the snap only runs at
 * the end of a nested scroll of a list, and some ways of moving the bar end without one (a fling that is handed on, a
 * switch of the tab, a pull to refresh), so the bar could stay halfway.
 */
class SettlingAppBarBehavior : AppBarLayout.Behavior() {

	/** Called instead of Material's snap when a nested scroll ends. */
	var onScrollStopped: (() -> Unit)? = null

	override fun onStopNestedScroll(
		coordinatorLayout: CoordinatorLayout,
		abl: AppBarLayout,
		target: View,
		type: Int,
	) {
		val callback = onScrollStopped
		if (callback == null) {
			super.onStopNestedScroll(coordinatorLayout, abl, target, type)
		} else {
			callback()
		}
	}
}

/**
 * One rule for the top bar, whatever moved it: once no finger is on the screen and the bar has stopped moving, a bar
 * that is only partly shown slides fully open. A bar that is fully shown or fully hidden stays as it is.
 *
 * It watches the bar itself (its offset) and the touches of the whole screen, not the scrolling of a list, so a fling
 * is waited for (the bar keeps moving until it ends) and no way of moving the bar is missed.
 */
class AppBarSettler(
	private val appBar: AppBarLayout,
	private val behavior: SettlingAppBarBehavior,
) : AppBarLayout.OnOffsetChangedListener {

	private var isTouching = false
	private val settle = Runnable { settleNow() }

	init {
		appBar.addOnOffsetChangedListener(this)
		behavior.onScrollStopped = ::schedule
	}

	/** To be called with every touch event of the screen (Activity.dispatchTouchEvent). */
	fun onTouchEvent(event: MotionEvent) {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				isTouching = true
				appBar.removeCallbacks(settle)
			}

			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				isTouching = false
				schedule()
			}
		}
	}

	override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
		// Still moving (a fling, or the opening itself): wait until it rests.
		if (!isTouching) schedule()
	}

	fun schedule() {
		appBar.removeCallbacks(settle)
		appBar.postDelayed(settle, SETTLE_DELAY_MS)
	}

	private fun settleNow() {
		if (isTouching || !appBar.isLaidOut || !appBar.isShown) return
		val range = appBar.totalScrollRange
		val offset = behavior.topAndBottomOffset
		if (range > 0 && offset < 0 && offset > -range) {
			appBar.setExpanded(true, true)
		}
	}

	private companion object {

		/** How long the bar has to rest before it opens: longer than a frame of a fling. */
		const val SETTLE_DELAY_MS = 80L
	}
}
