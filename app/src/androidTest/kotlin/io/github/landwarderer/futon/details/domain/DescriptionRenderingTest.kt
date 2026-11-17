package io.github.landwarderer.futon.details.domain

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.landwarderer.futon.details.domain.markdown.DescriptionRun
import io.github.landwarderer.futon.details.domain.markdown.DescriptionText
import io.github.landwarderer.futon.details.domain.markdown.buildDescriptionText
import io.github.landwarderer.futon.details.domain.markdown.toSpannable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DescriptionRenderingTest {
    private val noImages = Html.ImageGetter { null }

    @Test
    fun htmlOnlyKeepsHttpAndHttpsLinks() {
        val schemes = listOf("https://example.test", "HTTP://example.test", "mailto:a@b.test",
            "javascript:alert(1)", "intent://open", "file:///tmp/a", "content://a")
        val html = schemes.mapIndexed { index, url -> "<a href=\"$url\">label$index</a>" }.joinToString(" ")
        val result = renderDescription(html, noImages) as Spanned
        val urls = result.getSpans(0, result.length, URLSpan::class.java).map { it.url }
        assertEquals(listOf("https://example.test", "HTTP://example.test"), urls)
        assertTrue(result.toString().contains("label6"))
    }

    @Test
    fun filteringPreservesStylesAndDoesNotMutateTheInput() {
        val original = SpannableString("abc def")
        original.setSpan(ForegroundColorSpan(Color.RED), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        original.setSpan(StyleSpan(Typeface.BOLD), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        original.setSpan(URLSpan("https://example.test"), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        original.setSpan(URLSpan("mailto:a@b.test"), 4, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val result = original.filterDescriptionSpans()
        assertEquals(0, result.getSpans(0, result.length, ForegroundColorSpan::class.java).size)
        assertEquals(1, result.getSpans(0, result.length, StyleSpan::class.java).size)
        assertEquals(1, result.getSpans(0, result.length, URLSpan::class.java).size)
        assertEquals(2, original.getSpans(0, original.length, URLSpan::class.java).size)
        assertEquals(1, original.getSpans(0, original.length, ForegroundColorSpan::class.java).size)
    }

    @Test
    fun inlineImagesUseTheExistingImageGetterAndSurviveSpanFiltering() {
        val sources = mutableListOf<String>()
        val getter = Html.ImageGetter { source ->
            sources += source
            ColorDrawable(Color.RED).apply { setBounds(0, 0, 10, 10) }
        }
        val result = renderDescription("Before <img src=\"https://example.test/cover.png\"> after", getter) as Spanned
        assertEquals(listOf("https://example.test/cover.png"), sources)
        assertEquals(1, result.getSpans(0, result.length, ImageSpan::class.java).size)
        val withoutImages = renderDescription("Before <img src=\"https://example.test/cover.png\"> after", null)
        assertFalse(withoutImages.contains('\uFFFC'))
    }

    @Test
    fun markdownSpanOffsetsUseUtf16AndKeepLineBreaks() {
        val result = buildDescriptionText("😀 **bold** [Link](https://example.test)\nSecond line").toSpannable()
        assertEquals("😀 bold Link\nSecond line", result.toString())
        val bold = result.getSpans(0, result.length, StyleSpan::class.java).single()
        val link = result.getSpans(0, result.length, URLSpan::class.java).single()
        assertEquals(3, result.getSpanStart(bold))
        assertEquals(7, result.getSpanEnd(bold))
        assertEquals(8, result.getSpanStart(link))
        assertEquals(12, result.getSpanEnd(link))
    }

    @Test
    fun spanAdapterRejectsInvalidRangesWithoutDroppingValidStyles() {
        val result = DescriptionText("abc", listOf(
            DescriptionRun(-1, 2, bold = true),
            DescriptionRun(0, 20, bold = true),
            DescriptionRun(1, 1, bold = true),
            DescriptionRun(0, 3, bold = true, italic = true),
        )).toSpannable()
        assertEquals("abc", result.toString())
        assertEquals(2, result.getSpans(0, result.length, StyleSpan::class.java).size)
    }

    @Test
    fun unsafeMarkdownLinksRetainLabelsWithoutClickableSpans() {
        val result = renderDescription("[mail](mailto:a@b.test) [unsafe](javascript:alert(1))", null) as Spanned
        assertTrue(result.toString().contains("mail"))
        assertEquals(0, result.getSpans(0, result.length, URLSpan::class.java).size)
    }
}
