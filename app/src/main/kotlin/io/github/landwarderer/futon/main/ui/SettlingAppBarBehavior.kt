package io.github.landwarderer.futon.main.ui

import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.RecyclerView
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
	private val findList: () -> RecyclerView?,
) : AppBarLayout.OnOffsetChangedListener {

	private var isTouching = false
	private val settle = Runnable { settleNow() }

	/**
	 * While the bar slides open in the middle of a list, the list would move down with it. It is held in place
	 * instead: before every frame it scrolls by exactly as much as the bar moved it, so the bar slides over the
	 * content. At the top of a list the content moves with the bar, so the first item starts below it.
	 */
	private var heldList: RecyclerView? = null
	private var heldListY = 0
	private val location = IntArray(2)
	private val holdContent = ViewTreeObserver.OnPreDrawListener {
		val list = heldList
		if (list == null || !list.isAttachedToWindow || !list.isShown) {
			stopHolding()
		} else {
			list.getLocationInWindow(location)
			val delta = location[1] - heldListY
			heldListY = location[1]
			if (delta != 0) list.scrollBy(0, delta)
		}
		true
	}

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
				stopHolding()
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
		// Fully open: the last step is still held in the coming frame, then the list is let go.
		if (verticalOffset == 0 && heldList != null) appBar.post { stopHolding() }
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
			findList()?.takeIf { it.computeVerticalScrollOffset() > 0 }?.let(::startHolding)
			appBar.setExpanded(true, true)
		}
	}

	private fun startHolding(list: RecyclerView) {
		stopHolding()
		list.getLocationInWindow(location)
		heldListY = location[1]
		heldList = list
		// Added after the coordinator layout's own listener, so the list has already been moved when it runs.
		appBar.viewTreeObserver.addOnPreDrawListener(holdContent)
	}

	private fun stopHolding() {
		if (heldList != null) {
			heldList = null
			appBar.viewTreeObserver.removeOnPreDrawListener(holdContent)
		}
	}

	private companion object {

		/** How long the bar has to rest before it opens: longer than a frame of a fling. */
		const val SETTLE_DELAY_MS = 80L
	}
}
