package io.github.landwarderer.futon.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.landwarderer.futon.core.db.entity.MangaSourceEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaSourcesTitleUpdateTest {
    private lateinit var database: MangaDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), MangaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun unchangedTitlesDoNotWriteIncludingNull() = runBlocking {
        val dao = database.getSourcesDao()
        dao.upsert(MangaSourceEntity(
            source = "MIHON_42", isEnabled = false, sortKey = 1, addedIn = 1,
            lastUsedAt = 0, isPinned = false, cfState = 0, title = null,
        ))
        assertEquals(0, dao.setTitle("MIHON_42", null))
        assertEquals(1, dao.setTitle("MIHON_42", "Name"))
        assertEquals(0, dao.setTitle("MIHON_42", "Name"))
        assertEquals(1, dao.setTitle("MIHON_42", null))
        assertEquals(0, dao.setTitle("unknown", "Name"))
        assertEquals(false, dao.findAll().single().isEnabled)
    }
}
