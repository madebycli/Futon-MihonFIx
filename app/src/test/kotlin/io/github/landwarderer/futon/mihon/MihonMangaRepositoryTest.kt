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
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.model.toDomainContent
import io.github.landwarderer.futon.mihon.model.toManga
import io.github.landwarderer.futon.mihon.state.MihonChapterSnapshotStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import coil3.request.CachePolicy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

private fun snapshotPath(chapter: SChapter?): String? {
    return chapter?.memo?.get("path")?.toString()?.trim('"')
}

class MihonMangaRepositoryTest {

    private val mainThreadTaskExecutor = object : TaskExecutor() {
        override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
        override fun postToMainThread(runnable: Runnable) = runnable.run()
        override fun isMainThread(): Boolean = true
    }

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(mainThreadTaskExecutor)
        Dispatchers.setMain(Dispatchers.Default)
        MihonChapterSnapshotStore.clearForTests()
    }

    @After
    fun tearDown() {
        MihonChapterSnapshotStore.clearForTests()
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun chapterSnapshotSurvivesRepositoryInstanceChange() = runTest {
        val chapter = chapterWithMetadata()
        val source = SnapshotCatalogueSource(SOURCE_ID, chapter)
        val sourceWrapperA = MihonMangaSource(source, "test.snapshot.a")
        val repositoryA = MihonMangaRepository(sourceWrapperA, memoryCache())

        val initialManga = SManga.create().apply {
            url = MANGA_URL
            title = "Snapshot test manga"
            initialized = true
        }.toDomainContent(sourceWrapperA).toManga()

        val details = repositoryA.getDetails(initialManga)
        val returnedChapter = requireNotNull(details.chapters).single()

        chapter.memo = buildJsonObject {
            put("path", "mutated-after-details")
        }

        val sourceWrapperB = MihonMangaSource(source, "test.snapshot.b")
        val repositoryB = MihonMangaRepository(sourceWrapperB, memoryCache())
        val pages = repositoryB.getPages(returnedChapter)

        assertEquals(1, pages.size)
        assertEquals("private/chapter/path", source.observedPath)
        assertTrue(source.receivedPageRequest)
    }

    @Test
    fun sourceIdsIsolateIdenticalChapterUrls() {
        val chapterA = chapterWithMetadata("source-a")
        val chapterB = chapterWithMetadata("source-b")

        MihonChapterSnapshotStore.put(SOURCE_ID, CHAPTER_URL, chapterA)
        MihonChapterSnapshotStore.put(SOURCE_ID + 1L, CHAPTER_URL, chapterB)

        assertEquals("source-a", snapshotPath(MihonChapterSnapshotStore.get(SOURCE_ID, CHAPTER_URL)))
        assertEquals("source-b", snapshotPath(MihonChapterSnapshotStore.get(SOURCE_ID + 1L, CHAPTER_URL)))
    }

    @Test
    fun returnedChapterSnapshotIsDefensiveCopy() {
        val original = chapterWithMetadata()
        MihonChapterSnapshotStore.put(SOURCE_ID, CHAPTER_URL, original)

        val returned = requireNotNull(MihonChapterSnapshotStore.get(SOURCE_ID, CHAPTER_URL))
        returned.name = "mutated"
        returned.memo = buildJsonObject {
            put("path", "mutated")
        }

        val reread = requireNotNull(MihonChapterSnapshotStore.get(SOURCE_ID, CHAPTER_URL))
        assertEquals("Original chapter", reread.name)
        assertEquals("private/chapter/path", snapshotPath(reread))
    }

    @Test
    fun complementaryMihonGroupsRemainSelectableAndMergeInReadingOrder() = runTest {
        val input = (1..10).map { number ->
            SChapter.create().apply {
                url = "/chapter/$number"
                name = "Chapter $number"
                chapter_number = number.toFloat()
                scanlator = if (number in 6..7) "Group B" else "Group A"
            }
        }
        val source = object : CatalogueSource {
            override val id = SOURCE_ID + 99
            override val name = "Complementary groups"
            override val lang = "en"
            override val supportsLatest = false
            override fun getFilterList() = FilterList()
            override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
            override suspend fun getMangaDetails(manga: SManga) = manga
            override suspend fun getChapterList(manga: SManga) = input.reversed()
            override suspend fun getPageList(chapter: SChapter) = emptyList<Page>()
        }
        val wrapper = MihonMangaSource(source, "test.complementary")
        val initial = SManga.create().apply {
            url = "/manga/complementary"
            title = "Complementary groups"
            initialized = true
        }.toDomainContent(wrapper).toManga()
        val original = MangaDetails(MihonMangaRepository(wrapper, memoryCache()).getDetails(initial))
        val merged = original.filterChapters(null)
        assertTrue(merged.isMerged)
        assertEquals((1..10).map { it.toFloat() }, merged.allChapters.map { it.number })
        val separate = merged.copy(mergeRepeatedChapters = false).filterChapters("Group B")
        assertEquals(listOf(6f, 7f), separate.allChapters.map { it.number })
        assertEquals(setOf("Group A", "Group B"), separate.chapters.keys)
        assertEquals(setOf("Group A", "Group B"), separate.identityChapters.mapNotNull { it.branch }.toSet())
        assertEquals((1..10).map { it.toFloat() },
            separate.copy(mergeRepeatedChapters = true).filterChapters(null).allChapters.map { it.number })
    }

    private fun countingSource(
        sourceId: Long,
        gate: CompletableDeferred<Unit>? = null,
        failFirstChapterLists: Int = 0,
    ) = object : CatalogueSource {
        val chapterListCalls = AtomicInteger()
        override val id = sourceId
        override val name = "Counting source"
        override val lang = "en"
        override val supportsLatest = false
        override fun getFilterList() = FilterList()
        override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
        override suspend fun getMangaDetails(manga: SManga) = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> {
            val call = chapterListCalls.incrementAndGet()
            gate?.await()
            if (call <= failFirstChapterLists) throw IllegalStateException("Chapter list is incomplete")
            return listOf(SChapter.create().apply {
                url = "/chapter/1"
                name = "Chapter 1"
                chapter_number = 1f
            })
        }
        override suspend fun getPageList(chapter: SChapter) = emptyList<Page>()
    }

    private fun detailsSeed(wrapper: MihonMangaSource) = SManga.create().apply {
        url = "/manga/counting"
        title = "Counting"
        initialized = true
    }.toDomainContent(wrapper).toManga()

    @Test
    fun aSecondDetailsLoadJoinsTheOneInProgress() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = countingSource(SOURCE_ID + 200, gate)
        val wrapper = MihonMangaSource(source, "test.counting")
        val repository = MihonMangaRepository(wrapper, memoryCache())
        val seed = detailsSeed(wrapper)
        val first = async(Dispatchers.Default) { repository.getDetails(seed, CachePolicy.WRITE_ONLY) }
        val second = async(Dispatchers.Default) { repository.getDetails(seed, CachePolicy.WRITE_ONLY) }
        // Real time: runTest would skip a delay, and the second caller needs a moment to reach the running load.
        withContext(Dispatchers.Default) {
            while (source.chapterListCalls.get() == 0) delay(10)
            delay(500)
        }
        gate.complete(Unit)
        assertEquals(1, first.await().chapters?.size)
        assertEquals(1, second.await().chapters?.size)
        assertEquals(1, source.chapterListCalls.get())
    }

    @Test
    fun anIncompleteChapterListIsReadAgain() = runTest {
        val source = countingSource(SOURCE_ID + 201, failFirstChapterLists = 2)
        val wrapper = MihonMangaSource(source, "test.retry")
        val details = MihonMangaRepository(wrapper, memoryCache()).getDetails(detailsSeed(wrapper))
        assertEquals(1, details.chapters?.size)
        assertEquals(3, source.chapterListCalls.get())
    }

    @Test
    fun transientFailuresAreRecognisedThroughTheirCauses() {
        assertTrue(RuntimeException("x", java.io.IOException("net")).isTransientDetailsFailure())
        assertTrue(IllegalStateException("Chapter list is incomplete").isTransientDetailsFailure())
        assertTrue(!IllegalArgumentException("bad url").isTransientDetailsFailure())
    }

    private fun memoryCache(): MemoryContentCache {
        return mock(MemoryContentCache::class.java)
    }

    private fun chapterWithMetadata(path: String = "private/chapter/path"): SChapter {
        return SChapter.create().apply {
            url = CHAPTER_URL
            name = "Original chapter"
            date_upload = 1_717_171_717L
            chapter_number = 12.5f
            scanlator = "Group"
            number = "12.5"
            volume = "3"
            scanlators = listOf("Group")
            note = "Extension note"
            memo = buildJsonObject {
                put("path", path)
            }
            locked = true
            read = false
            last_page_read = 4
        }
    }

    private class SnapshotCatalogueSource(
        override val id: Long,
        private val chapter: SChapter,
    ) : CatalogueSource {
        override val name: String = "Snapshot test source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false
        var observedPath: String? = null
        var receivedPageRequest: Boolean = false

        override fun getFilterList(): FilterList = FilterList()

        override suspend fun getPopularManga(page: Int): MangasPage {
            return MangasPage(emptyList(), false)
        }

        override suspend fun getMangaDetails(manga: SManga): SManga = manga

        override suspend fun getChapterList(manga: SManga): List<SChapter> = listOf(chapter)

        override suspend fun getPageList(chapter: SChapter): List<Page> {
            receivedPageRequest = true
            observedPath = snapshotPath(chapter)
            assertEquals("private/chapter/path", observedPath)
            return listOf(
                Page(
                    index = 0,
                    url = "/page/1",
                    imageUrl = "https://images.example.test/page-1.jpg",
                ),
            )
        }
    }

    private companion object {
        const val SOURCE_ID = 7_654_321L
        const val MANGA_URL = "/manga/snapshot-test"
        const val CHAPTER_URL = "/chapter/snapshot-test"
    }
}
