// Adapted from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at f32242d649471627d8906a523511bc69217897eb:
// core-source/src/jvmTest/kotlin/org/skepsun/kototoro/core/source/MihonModelRulesTest.kt
package io.github.landwarderer.futon.mihon.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MihonModelRulesTest {

    private fun branches(vararg chapters: Pair<String?, Float>) =
        MihonModelRules.scanlatorBranches(chapters.toList(), { it.first }, { it.second })

    @Test
    fun partlyFilledCreditsStayOneList() {
        // Komga: some books credit a translator, others none.
        assertFalse(branches("Alice" to 1f, null to 2f, "" to 3f, "Bob" to 4f))
        assertFalse(branches(null to 1f, null to 2f))
        // A blank credit and a missing one are the same group.
        assertFalse(branches("" to 1f, null to 1f))
    }

    @Test
    fun scanlatorsBecomeBranchesWhenTheyCompeteForOneChapterNumber() {
        assertTrue(branches("Alice" to 1f, "Bob" to 1f, "Alice" to 2f))
        assertTrue(branches(null to 1f, "Bob" to 1f))
    }

    @Test
    fun differentGroupsForDifferentChaptersAreNotCompeting() {
        // The reference case of the chapter list: WebToon, an official group and another group on disjoint numbers.
        assertFalse(
            branches("WebToon" to 222f, "WebToon" to 222.5f, "Official · WebToon" to 223f, "Saucerer" to 223.9f),
        )
    }
}
