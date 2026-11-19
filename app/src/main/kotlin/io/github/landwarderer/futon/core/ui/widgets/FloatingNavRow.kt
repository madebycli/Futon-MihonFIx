// The look and the measurements follow Kototoro's floating bottom navigation
// (https://github.com/Kototoro-app/Kototoro, Apache License 2.0):
//   app/src/main/kotlin/org/skepsun/kototoro/core/ui/widgets/KototoroBottomNav.kt
//     KototoroBottomNav (floating branch), FloatingBottomNavRow, FullWidthFloatingBottomNavRow,
//     resolveNavBarHeight, resolveBottomNavInsetPillHeight, resolveBottomNavFullWidthPillHeight
//   app/src/main/kotlin/org/skepsun/kototoro/core/ui/glass/GlassSurface.kt (the Material fallback of GlassSurface:
//     surfaceContainer, a 1 dp outlineVariant hairline at 10 %, the 4 dp navigation shadow)
// Kototoro draws this with Jetpack Compose. Clio's main screen is built with Views, so the composables were rebuilt
// as Views with the same numbers; the liquid-glass effects of Kototoro's iOS style are not part of the Material look
// and were left out.
package io.github.landwarderer.futon.core.ui.widgets

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.github.landwarderer.futon.core.prefs.NavLabelMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.google.android.material.R as materialR

/**
 * The floating navigation bar: a compact rounded bar with the sections, and an optional view beside it (the round
 * continue button). It only draws and reports clicks; which sections exist and which one is selected comes from
 * the [com.google.android.material.navigation.NavigationBarView] that hosts it.
 */
@SuppressLint("ViewConstructor")
class FloatingNavRow(context: Context) : ViewGroup(context) {

	/** One section of the bar. [badge] is null for none, 0 for a dot and a positive count for a number. */
	data class Entry(
		@IdRes val id: Int,
		val title: CharSequence,
		@DrawableRes val icon: Int,
		val badge: Int?,
	)

	/** How the bar looks; the lengths are in dp. */
	data class Style(
		val labelMode: NavLabelMode,
		val isCapsuleEnabled: Boolean,
		val isFullWidth: Boolean,
		val contentHeight: Int,
		val cornerRadius: Int,
		/** Opacity of the bar's background, 0..1; the content shows through below 1. Not in Kototoro's Material look. */
		val opacity: Float = 1f,
		/** The selected section in the accent color (primary) instead of onSecondaryContainer. */
		val isAccentColored: Boolean = false,
	) {

		/** The height of the bar: the content height plus the 4 dp inset, as resolveNavBarHeight in Kototoro. */
		val barHeight: Int
			get() = contentHeight + 4
	}

	var onItemClick: ((Int) -> Unit)? = null

	private val density = resources.displayMetrics.density
	private var adjacentView: View? = null
	private var entries: List<Entry> = emptyList()
	private var selectedId: Int = View.NO_ID
	private var style = Style(NavLabelMode.SELECTED, true, false, 52, 28)
	private var spec = FloatingNavLayoutSpec.resolve(Float.MAX_VALUE, 1, null, showLabels = true)

	// Created after the fields above: the bar reads the style while it is built.
	private val bar = BarView(context)

	init {
		clipChildren = false
		clipToPadding = false
		addView(bar)
	}

	/** The height of the bar and of the view beside it, in pixels. */
	val barHeightPx: Int
		get() = dp(style.barHeight)

	fun setStyle(value: Style) {
		if (value == style) return
		style = value
		bar.applyShape()
		bar.rebuild()
		adjacentView?.let { updateAdjacentSize(it) }
		requestLayout()
	}

