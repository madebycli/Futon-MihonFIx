package io.github.landwarderer.futon.core.ui.util

import android.view.View
import androidx.core.view.ViewCompat
import io.github.landwarderer.futon.R

/**
 * Height of the bar that floats over the bottom of a screen's content: with the floating navigation bar of the main
 * screen the lists scroll behind the bar, and get its height as bottom padding so their last item can still be
 * scrolled above it (also while the bar is pinned, and in lists too short to scroll).
 *
 * A host sets the height on a view that contains the lists; a list reads it from its nearest ancestor that has one.
 * Screens without a floating bar have none, so their lists stay as they are.
 */
object BottomOverlay {

	/** Sets [px] for every list inside [view], and lets them lay out their padding again when it changed. */
	fun set(view: View, px: Int) {
		if (view.getTag(R.id.tag_bottom_overlay) != px) {
			view.setTag(R.id.tag_bottom_overlay, px)
			ViewCompat.requestApplyInsets(view)
		}
	}

	/** The height set on the nearest ancestor of [view] (or [view] itself), 0 when there is none. */
	fun of(view: View): Int {
		var current: View? = view
		while (current != null) {
			(current.getTag(R.id.tag_bottom_overlay) as? Int)?.let { return it }
			current = current.parent as? View
		}
		return 0
	}
}
