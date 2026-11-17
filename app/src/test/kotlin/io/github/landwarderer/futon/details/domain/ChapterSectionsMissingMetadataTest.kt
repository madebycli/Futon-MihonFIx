package io.github.landwarderer.futon.details.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSectionsMissingMetadataTest {
    private data class Item(val id: Long, val volume: Int = 0, val credit: String? = null)

    private fun sections(items: List<Item>) = withVolumeSections(items, { it.volume }, { it.credit })

    private fun labels(items: List<Item>): List<Any> = sections(items).map {
        when (it) {
            is ChapterSection.Header -> it.customName ?: "volume:${it.volume}"
            is ChapterSection.Item -> it.chapter.id
        }
    }

    @Test
    fun blankCreditsDoNotCreateHeadersAndStayInTheGroupAbove() {
        val chapters = listOf(Item(1), Item(2, credit = " A "), Item(3, credit = " "),
            Item(4, credit = ""), Item(5, credit = "A"), Item(6, credit = "B"), Item(7, credit = "A"))
        assertEquals(listOf<Any>(1L, "A", 2L, 3L, 4L, 5L, "B", 6L, "A", 7L), labels(chapters))
        assertEquals(chapters, sections(chapters).filterIsInstance<ChapterSection.Item<Item>>().map { it.chapter })
    }

    @Test
    fun realVolumesStillCreateHeadersForUnknownAndKnownVolumes() {
        val chapters = listOf(Item(1), Item(2, volume = 1), Item(3, volume = 1, credit = "A"),
            Item(4, volume = 1), Item(5, volume = 2), Item(6))
        assertEquals(listOf<Any>("volume:0", 1L, "volume:1", 2L, "A", 3L, 4L,
            "volume:2", 5L, "volume:0", 6L), labels(chapters))
    }

    @Test
    fun reversedRowsUseTheirActualOrderAndNeverCreateBlankCreditHeaders() {
        val chapters = listOf(Item(1, credit = "A"), Item(2), Item(3, credit = "A")).reversed()
        assertEquals(listOf<Any>("A", 3L, 2L, 1L), labels(chapters))
        assertTrue(sections(listOf(Item(1, credit = " "), Item(2))).all { it is ChapterSection.Item })
    }
}
