package io.github.landwarderer.futon.core.parser

import android.content.Context
import eu.kanade.tachiyomi.source.CatalogueSource
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.model.AnonymousMangaSource
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import io.github.landwarderer.futon.mihon.MihonExtensionManager
import io.github.landwarderer.futon.mihon.MihonMangaRepository
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.state.MihonSnapshotPersistence
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AwaitingMihonRepositoryTest {

    private val name = "MIHON_12345"

    @Test
    fun aRestoredMihonSourceIsNoLongerAPlaceholderOnceItsExtensionIsLoaded() {
        val manager = mock(MihonExtensionManager::class.java)
        val factory = MangaRepository.Factory(
            context = mock(Context::class.java),
            localMangaRepository = mock(LocalMangaRepository::class.java),
            localNovelRepository = javax.inject.Provider { throw UnsupportedOperationException() },
            loaderContext = mock(MangaLoaderContext::class.java),
            contentCache = mock(MemoryContentCache::class.java),
            mirrorSwitcher = mock(MirrorSwitcher::class.java),
            mihonExtensionManager = manager,
            mihonSnapshotPersistence = mock(MihonSnapshotPersistence::class.java),
        )
        val source = AnonymousMangaSource(name, "Comix")

        // The extension scan has not produced the source yet: the placeholder stands in for it.
        val placeholder = factory.create(source)
        assertTrue(placeholder is AwaitingMihonMangaRepository)

        // Once the extension is available, callers get the real repository (settings treat the placeholder as empty).
        val catalogue = mock(CatalogueSource::class.java)
        `when`(catalogue.id).thenReturn(12345L)
        `when`(manager.getMihonMangaSourceByName(name)).thenReturn(MihonMangaSource(catalogue, "org.example.comix"))
        val resolved = factory.create(source)
        assertTrue(resolved is MihonMangaRepository)
        assertSame(resolved, factory.create(source))
    }
}
