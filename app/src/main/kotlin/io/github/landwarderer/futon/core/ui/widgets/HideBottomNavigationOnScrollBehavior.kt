package io.github.landwarderer.futon.core.ui.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.navigation.NavigationBarView
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.getAnimationDuration
import io.github.landwarderer.futon.core.util.ext.measureHeight

class HideBottomNavigationOnScrollBehavior @JvmOverloads constructor(
	context: Context? = null,
	attrs: AttributeSet? = null,
) : CoordinatorLayout.Behavior<NavigationBarView>(context, attrs) {

	@ViewCompat.NestedScrollType
	private var lastStartedType: Int = 0

	private var offsetAnimator: ValueAnimator? = null

	private var dyRatio = 1F

	var isPinned: Boolean = false
		set(value) {
			field = value
			if (value) {
				offsetAnimator?.cancel()
				offsetAnimator = null
			}
		}

	override fun layoutDependsOn(parent: CoordinatorLayout, child: NavigationBarView, dependency: View): Boolean {
		return dependency is AppBarLayout
	}

	override fun onDependentViewChanged(
		parent: CoordinatorLayout,
		child: NavigationBarView,
		dependency: View,
	): Boolean {
		val appBarSize = dependency.measureHeight()
		dyRatio = if (appBarSize > 0) {
			child.measureHeight().toFloat() / appBarSize
		} else {
			1F
		}
		return false
	}

	override fun onStartNestedScroll(
		coordinatorLayout: CoordinatorLayout,
		child: NavigationBarView,
		directTargetChild: View,
		target: View,
		axes: Int,
		type: Int,
	): Boolean {
		if (isPinned || (axes and ViewCompat.SCROLL_AXIS_VERTICAL) == 0) {
			return false
		}
		lastStartedType = type
		offsetAnimator?.cancel()
		return true
	}


	/**
	 * The bar follows what the content really scrolled, not every drag: a list that cannot scroll, or a pull to
	 * refresh at the top of a list, no longer moves the bar and leaves it hanging halfway. Pulling down at the top
	 * brings it back.
	 */
	override fun onNestedScroll(
		coordinatorLayout: CoordinatorLayout,
		child: NavigationBarView,
		target: View,
		dxConsumed: Int,
		dyConsumed: Int,
		dxUnconsumed: Int,
		dyUnconsumed: Int,
		type: Int,
		consumed: IntArray,
	) {
		// This overlay observes the list. Leave the unconsumed distance available to its parents.
		if (isPinned || !target.isShown) return
		val dy = dyConsumed + dyUnconsumed.coerceAtMost(0)
		if (dy != 0) {
			// The floating bar moves with the content one to one, as Kototoro's bottom bar; the docked bar keeps
			// its old pace, scaled to the height of the search bar.
			val ratio = if (child.isFloatingBar()) 1F else dyRatio
			child.translationY = (child.translationY + (dy * ratio)).coerceIn(0F, slideRangeOf(child))
		}
	}

	private fun NavigationBarView.isFloatingBar() = (this as? SlidingBottomNavigationView)?.floatingRow != null

	override fun onStopNestedScroll(
		coordinatorLayout: CoordinatorLayout,
		child: NavigationBarView,
		target: View,
		type: Int,
	) {
		// The floating bar stays where the scrolling left it, as in Kototoro; the docked bar snaps open or closed.
		if (child.isFloatingBar()) return
		if (!isPinned && (lastStartedType == ViewCompat.TYPE_TOUCH || type == ViewCompat.TYPE_NON_TOUCH)) {
			animateBottomNavigationVisibility(child, child.translationY < slideRangeOf(child) / 2)
		}
	}

	private fun animateBottomNavigationVisibility(child: NavigationBarView, isVisible: Boolean) {
		offsetAnimator?.cancel()
		offsetAnimator = ValueAnimator().apply {
			interpolator = DecelerateInterpolator()
			duration = child.context.getAnimationDuration(R.integer.config_shorterAnimTime)
			addUpdateListener {
				child.translationY = it.animatedValue as Float
			}
		}
		offsetAnimator?.setFloatValues(
			child.translationY,
			if (isVisible) 0F else slideRangeOf(child),
		)
		offsetAnimator?.start()
	}

	private fun slideRangeOf(child: NavigationBarView): Float =
		((child as? SlidingBottomNavigationView)?.slideRange ?: child.height).toFloat()
}
