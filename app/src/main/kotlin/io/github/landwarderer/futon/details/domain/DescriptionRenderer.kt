package io.github.landwarderer.futon.details.domain

import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import androidx.core.text.getSpans
import androidx.core.text.parseAsHtml
import io.github.landwarderer.futon.core.util.ext.isHttpUrl
import io.github.landwarderer.futon.core.util.ext.sanitize
import io.github.landwarderer.futon.details.domain.markdown.buildDescriptionText
import io.github.landwarderer.futon.details.domain.markdown.toSpannable

/**
 * Renders a description a source supplied, for a TextView with a link movement method.
 *
 * - Text without any HTML (plain text, Markdown, as Mihon and Aniyomi extensions send it) is not run through the HTML
 *   parser, which would fold its line breaks into spaces; it is formatted as Markdown instead.
 * - HTML goes through `Html.fromHtml`, keeping the source's line breaks when it does not lay out its own lines.
 *
 * Links stay clickable only when they are `http` or `https`. Blocking: call it off the main thread.
 *
 * @param imageGetter loads inline images of an HTML description, or `null` to render without them (the result is
 * then sanitized to plain text like before).
 */
fun renderDescription(description: String, imageGetter: Html.ImageGetter?): CharSequence {
    val html = descriptionHtmlSource(description)
        ?: return buildDescriptionText(description.sanitize().toString()).toSpannable()
    return if (imageGetter != null) {
        html.parseAsHtml(imageGetter = imageGetter).filterDescriptionSpans()
    } else {
        html.parseAsHtml().filterDescriptionSpans().sanitize()
    }
}

/** Removes the foreground colors of an HTML description and every link that is not `http` or `https`. */
fun Spanned.filterDescriptionSpans(): Spanned {
    val spannable = SpannableString.valueOf(this)
    for (span in spannable.getSpans<ForegroundColorSpan>()) {
        spannable.removeSpan(span)
    }
    for (span in spannable.getSpans<URLSpan>()) {
        if (!span.url.orEmpty().trim().isHttpUrl()) {
            spannable.removeSpan(span)
        }
    }
    return spannable
}