	fun setEntries(value: List<Entry>, selected: Int) {
		val isSameItems = value.map { it.id } == entries.map { it.id }
		if (isSameItems && value == entries && selected == selectedId) return
		val isSelectionChange = isSameItems && selected != selectedId && isLaidOut
		entries = value
		if (isSelectionChange) {
			// The selection moves: the items change their size and the capsule slides to the new item.
			// One short step: the label fades in while the icon lifts and the capsule slides (AutoTransition would fade
			// out, move and fade in one after the other).
			TransitionManager.beginDelayedTransition(
				bar,
				TransitionSet()
					.setOrdering(TransitionSet.ORDERING_TOGETHER)
					.addTransition(ChangeBounds())
					.addTransition(Fade())
					.setDuration(SELECTION_ANIMATION_MS)
					.setInterpolator(FastOutSlowInInterpolator()),
			)
		}
		selectedId = selected
		if (isSameItems) bar.bind() else bar.rebuild()
		requestLayout()
	}

	/** Puts [view] beside the bar, as Kototoro does with the continue button, or removes it with null. */
	fun setAdjacentView(view: View?) {
		if (adjacentView === view) return
		adjacentView?.let { removeView(it) }
		adjacentView = view
		if (view != null) {
			(view.parent as? ViewGroup)?.removeView(view)
			addView(view)
			updateAdjacentSize(view)
		}
		requestLayout()
	}

	private fun updateAdjacentSize(view: View) {
		val size = barHeightPx
		view.layoutParams = LayoutParams(size, size)
	}

