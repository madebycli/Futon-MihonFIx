package io.github.landwarderer.futon.main.ui

import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.allViews
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout

/**
 * The app bar behaviour of the main screen. With the docked top bar it is the plain Material behaviour, snap
 * included. With the floating top bar there is one rule instead of Material's snap (the two would fight): once the
 * scrolling has ended, a search bar that is partly shown slides fully open, so it never stays stuck halfway.
 *
 * "Ended" means after a fling too, not when the finger is lifted in the middle of one. The scrolling list may sit in
 * a wrapper (SwipeRefreshLayout in Feed), so it is looked up below the nested-scroll target; and as a wrapper does
 * not always pass the end of a fling on, the list itself is watched until it comes to rest.
 */
class SettlingAppBarBehavior : AppBarLayout.Behavior() {

	var isSettleEnabled: Boolean = false

	private var watchedList: RecyclerView? = null
	private var watchedAppBar: AppBarLayout? = null

	private val restListener = object : RecyclerView.OnScrollListener() {
		override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
			if (newState == RecyclerView.SCROLL_STATE_IDLE) {
				stopWatching()
				watchedAppBar?.let(::settle)
			}
		}
	}

	override fun onStartNestedScroll(
		parent: CoordinatorLayout,
		child: AppBarLayout,
		directTargetChild: View,
		target: View,
		nestedScrollAxes: Int,
		type: Int,
	): Boolean {
		if (type == ViewCompat.TYPE_TOUCH) stopWatching()
		return super.onStartNestedScroll(parent, child, directTargetChild, target, nestedScrollAxes, type)
	}

	override fun onStopNestedScroll(
		coordinatorLayout: CoordinatorLayout,
		abl: AppBarLayout,
		target: View,
		type: Int,
	) {
		if (!isSettleEnabled) {
			super.onStopNestedScroll(coordinatorLayout, abl, target, type)
			return
		}
		// Material's snap is left out on purpose: one rule only.
		val list = findList(target)
		if (list != null && list.scrollState == RecyclerView.SCROLL_STATE_SETTLING) {
			watch(list, abl)
			return
		}
		settle(abl)
	}

	private fun settle(abl: AppBarLayout) {
		val offset = topAndBottomOffset
		if (offset < 0 && offset > -abl.totalScrollRange) {
			abl.setExpanded(true, true)
		}
	}

	private fun findList(target: View): RecyclerView? = when (target) {
		is RecyclerView -> target
		is ViewGroup -> target.allViews.firstOrNull { it is RecyclerView && it.isShown } as? RecyclerView
		else -> null
	}

	private fun watch(list: RecyclerView, abl: AppBarLayout) {
		stopWatching()
		watchedList = list
		watchedAppBar = abl
		list.addOnScrollListener(restListener)
	}

	private fun stopWatching() {
		watchedList?.removeOnScrollListener(restListener)
		watchedList = null
	}
}
