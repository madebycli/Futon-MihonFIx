package io.github.landwarderer.futon.details.domain

import io.github.landwarderer.futon.core.model.AnonymousMangaSource
import io.github.landwarderer.futon.core.model.LocalMangaSource
import io.github.landwarderer.futon.details.data.MangaDetails
import io.github.landwarderer.futon.details.ui.mapChapters
import io.github.landwarderer.futon.local.domain.model.LocalManga
import io.github.landwarderer.futon.reader.ui.ReaderState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import java.io.File

/**
 * Chapter groups shown as one list, ported from Kototoro (`ChapterGroupingTest`, `ChaptersDeduplicationTest`),
 * plus the Futon wiring: [MangaDetails], the reader list and the chapter list items.
 */
class ChapterGroupsTest {

    private val source = AnonymousMangaSource("MIHON_42", "Test")

    @Before
    fun enableMerge() {
        ChapterMergeSetting.isEnabled = true
    }

    @After
    fun resetMerge() {
        ChapterMergeSetting.isEnabled = ChapterMergeSetting.DEFAULT
    }

    // --- Kototoro ChapterGroupingTest: sections ---------------------------------------------------------------

    private data class Item(val id: Long, val volume: Int = 0, val scanlator: String? = null)

    private fun sections(items: List<Item>) = withVolumeSections(items, { it.volume }, { it.scanlator })

    private fun List<ChapterSection<Item>>.labels() = map {
        when (it) {
            is ChapterSection.Header -> it.customName ?: "H${it.volume}"
            is ChapterSection.Item -> it.chapter.id
        }
    }

    @Test
    fun noHeadersWithoutVolumesAndGroups() {
        assertTrue(sections(listOf(Item(1), Item(2))).all { it is ChapterSection.Item })
    }

    @Test
    fun volumeHeadersFollowVolumeChanges() {
        val volumes = listOf(Item(1, 0), Item(2, 1), Item(3, 1), Item(4, 2))
        assertEquals(listOf<Any>("H0", 1L, "H1", 2L, 3L, "H2", 4L), sections(volumes).labels())
    }

    @Test
    fun groupHeadersAppearOnlyWhenTheGroupChangesAndMayRepeat() {
        val items = listOf(
            Item(1, scanlator = "WebToon"), Item(2, scanlator = "WebToon"),
            Item(3, scanlator = "Official · WebToon"), Item(4, scanlator = "Saucerer"),
            Item(5, scanlator = "WebToon"),
        )
        assertEquals(
            listOf<Any>("WebToon", 1L, 2L, "Official · WebToon", 3L, "Saucerer", 4L, "WebToon", 5L),
            sections(items).labels(),
        )
    }

    // --- Kototoro ChaptersDeduplicationTest: mergeRepeated ------------------------------------------------------

    private fun chapter(
        id: Long,
        number: Float,
        title: String? = "Chapter ${number.toString().removeSuffix(".0")}",
        volume: Int = 0,
        scanlator: String? = null,
        branch: String? = scanlator,
        uploadDate: Long = 0L,
        url: String = "title/test/$id",
    ) = MangaChapter(
        id = id, title = title, number = number, volume = volume, url = url, scanlator = scanlator,
        uploadDate = uploadDate, branch = branch, source = source,
    )

    @Test
    fun repeatedChapterKeepsTheLatestUpload() {
        val merged = listOf(
            chapter(1, 1f, scanlator = "A", uploadDate = 1000L),
            chapter(2, 1f, title = "Chapter 1 - Special Ed", scanlator = "B", uploadDate = 2000L),
        ).mergeRepeated()
        assertEquals(listOf(2L), merged.map { it.id })
    }

    @Test
    fun sameNumberInDifferentVolumesIsNotRepeated() {
        val merged = listOf(chapter(1, 1f, volume = 1), chapter(2, 1f, volume = 2)).mergeRepeated()
        assertEquals(listOf(1L, 2L), merged.map { it.id })
    }

    @Test
    fun unnumberedChaptersMergeByTitle() {
        val merged = listOf(
            chapter(1, 0f, title = "Special Extra", uploadDate = 1000L),
            chapter(2, 0f, title = "Special Extra ", uploadDate = 2000L),
        ).mergeRepeated()
        assertEquals(listOf(2L), merged.map { it.id })
    }

    @Test
    fun downloadedCopyWinsWhenTheUploadDateIsEqual() {
        val merged = listOf(
            chapter(1, 2f, uploadDate = 1000L),
            chapter(2, 2f, uploadDate = 1000L, url = "file:///c/2"),
        ).mergeRepeated()
        assertEquals(listOf(2L), merged.map { it.id })
    }

