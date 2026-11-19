package io.github.landwarderer.futon.core.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases as Kototoro's BottomNavLayoutSpec: the roomiest spacing that still fits wins. */
class FloatingNavLayoutSpecTest {

	@Test
	fun aPhoneWidthFitsFiveItemsAndTheButtonRoomily() {
		// 411 dp: 5 x 48 + 4 x 5.8 + 2 x 6.2 = 275.6, + 64 button + 8 gap + 2 x 12 = 371.6
		val spec = FloatingNavLayoutSpec.resolve(411f, 5, 64f, showLabels = true)
		assertEquals(FloatingNavDensity.REGULAR, spec.density)
		assertEquals(5.8f, spec.itemSpacing)
		assertEquals(8f, spec.fabGap)
		assertNull(spec.labelMaxWidth)
	}

	@Test
	fun aNarrowScreenPacksTheItemsTighter() {
		// 5 x 48 + 4 x 4 + 2 x 4 = 264, + 56 + 6 + 2 x 8 = 342
		val spec = FloatingNavLayoutSpec.resolve(345f, 5, 56f, showLabels = true)
		assertEquals(FloatingNavDensity.COMPACT, spec.density)
		assertEquals(8f, spec.outerHorizontalPadding)
	}

	@Test
	fun theTightestSpacingKeepsLabelsBelowButMayDropLabelsBeside() {
		val below = FloatingNavLayoutSpec.resolve(300f, 6, 56f, showLabels = true)
		assertEquals(FloatingNavDensity.MINIMUM, below.density)
		assertTrue(below.showLabels)

		val beside = FloatingNavLayoutSpec.resolve(300f, 6, 56f, showLabels = true, isExpressivePill = true)
		assertEquals(FloatingNavDensity.MINIMUM, beside.density)
		assertFalse(beside.showLabels)
	}

	@Test
	fun labelsBesideTheIconGetALimitedWidthAndScale() {
		// 275.6 + 56 label + 56 button + 8 gap + 2 x 12 = 419.6
		val regular = FloatingNavLayoutSpec.resolve(420f, 5, 56f, showLabels = true, isExpressivePill = true)
		assertEquals(56f, regular.labelMaxWidth)
		assertEquals(1f, regular.labelScale)

		// Compact: 264 + 40 label + 56 + 6 + 2 x 8 = 382.
		val compact = FloatingNavLayoutSpec.resolve(400f, 5, 56f, showLabels = true, isExpressivePill = true)
		assertEquals(FloatingNavDensity.COMPACT, compact.density)
		assertEquals(40f, compact.labelMaxWidth)
		assertEquals(0.84f, compact.labelScale)
	}
}
