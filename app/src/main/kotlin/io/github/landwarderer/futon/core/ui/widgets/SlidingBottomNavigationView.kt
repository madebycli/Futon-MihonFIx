package io.github.landwarderer.futon.core.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewPropertyAnimator
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.customview.view.AbsSavedState
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.navigation.NavigationBarView
import io.github.landwarderer.futon.core.util.ext.applySystemAnimatorScale
import io.github.landwarderer.futon.core.util.ext.measureHeight
import kotlin.math.max
import com.google.android.material.R as materialR

private const val STATE_DOWN = 1
private const val STATE_UP = 2

private const val SLIDE_UP_ANIMATION_DURATION = 225L
private const val SLIDE_DOWN_ANIMATION_DURATION = 175L

private const val MAX_ITEM_COUNT = 6

class SlidingBottomNavigationView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	@AttrRes defStyleAttr: Int = materialR.attr.bottomNavigationStyle,
	@StyleRes defStyleRes: Int = materialR.style.Widget_Design_BottomNavigationView,
) : NavigationBarView(context, attrs, defStyleAttr, defStyleRes),
	CoordinatorLayout.AttachedBehavior {

	private var currentAnimator: ViewPropertyAnimator? = null

	private var currentState = STATE_UP
	private var behavior = HideBottomNavigationOnScrollBehavior()

	var isPinned: Boolean
		get() = behavior.isPinned
		set(value) {
			behavior.isPinned = value
			if (value && currentState == STATE_UP) {
				currentAnimator?.cancel()
				translationY = 0f
			}
		}

	/** How far the bar slides to leave the screen: its height plus the gap below it when it floats. */
	val slideRange: Int
		get() = measureHeight() + ((layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0)

	val isShownOrShowing: Boolean
		get() = isVisible && currentState == STATE_UP

	// Read back, as the constructor of the super class already set the minimum height from the style.
	private var dockedMinimumHeight: Int = minimumHeight

	/**
	 * The floating bar that is shown instead of the docked one, or null for the docked bar. The docked menu view
	 * stays the source of the sections and the selection; it is only hidden while the floating bar is shown.
	 */
	var floatingRow: FloatingNavRow? = null
		set(value) {
			if (field === value) return
			field?.let { removeView(it) }
			field = value
			if (value != null) {
				(value.parent as? android.view.ViewGroup)?.removeView(value)
				addView(value, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
			}
			getChildAt(0)?.isVisible = value == null
			applyMinimumHeight()
		}

	/**
	 * A height chosen in the settings for the docked bar, or null to keep the height that follows the labels. It is a
	 * minimum: the bar never gets lower than its icons and labels need.
	 */
	var dockedHeightOverride: Int? = null
		set(value) {
			if (field != value) {
				field = value
				applyMinimumHeight()
				requestLayout()
			}
		}

	/** Called when the selection, the visible sections or a badge changed, to redraw the floating bar. */
	var onMenuStateChanged: (() -> Unit)? = null

	fun notifyMenuStateChanged() {
		onMenuStateChanged?.invoke()
	}

	override fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
		super.setOnItemSelectedListener(
			listener?.let { l ->
				OnItemSelectedListener { item ->
					// The item becomes checked after the listener returned, so the floating bar reads it afterwards.
					l.onNavigationItemSelected(item).also { post { notifyMenuStateChanged() } }
				}
			},
		)
	}

	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		val row = floatingRow
		// The floating bar is narrower than this view: touches beside and above it belong to the content.
		if (row != null && ev.actionMasked == MotionEvent.ACTION_DOWN && !row.isTouchOnContent(ev.x, ev.y)) {
			return false
		}
		return super.dispatchTouchEvent(ev)
	}

	override fun getBehavior(): CoordinatorLayout.Behavior<*> {
		return behavior
	}

	/** From BottomNavigationView **/

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		super.onTouchEvent(event)
		// Consume all events to avoid views under the BottomNavigationView from receiving touch events.
		return true
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val minHeightSpec = makeMinHeightSpec(heightMeasureSpec)
		super.onMeasure(widthMeasureSpec, minHeightSpec)
		if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
			setMeasuredDimension(
				measuredWidth,
				max(
					measuredHeight,
					suggestedMinimumHeight + paddingTop + paddingBottom,
				),
			)
		}
	}

	private fun makeMinHeightSpec(measureSpec: Int): Int {
		var minHeight = suggestedMinimumHeight
		if (MeasureSpec.getMode(measureSpec) != MeasureSpec.EXACTLY && minHeight > 0) {
			minHeight += paddingTop + paddingBottom

			return MeasureSpec.makeMeasureSpec(
				max(MeasureSpec.getSize(measureSpec), minHeight), MeasureSpec.AT_MOST,
			)
		}

		return measureSpec
	}

	override fun getMaxItemCount(): Int = MAX_ITEM_COUNT

	@SuppressLint("RestrictedApi")
	override fun createNavigationBarMenuView(context: Context) = BottomNavigationMenuView(context)

	/** End **/

	override fun onSaveInstanceState(): Parcelable {
		val superState = super.onSaveInstanceState()
		return SavedState(superState, currentState, translationY)
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state is SavedState) {
			super.onRestoreInstanceState(state.superState)
			super.setTranslationY(if (isPinned && state.currentState == STATE_UP) 0f else state.translationY)
			currentState = state.currentState
		} else {
			super.onRestoreInstanceState(state)
		}
	}

	override fun setTranslationY(translationY: Float) {
		// Disallow translation change when state down
		if (currentState != STATE_DOWN) {
			super.setTranslationY(translationY)
		}
	}

	override fun setMinimumHeight(minHeight: Int) {
		dockedMinimumHeight = minHeight
		applyMinimumHeight()
	}

	/** The minimum height is that of the docked bar; the floating bar has its own height. */
	private fun applyMinimumHeight() {
		val docked = dockedHeightOverride ?: dockedMinimumHeight
		super.setMinimumHeight(if (floatingRow == null) docked else 0)
		// The chosen height is a minimum: when the icons and labels need more room the bar grows instead of cutting
		// them off, so a low setting never pushes an icon out of the bar.
		getChildAt(0)?.minimumHeight = docked
	}

	fun show() {
		if (currentState == STATE_UP) {
			return
		}
		currentAnimator?.cancel()
		clearAnimation()

		currentState = STATE_UP
		animateTranslation(
			0F,
			SLIDE_UP_ANIMATION_DURATION,
			LinearOutSlowInInterpolator(),
		)
	}

	fun hide() {
		if (currentState == STATE_DOWN) {
			return
		}
		currentAnimator?.cancel()
		clearAnimation()

		currentState = STATE_DOWN
		val target = slideRange
		if (target == 0) {
			return
		}
		animateTranslation(
			target.toFloat(),
			SLIDE_DOWN_ANIMATION_DURATION,
			FastOutLinearInInterpolator(),
		)
	}

	/**
	 * Brings the bar fully back when scrolling left it partly hidden, as Kototoro resets its bottom bar when another
	 * section is opened. Does nothing while the bar is hidden on purpose (action mode).
	 */
	fun revealIfScrolledAway() {
		if (currentState == STATE_UP && translationY != 0F) {
			currentAnimator?.cancel()
			animateTranslation(0F, SLIDE_UP_ANIMATION_DURATION, LinearOutSlowInInterpolator())
		}
	}

	fun showOrHide(show: Boolean) {
		if (show) {
			show()
		} else {
			hide()
		}
	}

	private fun animateTranslation(targetY: Float, duration: Long, interpolator: TimeInterpolator) {
		currentAnimator = animate()
			.translationY(targetY)
			.setInterpolator(interpolator)
			.setDuration(duration)
			.applySystemAnimatorScale(context)
			.setListener(
				object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						currentAnimator = null
						postInvalidate()
					}
				},
			)
	}

	internal class SavedState : AbsSavedState {

		var currentState = STATE_UP
		var translationY = 0F

		constructor(superState: Parcelable, currentState: Int, translationY: Float) : super(superState) {
			this.currentState = currentState
			this.translationY = translationY
		}

		constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
			currentState = source.readInt()
			translationY = source.readFloat()
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeInt(currentState)
			out.writeFloat(translationY)
		}

		companion object {

			@Suppress("unused")
			@JvmField
			val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
				override fun createFromParcel(`in`: Parcel) = SavedState(`in`, SavedState::class.java.classLoader)

				override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
			}
		}
	}
}
