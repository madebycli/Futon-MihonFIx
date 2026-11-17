package io.github.landwarderer.futon.explore.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDiscoveryPolicyTest {
    @Test
    fun defaultMangaSourcesAreSeededOnlyForAnUninitializedEmptyDatabase() {
        assertTrue(shouldSeedDefaultMangaSources(isDatabaseEmpty = true, sourcesVersion = 0))
        assertFalse(shouldSeedDefaultMangaSources(isDatabaseEmpty = false, sourcesVersion = 0))
        assertFalse(shouldSeedDefaultMangaSources(isDatabaseEmpty = true, sourcesVersion = 1))
        assertFalse(shouldSeedDefaultMangaSources(isDatabaseEmpty = false, sourcesVersion = 1))
    }

    @Test
    fun mihonDiscoveryRespectsTheAllSourcesPreference() {
        assertFalse(defaultSourceEnabled(isNovel = false, allSourcesEnabled = false))
        assertTrue(defaultSourceEnabled(isNovel = false, allSourcesEnabled = true))
        assertTrue(defaultSourceEnabled(isNovel = true, allSourcesEnabled = false))
    }
}
