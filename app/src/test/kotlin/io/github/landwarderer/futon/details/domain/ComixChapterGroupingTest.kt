package io.github.landwarderer.futon.details.domain

import io.github.landwarderer.futon.core.model.AnonymousMangaSource
import io.github.landwarderer.futon.core.model.LocalMangaSource
import io.github.landwarderer.futon.details.data.MangaDetails
import io.github.landwarderer.futon.details.ui.mapChapters
import io.github.landwarderer.futon.local.domain.model.LocalManga
import io.github.landwarderer.futon.reader.data.filterChapters
import io.github.landwarderer.futon.reader.ui.ReaderState
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ComixChapterGroupingTest {
    private val source = AnonymousMangaSource("MIHON_${ComixChapterGrouping.SOURCE_ID}", "Comix")

    @Test
    fun distinct227ChaptersHaveOneCompleteGroupRegardlessOfOldSelection() {
        val chapters = (1..227).map {
            chapter(it.toLong(), it.toFloat(), when (it) {
                223 -> "Asura Scans"
                227 -> "Saucerer"
                else -> "WebToon"
            })
        }
        val details = MangaDetails(manga(chapters))
        assertTrue(details.hasCombinedComixChapters)
        assertEquals(setOf<String?>(null), details.chapters.keys)
        assertEquals(227, details.chapters.getValue(null).size)
        for (selection in listOf(null, "Asura Scans", "Saucerer", "WebToon")) {
            assertEquals(chapters, details.chapterListForBranch(selection))
            assertEquals(chapters, details.filterChapters(selection).allChapters)
        }
    }

    @Test
    fun duplicatesKeepGroupsAndBothOriginalUploads() {
        val a = chapter(1, 1f, "Asura Scans")
        val b = chapter(2, 1f, "WebToon")
        val details = MangaDetails(manga(listOf(a, b)))
        assertFalse(details.hasCombinedComixChapters)
        assertEquals(2, details.chapters.size)
        assertEquals(listOf(a), details.chapterListForBranch(a.branch))
        assertEquals(listOf(b), details.chapterListForBranch(b.branch))
        assertEquals(listOf(a, b), details.allChapters)
    }

    @Test
    fun extensionDedupResultIsCombinedWithoutRemovingOrChangingChapters() {
        val preferred = listOf(chapter(11, 1f, "Official"), chapter(22, 2f, "Other"))
        val result = MangaDetails(manga(preferred)).chapterListForBranch(null)
        assertEquals(preferred, result)
        assertSame(preferred[0], result[0])
        assertSame(preferred[1], result[1])
        assertEquals(preferred.map { it.url }, result.map { it.url })
    }

    @Test
    fun uncertainNumbersAndEditionAlternativesRemainGrouped() {
        val normal = chapter(1, 1f, "A")
        for (other in listOf(
            chapter(2, -1f, "B"), chapter(2, Float.NaN, "B"), chapter(2, Float.POSITIVE_INFINITY, "B"),
            chapter(2, 2f, "B").copy(title = "Special"),
            chapter(2, 2f, "B").copy(title = "Chapter 9"),
            chapter(2, 1f, "B").copy(title = "Chapter 1: Another edition"),
        )) {
            val details = MangaDetails(manga(listOf(normal, other)))
            assertFalse(details.hasCombinedComixChapters)
            assertEquals(2, details.allChapters.size)
        }
    }

    @Test
    fun decimalPrologueAndVolumeChaptersAreNotLost() {
        val chapters = listOf(chapter(1, 0f, "A").copy(title = "Chapter 0: Prologue"),
            chapter(2, 1.5f, "B"), chapter(3, 1f, "A"), chapter(4, 1f, "B").copy(volume = 2))
        val details = MangaDetails(manga(chapters))
        assertTrue(details.hasCombinedComixChapters)
        assertEquals(4, details.chapterListForBranch(null).size)
    }

    @Test
    fun sameDisplayNameAndOtherSourceIdsDoNotEnableCombining() {
        val chapters = listOf(chapter(1, 1f, "A"), chapter(2, 2f, "B"))
        for (identity in listOf("COMIX", "MIHON_42", "content:comix/reader")) {
            val details = MangaDetails(manga(chapters, AnonymousMangaSource(identity, "Comix")))
            assertFalse(details.hasCombinedComixChapters)
            assertEquals(2, details.chapters.size)
        }
    }

    @Test
    fun restoredSourceIdentityWorksWithoutExtensionRuntimeOrMemoCache() {
        val chapters = listOf(chapter(1, 1f, "A"), chapter(2, 2f, "B"))
        val restored = manga(chapters, AnonymousMangaSource(source.name))
        assertTrue(restored.hasCombinedComixChapters())
        assertEquals(chapters, restored.filterChapters("A").chapters)
    }

    @Test
    fun emptyAndLargeListsAreHandledWithoutDroppingChapters() {
        assertFalse(MangaDetails(manga(emptyList())).hasCombinedComixChapters)
        assertTrue(MangaDetails(manga(emptyList())).chapters.isEmpty())
        val chapters = (1..10000).map { chapter(it.toLong(), it.toFloat(), if (it % 2 == 0) "A" else "B") }
        val details = MangaDetails(manga(chapters))
        assertTrue(details.hasCombinedComixChapters)
        assertEquals(chapters, details.chapterListForBranch(null))
    }

    @Test
    fun readerStartsAtFirstNumberAndOriginalClickedChapterSurvives() {
        val chapters = listOf(chapter(2, 2f, "A"), chapter(1, 1f, "B"), chapter(3, 3f, "A"))
        val original = manga(chapters)
        assertEquals(1L, ReaderState(original, "A").chapterId)
        val projected = MangaDetails(original).filterChapters("A")
        assertEquals(listOf(1L, 2L, 3L), projected.allChapters.map { it.id })
        assertSame(chapters[0], projected.allChapters[1])
    }

    @Test
    fun downloadFlagsAndReadProgressCrossGroupBoundary() {
        val chapters = listOf(chapter(1, 1f, "A"), chapter(2, 2f, "B"), chapter(3, 3f, "A"))
        val local = chapters[1].copy(source = LocalMangaSource, url = "file:///chapter2")
        val details = MangaDetails(manga(chapters)).copy(
            localManga = LocalManga(manga(listOf(local), LocalMangaSource), File("/test.cbz")),
        )
        val items = details.mapChapters(2L, 0, "A", emptyList(), false, false)
        assertEquals(listOf(1L, 2L, 3L), items.map { it.chapter.id })
        assertFalse(items[0].isUnread)
        assertTrue(items[1].isCurrent)
        assertTrue(items[1].isDownloaded)
        assertTrue(items[2].isUnread)
        assertEquals("file:///chapter2", items[1].chapter.url)
        val downloaded = details.mapChapters(2L, 0, null, emptyList(), false, true)
        assertEquals(listOf(2L), downloaded.map { it.chapter.id })
    }

    @Test
    fun refreshReevaluatesDuplicatesWithoutPersistingSelection() {
        val first = MangaDetails(manga(listOf(chapter(1, 1f, "A"), chapter(2, 2f, "B"))))
        assertTrue(first.hasCombinedComixChapters)
        val refreshed = MangaDetails(manga(first.allChapters + chapter(3, 1f, "B")))
        assertFalse(refreshed.hasCombinedComixChapters)
        assertEquals(2, refreshed.chapters.size)
        assertEquals(3, refreshed.allChapters.size)
    }

    @Test
    fun matchingIdWithWrongRuntimePackageDoesNotEnableCombining() {
        val catalogue = org.mockito.Mockito.mock(eu.kanade.tachiyomi.source.CatalogueSource::class.java)
        org.mockito.Mockito.`when`(catalogue.id).thenReturn(ComixChapterGrouping.SOURCE_ID)
        val wrongPackage = io.github.landwarderer.futon.mihon.model.MihonMangaSource(catalogue, "other.extension")
        assertFalse(ComixChapterGrouping.shouldCombine(wrongPackage, listOf(chapter(1, 1f, "A"))))
    }

    @Test
    fun otherSourcesKeepReaderGroupSelectionAndFallback() {
        val other = AnonymousMangaSource("MIHON_42", "Other")
        val original = manga(listOf(chapter(1, 1f, "A"), chapter(2, 2f, "B")), other)
        assertEquals(2L, ReaderState(original, "B").chapterId)
        assertEquals(1L, ReaderState(original, "missing").chapterId)
        assertEquals(listOf(2L), original.filterChapters("B").chapters.orEmpty().map { it.id })
    }

    @Test
    fun smartResumeCanAdvanceAcrossScanlatorGroups() = kotlinx.coroutines.test.runTest {
        val details = MangaDetails(manga(listOf(chapter(1, 1f, "A"), chapter(2, 2f, "B"))))
        val history = io.github.landwarderer.futon.core.model.MangaHistory(
            createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH,
            chapterId = 1, page = 9, scroll = 0, percent = 1f, chaptersCount = 1,
        )
        val resolver = io.github.landwarderer.futon.reader.domain.SmartResumeResolver(
            ReaderState(history), history,
            io.github.landwarderer.futon.reader.domain.ChapterCompletionRule(
                io.github.landwarderer.futon.core.prefs.ChapterCompletionMode.PERCENTAGE, 90, 1,
            ), false,
        )
        var opened = 0L
        val state = resolver.resolve(details.chapterListForBranch("A").map { it.id }, 10, true) {
            opened = it
            true
        }
        assertEquals(2L, opened)
        assertEquals(ReaderState(2, 0, 0), state)
        assertEquals("title/test/2", details.allChapters[1].url)
    }

    private fun chapter(id: Long, number: Float, group: String) = MangaChapter(
        id = id, title = "Chapter ${number.toString().removeSuffix(".0")}", number = number,
        volume = 0, url = "title/test/$id", scanlator = group, uploadDate = 0L, branch = group, source = source,
    )

    private fun manga(chapters: List<MangaChapter>, source: MangaSource = this.source) = Manga(
        id = 1, title = "Test", altTitles = emptySet(), url = "/test", publicUrl = "https://example.com/test",
        rating = 0f, contentRating = null, coverUrl = null, tags = emptySet(), state = MangaState.ONGOING,
        authors = emptySet(), chapters = chapters, source = source,
    )
}
