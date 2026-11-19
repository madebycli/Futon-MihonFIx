package io.github.landwarderer.futon.main.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import io.github.landwarderer.futon.core.ui.widgets.SlidingBottomNavigationView

/** Keeps the round continue button level with the navigation bar when the bar slides away and back. */
class RoundResumeButtonBehavior : CoordinatorLayout.Behavior<View> {

	constructor() : super()
	constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

	override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
		return dependency is SlidingBottomNavigationView
	}

	override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
		if (child.translationY == dependency.translationY) return false
		child.translationY = dependency.translationY
		return true
	}
}
