package io.github.landwarderer.futon.core.ui.text

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.TextView

/**
 * Makes the links of a description clickable and nothing else.
 *
 * [LinkMovementMethod] inherits `ScrollingMovementMethod`, which scrolls the text *inside* a TextView that has a
 * `maxLines` limit whenever it is dragged. A collapsed description sits in a scrolling screen, so such a drag made
 * the text jump on its own, and expanding or collapsing it afterwards left it scrolled to a random offset. This
 * method claims a touch only when it hits a link; every other touch goes to the parent that scrolls the screen.
 */
object DescriptionMovementMethod : LinkMovementMethod() {

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN) return false
        val layout = widget.layout ?: return false
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
        if (y < 0 || y > layout.height) return false
        val line = layout.getLineForVertical(y)
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return false
        val offset = layout.getOffsetForHorizontal(line, x)
        val link = buffer.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull() ?: return false
        if (action == MotionEvent.ACTION_UP) {
            link.onClick(widget)
        }
        return true
    }

    override fun canSelectArbitrarily(): Boolean = false
}
