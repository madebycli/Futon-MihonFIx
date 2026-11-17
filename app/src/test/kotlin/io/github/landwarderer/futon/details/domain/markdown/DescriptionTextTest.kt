// Cases ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0): DescriptionAnnotatedStringTest at
// madebycli/Kototoro 11fdfb3a, adapted from Compose AnnotatedString to Futon's Android-free DescriptionText.
package io.github.landwarderer.futon.details.domain.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionTextTest {

    private fun DescriptionText.slice(run: DescriptionRun) = text.substring(run.start, run.end)

    private val DescriptionText.boldTexts get() = runs.filter { it.bold }.map { slice(it) }

    private val DescriptionText.links get() = runs.filter { it.url != null }.map { it.url to slice(it) }

    @Test
    fun anExtensionDescriptionReadsAsFormattedTextWithItsLineBreaks() {
        val text = buildDescriptionText(
            "**Year:** 2024\n**Status:** Ongoing\n[AniList](https://anilist.co/manga/1)\n\n- One\n- Two\n\n---\n\nEnd",
        )
        assertEquals("Year: 2024\nStatus: Ongoing\nAniList\n\n• One\n• Two\n\n────────\n\nEnd", text.text)
        assertEquals(listOf("Year:", "Status:"), text.boldTexts)
        assertEquals(listOf("https://anilist.co/manga/1" to "AniList"), text.links)
    }

    @Test
    fun onlyWebUrlsBecomeLinks() {
        val text = buildDescriptionText("[Site](https://example.org) [App](intent://open) see https://example.org/a.")
        assertEquals("Site App see https://example.org/a.", text.text)
        assertEquals(
            listOf("https://example.org" to "Site", "https://example.org/a" to "https://example.org/a"),
            text.links,
        )
    }

    @Test
    fun unsafeSchemesNeverBecomeLinksButKeepTheirLabel() {
        val text = buildDescriptionText(
            "[a](javascript:alert(1)) [b](intent://x) [c](file:///sdcard/x) [d](content://x) [e](mailto:a@b.c) [f](HTTP://ok.example)",
        )
        assertEquals("a b c d e f", text.text)
        assertEquals(listOf("HTTP://ok.example" to "f"), text.links)
    }

    @Test
    fun plainTextKeepsLiteralsAndLineBreaks() {
        val text = buildDescriptionText("I <3 this\n5 * 3 * 2 = 30\nC:\\books\nunclosed **marker")
        assertEquals("I <3 this\n5 * 3 * 2 = 30\nC:\\books\nunclosed **marker", text.text)
        assertTrue(text.runs.isEmpty())
    }

    @Test
    fun urlsStopBeforeCjkPunctuationAndUnbalancedBrackets() {
        val text = buildDescriptionText("官网：https://example.org/d?x=1&y=2。更多 (see https://example.org/a_(b).)")
        assertEquals(
            listOf("https://example.org/d?x=1&y=2" to "https://example.org/d?x=1&y=2", "https://example.org/a_(b)" to "https://example.org/a_(b)"),
            text.links,
        )
    }

    @Test
    fun headingsQuotesListsAndCodeKeepTheirLayout() {
        val text = buildDescriptionText("# Title\n\n> quoted\n\n1. first\n2. second\n\n```\ncode here\n```")
        assertEquals("Title\n\n❯ quoted\n\n1. first\n2. second\n\ncode here", text.text)
        assertEquals(listOf("Title"), text.boldTexts)
        assertEquals(listOf("code here"), text.runs.filter { it.code }.map { text.slice(it) })
    }

    @Test
    fun emphasisStrikeAndInlineCodeAreStyled() {
        val text = buildDescriptionText("**bold** *it* ~~gone~~ `mono`")
        assertEquals("bold it gone mono", text.text)
        assertEquals(listOf("bold"), text.boldTexts)
        assertEquals(listOf("it"), text.runs.filter { it.italic }.map { text.slice(it) })
        assertEquals(listOf("gone"), text.runs.filter { it.strikeThrough }.map { text.slice(it) })
        assertEquals(listOf("mono"), text.runs.filter { it.code }.map { text.slice(it) })
    }

    @Test
    fun blankInputGivesEmptyText() {
        assertEquals("", buildDescriptionText("").text)
        assertEquals("", buildDescriptionText("  \n ").text.trim())
    }
}