	override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val width = MeasureSpec.getSize(widthMeasureSpec)
		val available = (width - paddingLeft - paddingRight).coerceAtLeast(0)
		val barHeight = barHeightPx
		val adjacent = adjacentView?.takeIf { it.isVisible }
		val newSpec = FloatingNavLayoutSpec.resolve(
			availableWidth = available / density,
			itemCount = entries.size,
			fabWidth = if (adjacent != null) style.barHeight.toFloat() else null,
			showLabels = style.labelMode != NavLabelMode.NONE,
			isExpressivePill = style.labelMode == NavLabelMode.SELECTED_BESIDE,
		)
		if (newSpec != spec) {
			spec = newSpec
			bar.bind()
		}
		val adjacentSpace = if (adjacent != null) barHeight + dp(spec.fabGap) else 0
		val maxBarWidth = (available - dp(spec.outerHorizontalPadding) * 2 - adjacentSpace).coerceAtLeast(0)
		val barHeightSpec = MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
		if (style.isFullWidth) {
			bar.measure(MeasureSpec.makeMeasureSpec(maxBarWidth, MeasureSpec.EXACTLY), barHeightSpec)
		} else {
			bar.measure(MeasureSpec.makeMeasureSpec(maxBarWidth, MeasureSpec.AT_MOST), barHeightSpec)
		}
		adjacent?.measure(barHeightSpec, barHeightSpec)
		setMeasuredDimension(width, barHeight + paddingTop + paddingBottom)
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		val adjacent = adjacentView?.takeIf { it.isVisible }
		val gap = if (adjacent != null) dp(spec.fabGap) else 0
		val total = bar.measuredWidth + gap + (adjacent?.measuredWidth ?: 0)
		val available = r - l - paddingLeft - paddingRight
		// Kototoro centres the wrapping bar together with the button; the full-width bar starts at the margin.
		val start = paddingLeft + if (style.isFullWidth) dp(spec.outerHorizontalPadding) else (available - total) / 2
		val top = paddingTop
		val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
		if (isRtl) {
			val end = r - l - start
			bar.layout(end - bar.measuredWidth, top, end, top + bar.measuredHeight)
			adjacent?.layout(
				end - total, top, end - total + adjacent.measuredWidth, top + adjacent.measuredHeight,
			)
		} else {
			bar.layout(start, top, start + bar.measuredWidth, top + bar.measuredHeight)
			adjacent?.let {
				val x = start + bar.measuredWidth + gap
				it.layout(x, top, x + it.measuredWidth, top + it.measuredHeight)
			}
		}
	}

	/** Only the bar and the view beside it take touches; the transparent rest lets them through to the content. */
	fun isTouchOnContent(x: Float, y: Float): Boolean {
		val adjacent = adjacentView?.takeIf { it.isVisible }
		return bar.containsPoint(x - left, y - top) || adjacent?.containsPoint(x - left, y - top) == true
	}

	private fun View.containsPoint(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom

	private fun dp(value: Float): Int = (value * density).roundToInt()

	private fun dp(value: Int): Int = (value * density).roundToInt()

	private fun color(attr: Int) = MaterialColors.getColor(this, attr)

	/** The rounded bar with the sections and the capsule of the selected one. */
	private inner class BarView(context: Context) : ViewGroup(context) {

		private val items = ArrayList<ItemView>()
		private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val pillRect = RectF()
		private var pillAnimator: ValueAnimator? = null
		private var hasPill = false
		private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
		private var downX = 0f
		private var isDragging = false
		private var dragItem: ItemView? = null

		init {
			setWillNotDraw(false)
			clipChildren = false
			applyShape()
		}

		fun applyShape() {
			val radius = if (style.isFullWidth) {
				// Kototoro's full-width bar is a capsule.
				dp(style.barHeight) / 2f
			} else {
				dp(style.cornerRadius).toFloat()
			}
			background = MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(radius).build()).apply {
				fillColor = ColorStateList.valueOf(
					ColorUtils.setAlphaComponent(
						color(materialR.attr.colorSurfaceContainer),
						(style.opacity.coerceIn(0f, 1f) * 255).roundToInt(),
					),
				)
				setStroke(
					density,
					ColorUtils.setAlphaComponent(color(materialR.attr.colorOutlineVariant), (0.10f * 255).roundToInt()),
				)
			}
			// resolveFallbackShadowElevation in Kototoro: a see-through surface gets no shadow, it would shine through.
			elevation = if (style.opacity < 1f) 0f else dp(NAV_SHADOW_ELEVATION_DP).toFloat()
			pillPaint.color = if (style.isAccentColored) {
				ColorUtils.setAlphaComponent(color(androidx.appcompat.R.attr.colorPrimary), (0.14f * 255).roundToInt())
			} else {
				ColorUtils.setAlphaComponent(color(materialR.attr.colorOnSurface), (0.10f * 255).roundToInt())
			}
		}

		fun rebuild() {
			removeAllViews()
			items.clear()
			for (entry in entries) {
				val item = ItemView(context)
				items += item
				addView(item)
			}
			bind()
		}

		fun bind() {
			for ((index, entry) in entries.withIndex()) {
				items.getOrNull(index)?.bind(entry, entry.id == selectedId)
			}
		}

		/** The shared capsule: Kototoro draws it behind the selected item, except with the label beside the icon. */
		private val usesSharedPill: Boolean
			get() = style.isCapsuleEnabled && (style.labelMode != NavLabelMode.SELECTED_BESIDE || style.isFullWidth)

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val height = MeasureSpec.getSize(heightMeasureSpec)
			val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
			if (style.isFullWidth) {
				// The tabs share the width evenly, inside a 4 dp inset on every side.
				val inset = dp(4)
				val count = items.size.coerceAtLeast(1)
				val itemWidth = ((maxWidth - inset * 2) / count).coerceAtLeast(0)
				val itemHeightSpec = MeasureSpec.makeMeasureSpec((height - inset * 2).coerceAtLeast(0), MeasureSpec.EXACTLY)
				for (item in items) {
					item.measure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), itemHeightSpec)
				}
				setMeasuredDimension(maxWidth, height)
				return
			}
			val itemHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
			val gaps = dp(spec.itemSpacing) * (items.size - 1).coerceAtLeast(0) + dp(spec.horizontalPadding) * 2
			if (style.labelMode == NavLabelMode.SELECTED_BESIDE) {
				// The selected item is wider than the others, but the bar keeps the width it needs for the widest
				// selection, so it does not grow or shrink when the selection moves; the items are centred in it.
				var total = 0
				for (item in items) {
					item.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), itemHeightSpec)
					total += item.measuredWidth
				}
				val minWidth = dp(FloatingNavLayoutSpec.ITEM_TOUCH_TARGET)
				val widest = items.maxOfOrNull { it.besideSelectedWidth() } ?: 0
				val fixed = minWidth * (items.size - 1).coerceAtLeast(0) + max(widest, minWidth) + gaps
				contentWidth = total + gaps
				setMeasuredDimension(min(max(fixed, contentWidth), maxWidth), height)
				return
			}
			// Every item gets the same slot, as wide as the widest item with its label shown: the bar stays the
			// same size whichever item is selected, and items do not shift when a label appears.
			val fitting = ((maxWidth - gaps) / items.size.coerceAtLeast(1)).coerceAtLeast(0)
			val slot = min(items.maxOfOrNull { it.belowSelectedWidth() } ?: 0, fitting)
			val slotSpec = MeasureSpec.makeMeasureSpec(slot, MeasureSpec.EXACTLY)
			for (item in items) {
				item.measure(slotSpec, itemHeightSpec)
			}
			contentWidth = slot * items.size + gaps
			setMeasuredDimension(min(contentWidth, maxWidth), height)
		}

		/** Width the items and gaps need; the bar may be wider (see onMeasure), then they are centred in it. */
		private var contentWidth = 0

		override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
			val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
			val inset = if (style.isFullWidth) dp(4) else 0
			val spacing = if (style.isFullWidth) 0 else dp(spec.itemSpacing)
			var x = if (style.isFullWidth) {
				inset
			} else {
				dp(spec.horizontalPadding) + ((r - l - contentWidth) / 2).coerceAtLeast(0)
			}
			val ordered = if (isRtl) items.asReversed() else items
			for (item in ordered) {
				val top = (b - t - item.measuredHeight) / 2
				item.layout(x, top, x + item.measuredWidth, top + item.measuredHeight)
				x += item.measuredWidth + spacing
			}
			updatePill(animate = hasPill)
		}

		private fun selectedItem(): ItemView? = dragItem ?: items.getOrNull(entries.indexOfFirst { it.id == selectedId })

		/** Moves the capsule to the selected item, sliding when it was shown before. */
		private fun updatePill(animate: Boolean) {
			val item = selectedItem()
			if (!usesSharedPill || item == null) {
				pillAnimator?.cancel()
				hasPill = false
				invalidate()
				return
			}
			val target = pillBounds(item)
			if (!animate || isDragging) {
				pillAnimator?.cancel()
				pillRect.set(target)
				hasPill = true
				invalidate()
				return
			}
			if (target == pillRect || (pillAnimator?.isRunning == true && target == pillTarget)) return
			pillAnimator?.cancel()
			val from = RectF(pillRect)
			pillTarget.set(target)
			pillAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
				duration = SELECTION_ANIMATION_MS
				interpolator = FastOutSlowInInterpolator()
				addUpdateListener {
					val f = it.animatedValue as Float
					pillRect.set(
						from.left + (target.left - from.left) * f,
						from.top + (target.top - from.top) * f,
						from.right + (target.right - from.right) * f,
						from.bottom + (target.bottom - from.bottom) * f,
					)
					invalidate()
				}
				start()
			}
		}

		private val pillTarget = RectF()

		private fun pillBounds(item: ItemView): RectF {
			val height = if (style.isFullWidth) {
				// resolveBottomNavFullWidthPillHeight: the whole tab, up to 56 dp.
				min(item.height, dp(56))
			} else {
				// resolveBottomNavInsetPillHeight: the item less 4 dp above and below, up to 56 dp.
				(item.height - dp(4) * 2).coerceIn(0, dp(56))
			}
			val top = item.top + (item.height - height) / 2f
			return RectF(item.left.toFloat(), top, item.right.toFloat(), top + height)
		}

		override fun dispatchDraw(canvas: Canvas) {
			if (hasPill && usesSharedPill) {
				val radius = pillRect.height() / 2f
				canvas.drawRoundRect(pillRect, radius, radius, pillPaint)
			}
			super.dispatchDraw(canvas)
		}

		override fun onDetachedFromWindow() {
			pillAnimator?.cancel()
			super.onDetachedFromWindow()
		}

		// Dragging along the bar moves the capsule with the finger and selects the item it is let go on, as in
		// Kototoro (FloatingBottomNavRow, detectDragGestures).

		override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
			if (!usesSharedPill) return false
			when (ev.actionMasked) {
				MotionEvent.ACTION_DOWN -> {
					downX = ev.x
					isDragging = false
				}

				MotionEvent.ACTION_MOVE -> if (!isDragging && abs(ev.x - downX) > touchSlop) {
					isDragging = true
					parent?.requestDisallowInterceptTouchEvent(true)
					return true
				}
			}
			return false
		}

		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (!isDragging) return event.actionMasked == MotionEvent.ACTION_DOWN && usesSharedPill
			when (event.actionMasked) {
				MotionEvent.ACTION_MOVE -> {
					val item = itemAt(event.x)
					val previous = selectedItem()
					val width = (previous?.width ?: 0).toFloat()
					val center = event.x.coerceIn(width / 2f, this.width - width / 2f)
					pillRect.offsetTo(center - width / 2f, pillRect.top)
					if (item != null && item !== dragItem) {
						dragItem = item
						item.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
					}
					invalidate()
				}

				MotionEvent.ACTION_UP -> {
					val target = dragItem
					isDragging = false
					dragItem = null
					val index = items.indexOf(target)
					if (index >= 0) {
						onItemClick?.invoke(entries[index].id)
					}
					updatePill(animate = true)
				}

				MotionEvent.ACTION_CANCEL -> {
					isDragging = false
					dragItem = null
					updatePill(animate = true)
				}
			}
			return true
		}

		private fun itemAt(x: Float) = items.firstOrNull { x >= it.left && x < it.right }
	}

	/** One section: the icon, and the label when it is shown. */
	private inner class ItemView(context: Context) : FrameLayout(context) {

		private val content = LinearLayout(context)
		private val icon = CheckableImageView(context)
		private val label = TextView(context)
		private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private var entry: Entry? = null
		private var isItemSelected = false
		@DrawableRes
		private var iconRes = 0

		init {
			clipChildren = false
			setWillNotDraw(false)
			content.gravity = Gravity.CENTER
			content.clipChildren = false
			addView(content, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
			content.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
			label.maxLines = 1
			label.ellipsize = TextUtils.TruncateAt.END
			label.includeFontPadding = false
			label.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
			icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
			content.addView(label, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
			isClickable = true
			isFocusable = true
			setOnClickListener { entry?.let { onItemClick?.invoke(it.id) } }
			badgePaint.color = color(androidx.appcompat.R.attr.colorError)
			badgeTextPaint.color = color(materialR.attr.colorOnError)
			badgeTextPaint.textAlign = Paint.Align.CENTER
			badgeTextPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
		}

		fun bind(entry: Entry, isSelected: Boolean) {
			val isFirstBind = this.entry == null || iconRes != entry.icon
			this.entry = entry
			if (iconRes != entry.icon) {
				iconRes = entry.icon
				icon.setImageDrawable(AppCompatResources.getDrawable(context, entry.icon)?.mutate())
			}
			isItemSelected = isSelected
			icon.isChecked = isSelected
			if (isFirstBind) {
				// The icon takes its first state without the animation, which only plays when the selection changes.
				icon.jumpDrawablesToCurrentState()
			}
			val selectedColor = if (style.isAccentColored) {
				color(androidx.appcompat.R.attr.colorPrimary)
			} else {
				color(materialR.attr.colorOnSecondaryContainer)
			}
			val contentColor = if (isSelected) selectedColor else color(materialR.attr.colorOnSurfaceVariant)
			ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(contentColor))
			label.setTextColor(contentColor)
			label.text = entry.title
			contentDescription = entry.title
			this.isSelected = isSelected
			val mode = style.labelMode
			val isBeside = mode == NavLabelMode.SELECTED_BESIDE
			val showLabel = spec.showLabels && when (mode) {
				NavLabelMode.NONE -> false
				NavLabelMode.ALL -> true
				NavLabelMode.SELECTED, NavLabelMode.SELECTED_BESIDE -> isSelected
			}
			label.isVisible = showLabel
			content.orientation = if (isBeside) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
			val labelParams = label.layoutParams as LinearLayout.LayoutParams
			if (style.isFullWidth) {
				bindFullWidth(isBeside, labelParams)
			} else if (isBeside) {
				bindBeside(isSelected, labelParams)
			} else {
				bindBelow(isSelected, labelParams)
			}
			label.layoutParams = labelParams
			invalidate()
		}

		/** Column of FloatingBottomNavRow: icon over label, the icon lifted by 3 dp while selected. */
		private fun bindBelow(isSelected: Boolean, labelParams: LinearLayout.LayoutParams) {
			TextViewCompat.setTextAppearance(label, materialR.style.TextAppearance_Material3_LabelSmall)
			label.setTextColor(icon.imageTintList)
			label.maxWidth = Int.MAX_VALUE
			labelParams.marginStart = 0
			labelParams.topMargin = 0
			content.background = null
			content.setPadding(0, 0, 0, 0)
			val horizontal = if (isSelected && style.isCapsuleEnabled) dp(8) else dp(1)
			// No fixed vertical padding: the content is centred, and onLayout makes sure it fits (see liftIcon).
			setPadding(horizontal, 0, horizontal, 0)
			minimumWidth = dp(FloatingNavLayoutSpec.ITEM_TOUCH_TARGET)
			content.minimumHeight = 0
			content.minimumWidth = 0
			(content.layoutParams as LayoutParams).height = LayoutParams.WRAP_CONTENT
			wantsLift = isSelected
			requestLayout()
		}

		/** The selected icon is lifted a little (3 dp in Kototoro), but only as far as the bar has room for it. */
		private var wantsLift = false
		private var appliedLift = Float.NaN

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			super.onLayout(changed, left, top, right, bottom)
			val isBelow = !style.isFullWidth && style.labelMode != NavLabelMode.SELECTED_BESIDE
			val lift = if (isBelow && wantsLift) {
				// Room above the content inside the item, keeping a 2 dp margin to the edge of the bar.
				val room = (content.top - dp(2)).coerceAtLeast(0)
				-min(dp(3), room).toFloat()
			} else {
				0f
			}
			if (lift != appliedLift) {
				val isFirst = appliedLift.isNaN()
				appliedLift = lift
				if (isFirst) {
					icon.translationY = lift
				} else {
					icon.animate()
						.translationY(lift)
						.setDuration(SELECTION_ANIMATION_MS)
						.setInterpolator(FastOutSlowInInterpolator())
						.start()
				}
			}
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			// Should the content not fit the bar (a very low bar with a large font), the label goes first: the icon
			// stays whole and inside the bar instead of being pushed out or cut.
			if (label.isVisible && content.measuredHeight > measuredHeight - paddingTop - paddingBottom) {
				label.isVisible = false
				super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			}
		}

		/** The expressive pill: icon and the label beside it in a secondaryContainer capsule, 40 dp high. */
		private fun bindBeside(isSelected: Boolean, labelParams: LinearLayout.LayoutParams) {
			TextViewCompat.setTextAppearance(label, materialR.style.TextAppearance_Material3_LabelMedium)
			label.setTextColor(icon.imageTintList)
			label.setTextSize(TypedValue.COMPLEX_UNIT_PX, label.textSize * spec.labelScale)
			label.maxWidth = spec.labelMaxWidth?.let { dp(it) } ?: Int.MAX_VALUE
			labelParams.marginStart = if (entry?.badge?.let { it > 0 } == true) dp(20) else dp(8)
			labelParams.topMargin = 0
			wantsLift = false
			setPadding(0, dp(4), 0, dp(4))
			minimumWidth = dp(FloatingNavLayoutSpec.ITEM_TOUCH_TARGET)
			content.minimumHeight = dp(40)
			content.minimumWidth = dp(40)
			content.setPadding(if (isSelected) dp(8) else 0, 0, if (isSelected) dp(8) else 0, 0)
			content.background = if (isSelected && style.isCapsuleEnabled) {
				GradientDrawable().apply {
					cornerRadius = dp(20).toFloat()
					setColor(
						color(
							if (style.isAccentColored) {
								materialR.attr.colorPrimaryContainer
							} else {
								materialR.attr.colorSecondaryContainer
							},
						),
					)
				}
			} else {
				null
			}
			(content.layoutParams as LayoutParams).height = dp(40)
		}

		private fun labelTextWidth(): Int {
			val text = entry?.title ?: return 0
			return ceil(label.paint.measureText(text, 0, text.length)).toInt()
		}

		/** Width of this item when it is selected with the label below the icon, at least the touch target. */
		fun belowSelectedWidth(): Int {
			val minWidth = dp(FloatingNavLayoutSpec.ITEM_TOUCH_TARGET)
			if (style.labelMode == NavLabelMode.NONE || !spec.showLabels) return minWidth
			val padding = if (style.isCapsuleEnabled) dp(8) else dp(1)
			return max(minWidth, max(labelTextWidth(), dp(24)) + padding * 2)
		}

		/** Width of this item when it is selected with the label beside the icon. */
		fun besideSelectedWidth(): Int {
			if (!spec.showLabels) return dp(FloatingNavLayoutSpec.ITEM_TOUCH_TARGET)
			val labelWidth = min(labelTextWidth(), spec.labelMaxWidth?.let { dp(it) } ?: Int.MAX_VALUE)
			val margin = if (entry?.badge?.let { it > 0 } == true) dp(20) else dp(8)
			return max(dp(FloatingNavLayoutSpec.ITEM_TOUCH_TARGET), dp(8) + dp(24) + margin + labelWidth + dp(8))
		}

		/** A tab of FullWidthFloatingBottomNavRow: icon and label 2 dp apart, or 6 dp apart side by side. */
		private fun bindFullWidth(isBeside: Boolean, labelParams: LinearLayout.LayoutParams) {
			TextViewCompat.setTextAppearance(label, materialR.style.TextAppearance_Material3_LabelMedium)
			label.setTextColor(icon.imageTintList)
			label.maxWidth = if (isBeside) dp(72) else Int.MAX_VALUE
			labelParams.marginStart = if (isBeside) dp(6) else 0
			labelParams.topMargin = if (isBeside) 0 else dp(2)
			wantsLift = false
			content.background = null
			content.setPadding(0, 0, 0, 0)
			setPadding(0, 0, 0, 0)
			minimumWidth = 0
			content.minimumHeight = 0
			content.minimumWidth = 0
			(content.layoutParams as LayoutParams).height = LayoutParams.WRAP_CONTENT
		}

		override fun dispatchDraw(canvas: Canvas) {
			super.dispatchDraw(canvas)
			val badge = entry?.badge ?: return
			// The Material badge at the top end of the icon: a 6 dp dot, or a number on a 16 dp high pill.
			val iconRight = content.left + icon.right.toFloat() + icon.translationX
			val iconTop = content.top + icon.top + icon.translationY
			if (badge <= 0) {
				val r = dp(3).toFloat()
				canvas.drawCircle(iconRight - r, iconTop + r, r, badgePaint)
			} else {
				val text = if (badge > 999) "999+" else badge.toString()
				val h = dp(16).toFloat()
				val w = maxOf(h, badgeTextPaint.measureText(text) + dp(8))
				val left = iconRight - dp(6)
				val rect = RectF(left, iconTop - dp(4), left + w, iconTop - dp(4) + h)
				canvas.drawRoundRect(rect, h / 2, h / 2, badgePaint)
				val baseline = rect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2
				canvas.drawText(text, rect.centerX(), baseline, badgeTextPaint)
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.className = "android.widget.Tab"
			info.isSelected = isItemSelected
		}
	}

	/** An icon with the checked state, so the animated selectors of the sections play their animation. */
	private class CheckableImageView(context: Context) : AppCompatImageView(context) {

		var isChecked: Boolean = false
			set(value) {
				if (field != value) {
					field = value
					refreshDrawableState()
				}
			}

		init {
			scaleType = ScaleType.CENTER_INSIDE
		}

		override fun onCreateDrawableState(extraSpace: Int): IntArray {
			val state = super.onCreateDrawableState(extraSpace + 1)
			if (isChecked) {
				mergeDrawableStates(state, intArrayOf(android.R.attr.state_checked))
			}
			return state
		}
	}

	private companion object {

		const val SELECTION_ANIMATION_MS = 180L

		/** GlassDefaults.navigationShadowElevation in Kototoro. */
		const val NAV_SHADOW_ELEVATION_DP = 4
	}
}
