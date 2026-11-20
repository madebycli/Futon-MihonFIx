package io.github.landwarderer.futon.core.ui.util

import android.view.View
import androidx.core.view.ViewCompat
import io.github.landwarderer.futon.R

/**
 * Height of the bars that float over the top of a screen's content: with the floating top bar of the main screen the
 * lists start below the search bar (and the tabs of Favourites) but scroll behind them.
 *
 * A host sets the height on a view that contains the lists; a list reads it from its nearest ancestor that has one and
 * adds it to its top padding. Screens without floating bars have none, so their lists stay as they are.
 */
object TopOverlay {

	/** Sets [px] for every list inside [view], and lets them lay out their padding again when it changed. */
	fun set(view: View, px: Int) {
		if (view.getTag(R.id.tag_top_overlay) != px) {
			view.setTag(R.id.tag_top_overlay, px)
			ViewCompat.requestApplyInsets(view)
			// A request made during a layout pass, or while the window is not shown, can get lost: ask once more.
			view.post { ViewCompat.requestApplyInsets(view) }
		}
	}

	/** The height set on the nearest ancestor of [view] (or [view] itself), 0 when there is none. */
	fun of(view: View): Int {
		var current: View? = view
		while (current != null) {
			(current.getTag(R.id.tag_top_overlay) as? Int)?.let { return it }
			current = current.parent as? View
		}
		return 0
	}
}
