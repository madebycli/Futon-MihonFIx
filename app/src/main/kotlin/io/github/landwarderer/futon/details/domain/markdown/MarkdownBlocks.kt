// Ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0):
// core-ui/src/commonMain/kotlin/org/skepsun/kototoro/core/ui/markdown/MarkdownBlocks.kt
// at madebycli/Kototoro PR #2 merge 11fdfb3a0972e56dc6061d3d5ec740695361798a (devel 404e596c79e4170f5aa30ea0cccf286dc76b53e8).
package io.github.landwarderer.futon.details.domain.markdown

/**
 * One block of a Markdown document, as far as release notes and changelogs use them.
 *
 * Android renders those with Markwon; the shared hosts render this subset instead, so the text that reaches the
 * screen is the same on both. Anything outside the subset (tables, footnotes, raw HTML beyond a line break) is
 * kept as paragraph text rather than dropped, so a note never silently loses its content.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Paragraph(val text: String) : MarkdownBlock

    /** A `> quoted` paragraph. */
    data class Quote(val text: String) : MarkdownBlock

    /**
     * One list item: [marker] is `•` for bullets, `☐`/`☑` for task items, or the number as `1.` for ordered lists;
     * [depth] counts the two-space indents in front of it.
     */
    data class ListItem(val marker: String, val text: String, val depth: Int = 0) : MarkdownBlock

    /** A fenced code block; [language] is the word after the opening fence, if any. */
    data class Code(val language: String?, val text: String) : MarkdownBlock

    /** A `---` / `***` / `___` horizontal rule. */
    data object Rule : MarkdownBlock
}

/** An inline run of a text block: literal text with a few toggles, or a link. */
sealed interface MarkdownSpan {
    data class Text(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strikeThrough: Boolean = false,
        val code: Boolean = false,
    ) : MarkdownSpan

    data class Link(val text: String, val url: String) : MarkdownSpan
}

/**
 * Splits [markdown] into blocks: ATX headings, paragraphs with soft/hard line breaks, block quotes, bullet, task and
 * ordered lists, fenced code and horizontal rules.
 *
 * The parser is deliberately forgiving: release notes are written by hand and by GitHub's own generator, so an
 * unclosed fence keeps the rest as code, an unclosed inline marker stays literal, and HTML comments (which the
 * generator leaves behind as release-template placeholders) never reach the screen.
 */
fun parseMarkdownBlocks(markdown: String, preserveLineBreaks: Boolean = false): List<MarkdownBlock> {
    val lines = stripComments(markdown).replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = ArrayList<MarkdownBlock>()
    val paragraph = ArrayList<String>()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        val text = joinLines(paragraph, preserveLineBreaks)
        paragraph.clear()
        if (text.isNotEmpty()) blocks += MarkdownBlock.Paragraph(text)
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> {
                flushParagraph()
                index++
            }

            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                flushParagraph()
                val fence = trimmed.take(3)
                val language = trimmed.removePrefix(fence).trim().takeIf { it.isNotEmpty() }
                val code = ArrayList<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith(fence)) {
                    code += lines[index]
                    index++
                }
                // Skip the closing fence when there is one; an unclosed fence keeps everything up to the end.
                if (index < lines.size) index++
                blocks += MarkdownBlock.Code(language, code.joinToString("\n"))
            }

            ATX_HEADING.matches(trimmed) -> {
                flushParagraph()
                val match = ATX_HEADING.matchEntire(trimmed)!!
                // GitHub closes headings with optional trailing hashes: "## Notes ##".
                blocks += MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    text = match.groupValues[2].trim().trimEnd('#').trim(),
                )
                index++
            }

            RULE.matches(trimmed) -> {
                flushParagraph()
                blocks += MarkdownBlock.Rule
                index++
            }

            trimmed.startsWith(">") -> {
                flushParagraph()
                val quote = ArrayList<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quote += lines[index].trim().removePrefix(">").removePrefix(" ").trimEnd()
                    index++
                }
                blocks += MarkdownBlock.Quote(joinLines(quote, preserveLineBreaks))
            }

            else -> {
                val bullet = BULLET.matchEntire(line)
                val ordered = ORDERED.matchEntire(line)
                when {
                    bullet != null -> {
                        flushParagraph()
                        val body = bullet.groupValues[3].trim()
                        val task = TASK_PREFIX.find(body)
                        blocks += if (task != null) {
                            MarkdownBlock.ListItem(
                                marker = if (task.groupValues[1].equals("x", ignoreCase = true)) "☑" else "☐",
                                text = body.substring(task.range.last + 1).trim(),
                                depth = depthOf(bullet.groupValues[1]),
                            )
                        } else {
                            MarkdownBlock.ListItem("•", body, depthOf(bullet.groupValues[1]))
                        }
                        index++
                    }

                    ordered != null -> {
                        flushParagraph()
                        blocks += MarkdownBlock.ListItem(
                            marker = "${ordered.groupValues[2]}.",
                            text = ordered.groupValues[3].trim(),
                            depth = depthOf(ordered.groupValues[1]),
                        )
                        index++
                    }

                    else -> {
                        // Kept raw: a paragraph line's trailing spaces are a Markdown hard line break.
                        paragraph += line
                        index++
                    }
                }
            }
        }
    }
    flushParagraph()
    return blocks
}

