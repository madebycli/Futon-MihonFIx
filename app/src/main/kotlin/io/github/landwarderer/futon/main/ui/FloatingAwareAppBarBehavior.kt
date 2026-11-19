package io.github.landwarderer.futon.main.ui

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

/**
 * The app bar behaviour of the main screen. While the top bar is docked it is the plain Material behaviour.
 *
 * While the top bar floats, the lists do not move with the app bar any more (they scroll behind it). The plain
 * behaviour would first take the whole scroll for hiding the search bar and only then let the list scroll, so the
 * list would stand still under the finger. Here the list always scrolls at once, and the app bar follows what the
 * list really scrolled, hiding and coming back by the same distance at the same time.
 */
class FloatingAwareAppBarBehavior : AppBarLayout.Behavior() {

	var isFloating: Boolean = false

	private val ignored = IntArray(2)

	override fun onNestedPreScroll(
		coordinatorLayout: CoordinatorLayout,
		child: AppBarLayout,
		target: View,
		dx: Int,
		dy: Int,
		consumed: IntArray,
		type: Int,
	) {
		if (!isFloating) {
			super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
		}
	}

	override fun onNestedScroll(
		coordinatorLayout: CoordinatorLayout,
		child: AppBarLayout,
		target: View,
		dxConsumed: Int,
		dyConsumed: Int,
		dxUnconsumed: Int,
		dyUnconsumed: Int,
		type: Int,
		consumed: IntArray,
	) {
		if (isFloating && dyConsumed != 0) {
			// Moves the app bar by what the list scrolled, through the same code that hides it normally, but nothing
			// is taken away from the list.
			ignored.fill(0)
			super.onNestedPreScroll(coordinatorLayout, child, target, 0, dyConsumed, ignored, type)
		}
		super.onNestedScroll(
			coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed,
		)
	}
}
