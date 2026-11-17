package io.github.landwarderer.futon.core.db

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.explore.data.MangaSourcesRepository
import io.github.landwarderer.futon.novel.data.source.NovelParserSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** Exercises actual source reconciliation, not only the DAO insert conflict behavior. */
@HiltAndroidTest
class SourceAssimilationTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var db: MangaDatabase
    @Inject lateinit var settings: AppSettings
    @Inject lateinit var repository: MangaSourcesRepository

    @Test
    fun individuallyDisabledBuiltInsAndDisableAllSurviveRediscovery() = runBlocking {
        hilt.inject()
        settings.isAllSourcesEnabled = false
        repository.refreshDiscoveredSources()
        repository.disableAllSources()

        val manga = repository.allMangaSources.take(2)
        assertEquals(2, manga.size)
        val novel = NovelParserSource.entries.first()
        val sources = listOf(novel) + manga
        repository.setSourcesEnabled(sources, true)

        for ((index, source) in sources.withIndex()) {
            repository.setSourcesEnabled(listOf(source), false)
            repository.refreshDiscoveredSources()
            val states = db.getSourcesDao().findAll().associate { it.source to it.isEnabled }
            sources.take(index + 1).forEach { assertEquals(false, states.getValue(it.name)) }
            sources.drop(index + 1).forEach { assertEquals(true, states.getValue(it.name)) }
        }

        repository.disableAllSources()
        repository.refreshDiscoveredSources()
        assertEquals(emptyList<String>(), db.getSourcesDao().findAllEnabledNames())
    }
}