/**
 * Splits one line of inline Markdown into runs: `**bold**`, `*italic*`, `~~strike~~`, `` `code` ``, `[text](url)` and
 * `![alt](url)`. Backslash escapes the next character, `<br>` becomes a newline and other tags are dropped.
 *
 * Emphases do not nest: `**bold**` inside a link label is reported as part of the [MarkdownSpan.Link]. Underscore
 * emphases must sit on word boundaries, so `snake_case_name` stays literal.
 *
 * [descriptionMode] reads source descriptions, which are mostly plain text: bare and `<…>` HTTP(S) URLs become links,
 * emphasis needs CommonMark flanking (`5 * 3 * 2` stays literal), a backslash only escapes Markdown punctuation
 * (`C:\books` keeps it), and angle brackets that are not a link or `<br>` stay text (`<3`).
 */
fun parseMarkdownInline(markdown: String, descriptionMode: Boolean = false): List<MarkdownSpan> {
    val spans = ArrayList<MarkdownSpan>()
    val literal = StringBuilder()
    var index = 0

    fun flush() {
        if (literal.isEmpty()) return
        spans += MarkdownSpan.Text(literal.toString())
        literal.clear()
    }

    while (index < markdown.length) {
        val char = markdown[index]
        val urlEnd = if (descriptionMode) bareUrlEnd(markdown, index) else -1
        if (urlEnd > index) {
            flush()
            val url = markdown.substring(index, urlEnd)
            spans += MarkdownSpan.Link(url, url)
            index = urlEnd
            continue
        }
        if (char == '<') {
            val close = markdown.indexOf('>', index + 1)
            if (close > index) {
                val inner = markdown.substring(index + 1, close)
                if (descriptionMode && !inner.any(Char::isWhitespace) &&
                    (inner.startsWith("https://", true) || inner.startsWith("http://", true))
                ) {
                    flush()
                    spans += MarkdownSpan.Link(inner, inner)
                    index = close + 1
                    continue
                }
                val tag = inner.trim().lowercase()
                if (tag == "br" || tag == "br/") {
                    literal.append('\n')
                    index = close + 1
                    continue
                }
                if (!descriptionMode) {
                    index = close + 1
                    continue
                }
            }
        }
        if (char == '\\' && index + 1 < markdown.length &&
            (!descriptionMode || markdown[index + 1] in "\\`*_{}[]()#+-.!>~")
        ) {
            literal.append(markdown[index + 1])
            index += 2
            continue
        }
        if (char == '[' || (char == '!' && index + 1 < markdown.length && markdown[index + 1] == '[')) {
            val link = LINK.find(markdown, index)?.takeIf { it.range.first == index }
            if (link != null) {
                flush()
                val label = link.groupValues[1]
                val url = link.groupValues[2]
                spans += MarkdownSpan.Link(label.ifEmpty { url }, url)
                index = link.range.last + 1
                continue
            }
        }
        val marker = markerAt(markdown, index)?.takeUnless { descriptionMode && !opensRun(markdown, index, it) }
        if (marker == null) {
            literal.append(char)
            index++
            continue
        }
        val start = index + marker.length
        val end = closingMarker(markdown, marker, start, requireFlanking = descriptionMode)
        if (end <= start) {
            literal.append(marker)
            index = start
            continue
        }
        val inner = markdown.substring(start, end)
        flush()
        spans += when (marker) {
            "**", "__" -> MarkdownSpan.Text(inner, bold = true)
            "~~" -> MarkdownSpan.Text(inner, strikeThrough = true)
            "`" -> MarkdownSpan.Text(inner, code = true)
            else -> MarkdownSpan.Text(inner, italic = true)
        }
        index = end + marker.length
    }
    flush()
    return spans
}

/** The emphasis marker that starts at [index], if any; underscores only count on a word boundary. */
private fun markerAt(markdown: String, index: Int): String? {
    val char = markdown[index]
    return when {
        markdown.startsWith("**", index) -> "**"
        markdown.startsWith("~~", index) -> "~~"
        char == '`' -> "`"
        char == '*' -> "*"
        markdown.startsWith("__", index) ->
            if (isWordBoundaryBefore(markdown, index)) "__" else null
        char == '_' -> if (isWordBoundaryBefore(markdown, index)) "_" else null
        else -> null
    }
}

/**
 * Where the marker that opened at [start] closes, or -1 when it never does. With [requireFlanking] the closing marker
 * must follow a non-space character, as CommonMark asks, so `5 * 3 * 2` stays arithmetic.
 */
