// Ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0):
// app/src/main/kotlin/org/skepsun/kototoro/details/domain/DescriptionMarkup.kt at madebycli/Kototoro 11fdfb3a.
package io.github.landwarderer.futon.details.domain

/** An HTML element (`<b>`, `</p>`, `<br/>`, `<a href="…">`) or a character reference (`&amp;`, `&#39;`, `&#x27;`). */
private val HTML_MARKUP = Regex(
    "</?[A-Za-z][A-Za-z0-9]*(?:\\s[^>]*)?/?>|&(?:#[0-9]+|#x[0-9A-Fa-f]+|[A-Za-z][A-Za-z0-9]*);",
)

/** An element that already lays the text out in lines, so the source's own newlines are only formatting. */
private val HTML_LINE_STRUCTURE = Regex(
    "<(?:br|p|div|li|ul|ol|h[1-6]|blockquote|pre|table|tr|hr)(?:[\\s/>])",
    RegexOption.IGNORE_CASE,
)

/**
 * How a source description should reach `Html.fromHtml`, or `null` when it carries no HTML at all and is shown as
 * it is.
 *
 * `Html.fromHtml` folds every newline into a space, which would run together the lines, lists and separators of a
 * plain-text or Markdown description (Mihon and Aniyomi extensions send those). So text without HTML skips the
 * parser, and HTML that does not lay out its own lines keeps the source's newlines as `<br>`. Descriptions that
 * structure themselves with `<p>`, `<br>`, lists or the like are left untouched, as before.
 */
internal fun descriptionHtmlSource(description: String): String? {
    if (!HTML_MARKUP.containsMatchIn(description)) return null
    if (HTML_LINE_STRUCTURE.containsMatchIn(description)) return description
    return description.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>")
}
