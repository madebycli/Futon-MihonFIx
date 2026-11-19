// Ported from Kototoro (https://github.com/Kototoro-app/Kototoro, Apache License 2.0):
// app/src/main/kotlin/org/skepsun/kototoro/core/ui/widgets/BottomNavLayoutSpec.kt (resolveBottomNavLayout).
// Compose Dp values became Float dp values; the logic and the numbers are unchanged.
package io.github.landwarderer.futon.core.ui.widgets

/** How tightly the floating bar packs its items, from roomy to as tight as it gets. */
enum class FloatingNavDensity {
	REGULAR,
	COMPACT,
	MINIMUM,
}

/** Spacing of the floating bar for one width; every length is in dp. */
data class FloatingNavLayoutSpec(
	val density: FloatingNavDensity,
	val itemSpacing: Float,
	val horizontalPadding: Float,
	val outerHorizontalPadding: Float,
	val fabGap: Float,
	val showLabels: Boolean,
	val labelScale: Float,
	val labelMaxWidth: Float?,
) {

	companion object {

		const val ITEM_TOUCH_TARGET = 48f
		private const val REGULAR_ITEM_SPACING = 5.8f
		private const val REGULAR_HORIZONTAL_PADDING = 6.2f
		private const val COMPACT_ITEM_SPACING = 4f
		private const val COMPACT_HORIZONTAL_PADDING = 4f
		private const val MINIMUM_ITEM_SPACING = 2f
		private const val MINIMUM_HORIZONTAL_PADDING = 0f
		private const val REGULAR_OUTER_PADDING = 12f
		private const val COMPACT_OUTER_PADDING = 8f
		private const val MINIMUM_OUTER_PADDING = 0f
		private const val REGULAR_FAB_GAP = 8f
		private const val COMPACT_FAB_GAP = 6f
		private const val MINIMUM_FAB_GAP = 4f
		private const val REGULAR_EXPRESSIVE_LABEL_WIDTH = 56f
		private const val COMPACT_EXPRESSIVE_LABEL_WIDTH = 40f
		private const val MINIMUM_EXPRESSIVE_LABEL_WIDTH = 32f
		private const val COMPACT_EXPRESSIVE_LABEL_SCALE = 0.84f
		private const val MINIMUM_EXPRESSIVE_LABEL_SCALE = 0.76f

		/**
		 * Picks the roomiest spacing with which [itemCount] items (and the round button of [fabWidth] beside them)
		 * fit into [availableWidth]. [isExpressivePill] is the "label beside the icon" look, which needs room for
		 * the label of the selected item.
		 */
		fun resolve(
			availableWidth: Float,
			itemCount: Int,
			fabWidth: Float?,
			showLabels: Boolean,
			isExpressivePill: Boolean = false,
		): FloatingNavLayoutSpec {
			val normalizedItemCount = itemCount.coerceAtLeast(1)
			val fab = fabWidth ?: 0f

			fun fits(
				itemSpacing: Float,
				horizontalPadding: Float,
				outerPadding: Float,
				fabGap: Float,
				expressiveLabelWidth: Float = 0f,
			): Boolean {
				val navWidth = ITEM_TOUCH_TARGET * normalizedItemCount +
					itemSpacing * (normalizedItemCount - 1) +
					horizontalPadding * 2 + expressiveLabelWidth
				return navWidth + fab + fabGap + outerPadding * 2 <= availableWidth
			}

			val shouldShowExpressiveLabels = showLabels && isExpressivePill

			return when {
				fits(
					itemSpacing = REGULAR_ITEM_SPACING,
					horizontalPadding = REGULAR_HORIZONTAL_PADDING,
					outerPadding = REGULAR_OUTER_PADDING,
					fabGap = REGULAR_FAB_GAP,
					expressiveLabelWidth = if (shouldShowExpressiveLabels) REGULAR_EXPRESSIVE_LABEL_WIDTH else 0f,
				) -> FloatingNavLayoutSpec(
					density = FloatingNavDensity.REGULAR,
					itemSpacing = REGULAR_ITEM_SPACING,
					horizontalPadding = REGULAR_HORIZONTAL_PADDING,
					outerHorizontalPadding = REGULAR_OUTER_PADDING,
					fabGap = REGULAR_FAB_GAP,
					showLabels = showLabels,
					labelScale = 1f,
					labelMaxWidth = REGULAR_EXPRESSIVE_LABEL_WIDTH.takeIf { shouldShowExpressiveLabels },
				)

				fits(
					itemSpacing = COMPACT_ITEM_SPACING,
					horizontalPadding = COMPACT_HORIZONTAL_PADDING,
					outerPadding = COMPACT_OUTER_PADDING,
					fabGap = COMPACT_FAB_GAP,
					expressiveLabelWidth = if (shouldShowExpressiveLabels) COMPACT_EXPRESSIVE_LABEL_WIDTH else 0f,
				) -> FloatingNavLayoutSpec(
					density = FloatingNavDensity.COMPACT,
					itemSpacing = COMPACT_ITEM_SPACING,
					horizontalPadding = COMPACT_HORIZONTAL_PADDING,
					outerHorizontalPadding = COMPACT_OUTER_PADDING,
					fabGap = COMPACT_FAB_GAP,
					showLabels = showLabels,
					labelScale = if (shouldShowExpressiveLabels) COMPACT_EXPRESSIVE_LABEL_SCALE else 1f,
					labelMaxWidth = COMPACT_EXPRESSIVE_LABEL_WIDTH.takeIf { shouldShowExpressiveLabels },
				)

				else -> FloatingNavLayoutSpec(
					density = FloatingNavDensity.MINIMUM,
					itemSpacing = MINIMUM_ITEM_SPACING,
					horizontalPadding = MINIMUM_HORIZONTAL_PADDING,
					outerHorizontalPadding = MINIMUM_OUTER_PADDING,
					fabGap = MINIMUM_FAB_GAP,
					showLabels = if (isExpressivePill) {
						shouldShowExpressiveLabels && fits(
							itemSpacing = MINIMUM_ITEM_SPACING,
							horizontalPadding = MINIMUM_HORIZONTAL_PADDING,
							outerPadding = MINIMUM_OUTER_PADDING,
							fabGap = MINIMUM_FAB_GAP,
							expressiveLabelWidth = MINIMUM_EXPRESSIVE_LABEL_WIDTH,
						)
					} else {
						showLabels
					},
					labelScale = if (shouldShowExpressiveLabels) MINIMUM_EXPRESSIVE_LABEL_SCALE else 1f,
					labelMaxWidth = MINIMUM_EXPRESSIVE_LABEL_WIDTH.takeIf { shouldShowExpressiveLabels },
				)
			}
		}
	}
}