private fun closingMarker(markdown: String, marker: String, start: Int, requireFlanking: Boolean = false): Int {
    var found = markdown.indexOf(marker, start)
    while (found > 0) {
        val closesWord = marker.all { it == '_' } && found + marker.length < markdown.length &&
            markdown[found + marker.length].isLetterOrDigit()
        val afterSpace = requireFlanking && markdown[found - 1].isWhitespace()
        if (!closesWord && !afterSpace) return found
        found = markdown.indexOf(marker, found + marker.length)
    }
    return -1
}

/**
 * Where a bare `http://` or `https://` URL that starts at [index] ends, or -1 when none starts there. Sentence
 * punctuation after the URL and a closing parenthesis it did not open stay outside: `(see https://a.b/c).`
 */
private fun bareUrlEnd(markdown: String, index: Int): Int {
    val schemeLength = when {
        markdown.startsWith("https://", index, ignoreCase = true) -> 8
        markdown.startsWith("http://", index, ignoreCase = true) -> 7
        else -> return -1
    }
    var end = index
    while (end < markdown.length && markdown[end].isUrlChar()) end++
    var balance = 0
    for (position in index until end) {
        when (markdown[position]) {
            '(' -> balance++
            ')' -> balance--
        }
    }
    while (end > index) {
        val last = markdown[end - 1]
        when {
            last in ".,;:!?'*" -> end--
            last == ')' && balance < 0 -> {
                end--
                balance++
            }
            else -> break
        }
    }
    return if (end > index + schemeLength) end else -1
}

/**
 * The characters a bare URL in a description may contain: RFC 3986's set without the IPv6 brackets. Anything else
 * ends it, so `[https://a.b]` keeps its bracket and the full-width punctuation and text that CJK descriptions put
 * right after a link stay outside it.
 */
private fun Char.isUrlChar(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in "-._~:/?#@!$&'()*+,;=%"

/** A marker only opens a run when text follows it directly: `** bold **` and a lone `*` stay literal. */
private fun opensRun(markdown: String, index: Int, marker: String): Boolean {
    val next = index + marker.length
    return next < markdown.length && !markdown[next].isWhitespace()
}

private fun isWordBoundaryBefore(markdown: String, index: Int): Boolean =
    index == 0 || !markdown[index - 1].isLetterOrDigit()

/** `#`…`######` followed by the heading text; GitHub requires the space, and so does this. */
private val ATX_HEADING = Regex("^(#{1,6})\\s+(.*)$")

/** Three or more of the same rule character, optionally spaced: `---`, `***`, `_ _ _`. */
private val RULE = Regex("^([-*_])(?:\\s*\\1){2,}$")

/** Bullet: indentation, the marker, then the text. */
private val BULLET = Regex("^(\\s*)([-*+])\\s+(.*)$")

/** Ordered item: indentation, the number with `.` or `)`, then the text. */
private val ORDERED = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")

/** GitHub task list prefix inside a bullet: `[ ]` or `[x]`. */
private val TASK_PREFIX = Regex("^\\[([ xX])]\\s*")

/** `[text](url)`, `[text](url "title")` or `![alt](url)`; callers anchor it by comparing the match start. */
private val LINK = Regex("!?\\[([^\\]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)")

/** Two spaces per nesting level; a tab counts as one level because notes are written by hand. */
private fun depthOf(indent: String): Int =
    (indent.count { it == ' ' } / 2 + indent.count { it == '\t' }).coerceAtMost(3)

/**
 * Joins the lines of one paragraph the way Markdown does: a soft break becomes a space, a hard break (two trailing
 * spaces or a trailing backslash) stays a newline.
 */
private fun joinLines(lines: List<String>, preserveLineBreaks: Boolean = false): String {
    val builder = StringBuilder()
    lines.forEachIndexed { position, raw ->
        if (position > 0) {
            val previous = lines[position - 1]
            val previousBreak = previous.endsWith("  ") || previous.endsWith("\\")
            builder.append(if (previousBreak || preserveLineBreaks) '\n' else ' ')
        }
        builder.append(raw.removeSuffix("\\").trim())
    }
    return builder.toString().trim()
}

/** Drops `<!-- … -->` blocks: GitHub's release template leaves those as placeholders for maintainers. */
private fun stripComments(markdown: String): String {
    if (!markdown.contains("<!--")) return markdown
    val builder = StringBuilder(markdown.length)
    var index = 0
    while (index < markdown.length) {
        val start = markdown.indexOf("<!--", index)
        if (start < 0) {
            builder.append(markdown, index, markdown.length)
            break
        }
        builder.append(markdown, index, start)
        val end = markdown.indexOf("-->", start + 4)
        index = if (end < 0) markdown.length else end + 3
    }
    return builder.toString()
}
