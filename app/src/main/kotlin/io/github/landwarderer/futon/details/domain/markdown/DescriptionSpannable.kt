package io.github.landwarderer.futon.details.domain.markdown

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan

/** The [DescriptionText] as a [Spannable] for a TextView with a link movement method. */
fun DescriptionText.toSpannable(): Spannable {
    val result = SpannableStringBuilder(text)
    for (run in runs) {
        if (run.start < 0 || run.start >= run.end || run.end > result.length) continue
        if (run.bold) result.setSpan(StyleSpan(Typeface.BOLD), run.start, run.end, SPAN_FLAGS)
        if (run.italic) result.setSpan(StyleSpan(Typeface.ITALIC), run.start, run.end, SPAN_FLAGS)
        if (run.strikeThrough) result.setSpan(StrikethroughSpan(), run.start, run.end, SPAN_FLAGS)
        if (run.code) result.setSpan(TypefaceSpan("monospace"), run.start, run.end, SPAN_FLAGS)
        run.url?.let { result.setSpan(URLSpan(it), run.start, run.end, SPAN_FLAGS) }
    }
    return result
}

private const val SPAN_FLAGS = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
