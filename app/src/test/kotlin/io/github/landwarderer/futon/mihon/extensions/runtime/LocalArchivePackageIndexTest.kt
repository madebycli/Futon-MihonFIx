// Adapted from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at f32242d649471627d8906a523511bc69217897eb:
// app/src/test/kotlin/org/skepsun/kototoro/extensions/runtime/LocalArchivePackageIndexTest.kt
package io.github.landwarderer.futon.mihon.extensions.runtime

import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalArchivePackageIndexTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun packageInfo(packageName: String): PackageInfo = PackageInfo().apply {
        this.packageName = packageName
    }

    private fun apk(name: String, content: String = name) = File(temp.root, "$name.apk").apply { writeText(content) }

    @Test
    fun eachArchiveIsParsedOnceAcrossRepeatedLookups() {
        val archives = listOf("a", "b", "c").map { apk(it) }
        var parseCount = 0
        val index = LocalArchivePackageIndex()

        repeat(5) {
            val entries = index.entries("mihon", archives) { file ->
                parseCount++
                packageInfo(file.nameWithoutExtension)
            }
            assertEquals(3, entries.size)
        }

        assertEquals("repeated lookups must reuse the parsed archives", 3, parseCount)
    }

    @Test
    fun aListOfExtensionsCostsOneParsePerArchiveInsteadOfOneWalkPerRow() {
        val managedApks = (1..60).map { apk("ext$it", "apk$it") }
        var parseCount = 0
        val index = LocalArchivePackageIndex()
        val parse: (File) -> PackageInfo = { file ->
            parseCount++
            packageInfo("pkg.${file.nameWithoutExtension}")
        }

        repeat(300) { row ->
            val entries = index.entries("mihon", managedApks, parse)
            val wanted = "pkg.ext${(row % 60) + 1}"
            assertNotNull(entries.firstOrNull { it.second.packageName == wanted })
        }

        assertEquals("300 rows over 60 archives must not re-parse the archives", 60, parseCount)
    }

    @Test
    fun unparsableArchivesAreSkippedButDoNotBreakTheIndex() {
        val valid = apk("valid")
        val broken = apk("broken")
        val index = LocalArchivePackageIndex()

        val entries = index.entries("mihon", listOf(valid, broken)) { file ->
            if (file.name == "broken.apk") null else packageInfo("pkg.valid")
        }

        assertEquals(1, entries.size)
        assertEquals("pkg.valid", entries.single().second.packageName)
        assertEquals(valid, entries.single().first)
    }

    @Test
    fun replacingAnArchiveReparsesTheDirectory() {
        val archives = listOf("a", "b").map { apk(it) }
        var parseCount = 0
        val index = LocalArchivePackageIndex()
        val parse: (File) -> PackageInfo = { file ->
            parseCount++
            packageInfo("pkg.${file.nameWithoutExtension}.${file.length()}")
        }

        index.entries("mihon", archives, parse)
        assertEquals(2, parseCount)

        archives.first().writeText("replaced-with-longer-content")
        archives.first().setLastModified(archives.first().lastModified() + 1_000)

        val entries = index.entries("mihon", archives, parse)

        assertEquals("an updated archive must invalidate the cached metadata", 4, parseCount)
        assertEquals(
            "pkg.a.${archives.first().length()}",
            entries.first { it.first == archives.first() }.second.packageName,
        )
    }

    @Test
    fun fingerprintIgnoresTheIterationOrderOfTheArchiveList() {
        val first = apk("a")
        val second = apk("b")

        assertEquals(
            LocalArchivePackageIndex.fingerprintOf(listOf(first, second)),
            LocalArchivePackageIndex.fingerprintOf(listOf(second, first)),
        )
    }

    @Test
    fun directCandidatesCoverBothManagedLayouts() {
        val dir = temp.root
        assertEquals(
            listOf(
                File(File(dir, "org.example.ext"), "org.example.ext.apk"),
                File(dir, "org.example.ext.apk"),
            ),
            LocalApkExtensionSupport.directApkCandidates(dir, "org.example.ext"),
        )
    }
}
