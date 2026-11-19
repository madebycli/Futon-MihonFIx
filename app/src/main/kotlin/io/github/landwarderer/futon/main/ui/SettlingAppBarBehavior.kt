package io.github.landwarderer.futon.main.ui

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout

/**
 * The app bar behaviour of the main screen: the plain Material behaviour, plus, while the top bar floats, the settle
 * of Kototoro's top bar (Material 3 enterAlwaysScrollBehavior with its snap): once the scrolling has ended (after a
 * fling, not when the finger is lifted in the middle of one), a search bar that is partly shown slides fully open
 * instead of staying stuck halfway.
 */
class SettlingAppBarBehavior : AppBarLayout.Behavior() {

	var isSettleEnabled: Boolean = false

	override fun onStopNestedScroll(
		coordinatorLayout: CoordinatorLayout,
		abl: AppBarLayout,
		target: View,
		type: Int,
	) {
		super.onStopNestedScroll(coordinatorLayout, abl, target, type)
		if (!isSettleEnabled) return
		val isFlingFollowing = type == ViewCompat.TYPE_TOUCH &&
			(target as? RecyclerView)?.scrollState == RecyclerView.SCROLL_STATE_SETTLING
		if (isFlingFollowing) return
		val offset = topAndBottomOffset
		if (offset < 0 && offset > -abl.totalScrollRange) {
			abl.setExpanded(true, true)
		}
	}
}
