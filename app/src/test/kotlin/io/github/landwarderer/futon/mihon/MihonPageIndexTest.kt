// Regression for Kototoro 038b8826 (Kototoro-app/Kototoro, Apache-2.0): pages that share one chapter url are told
// apart by Page.index, so the wrapped page url must carry the extension's index, not a position or a hashed id.
package io.github.landwarderer.futon.mihon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MihonPageIndexTest {

    @Test
    fun wrappedUrlCarriesTheExtensionsPageIndex() {
        assertEquals(7, wrappedMihonPageIndex("mihon://resolve?page_url=https%3A%2F%2Fa.test%2Fc&index=7"))
        assertEquals(
            12,
            wrappedMihonPageIndex("mihon://image?page_url=p&image_url=https%3A%2F%2Fa.test%2Fi.jpg&index=12"),
        )
    }

    @Test
    fun nonContiguousIndicesAreKept() {
        val indices = listOf(3, 9, 40).map { wrappedMihonPageIndex("mihon://resolve?page_url=u&index=$it") }
        assertEquals(listOf(3, 9, 40), indices)
    }

    @Test
    fun otherUrlsHaveNoWrappedIndex() {
        assertNull(wrappedMihonPageIndex("https://a.test/page?index=5"))
        assertNull(wrappedMihonPageIndex("mihon://resolve?page_url=u"))
    }
}
