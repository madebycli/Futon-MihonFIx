// Block layout ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0):
// core-ui/src/commonMain/kotlin/org/skepsun/kototoro/core/ui/markdown/MarkdownText.kt
// (buildDescriptionAnnotatedString) at madebycli/Kototoro PR #2 merge 11fdfb3a0972e56dc6061d3d5ec740695361798a.
// Futon renders into a Spannable for its TextView; this file holds the Android-free part.
package io.github.landwarderer.futon.details.domain.markdown

/** A styled range of a [DescriptionText]; [url] is only ever an http(s) link. */
data class DescriptionRun(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikeThrough: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

/** A source description as plain text plus the ranges to style. */
data class DescriptionText(val text: String, val runs: List<DescriptionRun>)

private const val DESCRIPTION_RULE = "────────"

/**
 * Builds the text of a Markdown or plain-text source description: the source's own line breaks are kept, bare
 * `http(s)` URLs become links, and only `http`/`https` links are ever clickable.
 */
fun buildDescriptionText(markdown: String): DescriptionText {
    val blocks = parseMarkdownBlocks(markdown, preserveLineBreaks = true)
    val text = StringBuilder()
    val runs = ArrayList<DescriptionRun>()
    blocks.forEachIndexed { index, block ->
        if (index > 0) {
            val previous = blocks[index - 1]
            text.append(if (previous is MarkdownBlock.ListItem && block is MarkdownBlock.ListItem) "\n" else "\n\n")
        }
        val content = when (block) {
            is MarkdownBlock.Heading -> block.text
            is MarkdownBlock.Paragraph -> block.text
            is MarkdownBlock.Quote -> "❯ ${block.text}"
            is MarkdownBlock.ListItem -> "  ".repeat(block.depth) + block.marker + " " + block.text
            is MarkdownBlock.Code -> block.text
            MarkdownBlock.Rule -> DESCRIPTION_RULE
        }
        if (block is MarkdownBlock.Code) {
            val start = text.length
            text.append(content)
            if (text.length > start) runs += DescriptionRun(start, text.length, code = true)
            return@forEachIndexed
        }
        val isHeading = block is MarkdownBlock.Heading
        for (span in parseMarkdownInline(content, descriptionMode = true)) {
            val start = text.length
            when (span) {
                is MarkdownSpan.Text -> {
                    text.append(span.text)
                    val bold = span.bold || isHeading
                    if (text.length > start && (bold || span.italic || span.strikeThrough || span.code)) {
                        runs += DescriptionRun(
                            start = start,
                            end = text.length,
                            bold = bold,
                            italic = span.italic,
                            strikeThrough = span.strikeThrough,
                            code = span.code,
                        )
                    }
                }

                is MarkdownSpan.Link -> {
                    text.append(span.text)
                    if (text.length > start && span.url.isWebUrl()) {
                        runs += DescriptionRun(start, text.length, url = span.url)
                    }
                }
            }
        }
    }
    return DescriptionText(text.toString(), runs)
}

private fun String.isWebUrl(): Boolean =
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
