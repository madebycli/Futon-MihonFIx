package io.github.landwarderer.futon.core.model

import eu.kanade.tachiyomi.source.CatalogueSource
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaParserSource

class SourceOriginTest {
    @Test
    fun builtInAndWrappedBuiltInSourcesHaveBuiltInOrigin() {
        val builtIn = MangaParserSource.entries.first()
        assertEquals(SourceOrigin.BUILT_IN, builtIn.sourceOrigin)
        assertEquals(SourceOrigin.BUILT_IN, MangaSourceInfo(builtIn, true, false).sourceOrigin)
    }

    @Test
    fun persistedMihonIdsRemainRecognizableWithoutChangingIdentity() {
        for (name in listOf("MIHON_12345", "mihon:12345")) {
            val source = AnonymousMangaSource(name, "Provider")
            assertEquals(SourceOrigin.MIHON_EXTENSION, source.sourceOrigin)
            assertEquals(name, source.name)
            assertEquals(source, MangaSourceInfo(source, true, false).mangaSource)
        }
    }

    @Test
    fun loadedMihonSourceHasExtensionOriginWithoutAlteringItsId() {
        val catalogue = mock(CatalogueSource::class.java)
        `when`(catalogue.id).thenReturn(12345L)
        val source = MihonMangaSource(catalogue, "org.example.extension")
        assertEquals(SourceOrigin.MIHON_EXTENSION, source.sourceOrigin)
        assertEquals("MIHON_12345", source.name)
        assertEquals(SourceOrigin.MIHON_EXTENSION, MangaSourceInfo(source, true, false).sourceOrigin)
    }

    @Test
    fun unrelatedUnknownAndLocalSourcesAreNotMislabelled() {
        assertNull(AnonymousMangaSource("unrelated", "Provider").sourceOrigin)
        assertNull(UnknownMangaSource.sourceOrigin)
        assertNull(LocalMangaSource.sourceOrigin)
    }
}
