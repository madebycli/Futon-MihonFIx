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
    fun discoveredSourcesAreEnabledByKind() {
        // Built-in manga sources follow the "all sources" preference.
        assertFalse(defaultSourceEnabled(isNovel = false, isMihon = false, allSourcesEnabled = false))
        assertTrue(defaultSourceEnabled(isNovel = false, isMihon = false, allSourcesEnabled = true))
        // A freshly installed Mihon extension and the novel sources are usable without a manual step.
        assertTrue(defaultSourceEnabled(isNovel = false, isMihon = true, allSourcesEnabled = false))
        assertTrue(defaultSourceEnabled(isNovel = true, isMihon = false, allSourcesEnabled = false))
    }
}
