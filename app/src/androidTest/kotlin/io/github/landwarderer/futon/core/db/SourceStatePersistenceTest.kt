package io.github.landwarderer.futon.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.landwarderer.futon.core.db.entity.MangaSourceEntity
import io.github.landwarderer.futon.novel.data.source.NovelParserSource
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceStatePersistenceTest {
    @Test
    fun disabledSourceSurvivesDiscoveryAndIsExcludedBeforeRecentLimit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "source-state-persistence-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MangaDatabase::class.java, name).allowMainThreadQueries().build()
        val db = open()
        try {
            val dao = db.getSourcesDao()
            fun source(name: String, enabled: Boolean, lastUsedAt: Long) = MangaSourceEntity(
                source = name, isEnabled = enabled, sortKey = 0, addedIn = 1,
                lastUsedAt = lastUsedAt, isPinned = false, cfState = 0, title = name,
            )
            dao.insertIfAbsent(listOf(source("MIHON_1", false, 200L), source("MIHON_2", true, 100L)))
            dao.insertIfAbsent(listOf(source("MIHON_1", true, 300L)))
            assertEquals(listOf("MIHON_2"), dao.findLastUsedEnabled(1).map { it.source })
            assertEquals(listOf("MIHON_1"), dao.findLastUsed(1).map { it.source })
            assertEquals(false, dao.findAll().single { it.source == "MIHON_1" }.isEnabled)
            db.close()
            val reopened = open()
            try {
                val restored = reopened.getSourcesDao()
                assertEquals(false, restored.findAll().single { it.source == "MIHON_1" }.isEnabled)
                assertEquals(listOf("MIHON_2"), restored.findLastUsedEnabled(1).map { it.source })
            } finally {
                reopened.close()
            }
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }
    @Test
    fun disableAllPersistsForBuiltInMangaAndNovelSources() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "internal-source-disable-all-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MangaDatabase::class.java, name).allowMainThreadQueries().build()
        val db = open()
        try {
            val dao = db.getSourcesDao()
            val manga = MangaParserSource.entries.first().name
            val novel = NovelParserSource.entries.first().name
            fun source(id: String) = MangaSourceEntity(
                source = id, isEnabled = true, sortKey = 0, addedIn = 1,
                lastUsedAt = 0, isPinned = false, cfState = 0, title = id,
            )
            dao.insertIfAbsent(listOf(source(manga), source(novel)))
            dao.disableAllSources()
            dao.insertIfAbsent(listOf(source(manga), source(novel)))
            db.close()
            val reopened = open()
            try {
                val saved = reopened.getSourcesDao().findAll().associateBy { it.source }
                assertEquals(false, saved.getValue(manga).isEnabled)
                assertEquals(false, saved.getValue(novel).isEnabled)
            } finally {
                reopened.close()
            }
        } finally {
            if (db.isOpen) db.close()
            context.deleteDatabase(name)
        }
    }

}