    @Test
    fun specialsAndFractionsKeepTheListingOrder() {
        val list = listOf(
            chapter(1, 1f), chapter(2, 0f, title = "Summer Special"), chapter(3, 1.5f), chapter(4, 2f),
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), list.mergeRepeated().map { it.id })
    }

    // --- Futon wiring ---------------------------------------------------------------------------------------------

    private fun manga(chapters: List<MangaChapter>, source: MangaSource = this.source) = Manga(
        id = 1, title = "Test", altTitles = emptySet(), url = "/test", publicUrl = "https://example.com/test",
        rating = 0f, contentRating = null, coverUrl = null, tags = emptySet(), state = MangaState.ONGOING,
        authors = emptySet(), chapters = chapters, source = source,
    )

    @Test
    fun groupsBecomeOneOrderedListWithoutLosingChapters() {
        val chapters = (1..227).map {
            chapter(it.toLong(), it.toFloat(), scanlator = when (it) {
                223 -> "Asura Scans"
                227 -> "Saucerer"
                else -> "WebToon"
            })
        }
        val details = MangaDetails(manga(chapters.shuffled(java.util.Random(7))))
        assertTrue(details.isMerged)
        assertEquals(3, details.branchCount)
        assertEquals(setOf<String?>(null), details.chapters.keys)
        assertEquals(chapters.map { it.id }, details.chapterListForBranch("Saucerer").map { it.id })
        assertEquals(chapters.map { it.id }, details.filterChapters("Asura Scans").allChapters.map { it.id })
    }

    @Test
    fun theSettingOffKeepsTheGroups() {
        ChapterMergeSetting.isEnabled = false
        val a = chapter(1, 1f, scanlator = "A")
        val b = chapter(2, 2f, scanlator = "B")
        val details = MangaDetails(manga(listOf(a, b)))
        assertFalse(details.isMerged)
        assertEquals(setOf<String?>("A", "B"), details.chapters.keys)
        assertEquals(listOf(a), details.chapterListForBranch("A"))
    }

    @Test
    fun aSingleGroupIsNotMerged() {
        val details = MangaDetails(manga(listOf(chapter(1, 1f, scanlator = "A"), chapter(2, 2f, scanlator = "A"))))
        assertFalse(details.isMerged)
    }

    @Test
    fun repeatedUploadsShowOneRepresentativeAndStayInTheGroups() {
        val old = chapter(1, 5f, scanlator = "A", uploadDate = 100L)
        val new = chapter(2, 5f, scanlator = "B", uploadDate = 200L)
        val other = chapter(3, 6f, scanlator = "A")
        val details = MangaDetails(manga(listOf(old, new, other)))
        assertEquals(listOf(2L, 3L), details.chapterListForBranch(null).map { it.id })
        // The unfiltered details still hold everything.
        assertEquals(setOf(1L, 2L, 3L), details.allChapters.map { it.id }.toSet())
    }

    @Test
    fun theOpenedChapterIsFoundThroughItsRepresentative() {
        val old = chapter(1, 5f, scanlator = "A", uploadDate = 100L)
        val new = chapter(2, 5f, scanlator = "B", uploadDate = 200L)
        val list = listOf(chapter(0, 4f, scanlator = "A"), new)
        assertEquals(1, list.indexOfChapter(old))
        assertEquals(1, list.indexOfChapter(new))
        assertEquals(-1, list.indexOfChapter(chapter(9, 99f, scanlator = "A")))
    }

    @Test
    fun readingOrderUsesVolumesWhenTheSourceHasThem() {
        val chapters = listOf(
            chapter(1, 2f, volume = 2, scanlator = "A"), chapter(2, 1f, volume = 1, scanlator = "B"),
            chapter(3, 1f, volume = 2, scanlator = "A"), chapter(4, 2f, volume = 1, scanlator = "B"),
        )
        val ids = MangaDetails(manga(chapters)).chapterListForBranch(null).map { it.id }
        assertEquals(listOf(2L, 4L, 3L, 1L), ids)
    }

    @Test
    fun readerStartsAtTheFirstChapterAndTheClickedChapterSurvives() {
        val chapters = listOf(chapter(2, 2f, scanlator = "A"), chapter(1, 1f, scanlator = "B"), chapter(3, 3f, scanlator = "A"))
        val original = manga(chapters)
        assertEquals(1L, ReaderState(original, "A").chapterId)
        val projected = MangaDetails(original).filterChapters("A")
        assertEquals(listOf(1L, 2L, 3L), projected.allChapters.map { it.id })
        assertEquals(chapters[0].url, projected.allChapters[1].url)
    }

    @Test
    fun downloadFlagsAndReadProgressCrossGroupBoundaries() {
        val chapters = listOf(chapter(1, 1f, scanlator = "A"), chapter(2, 2f, scanlator = "B"), chapter(3, 3f, scanlator = "A"))
        val local = chapters[1].copy(source = LocalMangaSource, url = "file:///chapter2")
        val details = MangaDetails(manga(chapters)).copy(
            localManga = LocalManga(manga(listOf(local), LocalMangaSource), File("/test.cbz")),
        )
        val items = details.mapChapters(
            currentChapterId = 2L, newCount = 0, branch = null, bookmarks = emptyList(),
            isGrid = false, isDownloadedOnly = false,
        )
        assertEquals(listOf(1L, 2L, 3L), items.map { it.chapter.id })
        assertEquals(listOf(false, true, true), items.map { it.isUnread })
        assertTrue(items[1].isCurrent)
        assertTrue(items[1].isDownloaded)
        assertFalse(items[0].isDownloaded)
    }

    @Test
    fun theCurrentChapterIsTheRepresentativeOfTheOneInTheHistory() {
        val old = chapter(1, 5f, scanlator = "A", uploadDate = 100L)
        val new = chapter(2, 5f, scanlator = "B", uploadDate = 200L)
        val before = chapter(0, 4f, scanlator = "A")
        val after = chapter(3, 6f, scanlator = "A")
        val details = MangaDetails(manga(listOf(before, old, new, after)))
        val items = details.mapChapters(
            currentChapterId = old.id, newCount = 0, branch = null, bookmarks = emptyList(),
            isGrid = false, isDownloadedOnly = false,
        )
        assertEquals(listOf(0L, 2L, 3L), items.map { it.chapter.id })
        assertEquals(listOf(false, true, true), items.map { it.isUnread })
        assertEquals(2L, items.single { it.isCurrent }.chapter.id)
    }

    @Test
    fun largeListsAreMergedWithoutLosingChapters() {
        val chapters = (1..10000).map { chapter(it.toLong(), it.toFloat(), scanlator = if (it % 2 == 0) "A" else "B") }
        val details = MangaDetails(manga(chapters))
        assertTrue(details.isMerged)
        assertEquals(10000, details.chapterListForBranch(null).size)
    }
}
