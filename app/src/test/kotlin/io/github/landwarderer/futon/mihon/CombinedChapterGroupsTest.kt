package io.github.landwarderer.futon.mihon

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.details.data.MangaDetails
import io.github.landwarderer.futon.details.domain.ChapterSection
import io.github.landwarderer.futon.details.domain.withVolumeSections
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.model.toDomainContent
import io.github.landwarderer.futon.mihon.model.toManga
import io.github.landwarderer.futon.mihon.state.MihonChapterSnapshotStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

/** Cross-PR regression: use the real Mihon repository before projecting the chapter list. */
class CombinedChapterGroupsTest {
    @Test
    fun creditsStayInOneNumberedListAndHeadersOnlyAppearOnChanges() = runTest {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
        try {
            val numbers = (215..222).map(Int::toFloat) + listOf(222.5f, 223f, 223.9f, 224f)
            val input = numbers.map { number ->
                SChapter.create().apply {
                    url = "/chapter/$number"
                    name = "Chapter $number"
                    chapter_number = number
                    scanlator = when (number) {
                        222.5f -> "WebToon"
                        223f -> "Official · WebToon"
                        223.9f -> "Saucerer"
                        224f -> "WebToon"
                        else -> null
                    }
                }
            }
            val source = object : CatalogueSource {
                override val id = 7_654_322L
                override val name = "Combined test"
                override val lang = "en"
                override val supportsLatest = false
                override fun getFilterList() = FilterList()
                override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
                override suspend fun getMangaDetails(manga: SManga) = manga
                override suspend fun getChapterList(manga: SManga) = input.reversed()
                override suspend fun getPageList(chapter: SChapter) = emptyList<Page>()
            }
            val wrapper = MihonMangaSource(source, "test.combined")
            val repository = MihonMangaRepository(wrapper, mock(MemoryContentCache::class.java))
            val manga = SManga.create().apply {
                url = "/combined"
                title = "Combined"
                initialized = true
            }.toDomainContent(wrapper).toManga()
            val details = MangaDetails(repository.getDetails(manga))
            assertEquals(1, details.branchCount)
            assertFalse(details.isMerged)
            assertTrue(details.allChapters.all { it.branch == null })
            val visible = details.chapterListForBranch(null)
            assertEquals(numbers, visible.map { it.number })
            assertEquals(numbers, details.filterChapters(null).allChapters.map { it.number })
            val rows: List<Any> = withVolumeSections(visible, { it.volume }, { it.scanlator }).map {
                when (it) {
                    is ChapterSection.Header -> it.customName ?: "volume:${it.volume}"
                    is ChapterSection.Item -> it.chapter.number
                }
            }
            assertEquals(
                listOf<Any>("volume:0") + (215..222).map { it.toFloat() } +
                    listOf("WebToon", 222.5f, "Official · WebToon", 223f, "Saucerer", 223.9f, "WebToon", 224f),
                rows,
            )
        } finally {
            MihonChapterSnapshotStore.clearForTests()
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }
}
