package io.github.landwarderer.futon.core.model

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
    fun unrelatedUnknownAndLocalSourcesAreNotMislabelled() {
        assertNull(AnonymousMangaSource("unrelated", "Provider").sourceOrigin)
        assertNull(UnknownMangaSource.sourceOrigin)
        assertNull(LocalMangaSource.sourceOrigin)
    }
}
