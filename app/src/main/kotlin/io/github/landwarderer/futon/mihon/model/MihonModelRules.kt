// Ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0) at f32242d649471627d8906a523511bc69217897eb
// (commit 0e813220 "keep scanlator credits from splitting chapter lists into branches"):
// core-source/src/commonMain/kotlin/org/skepsun/kototoro/core/source/MihonModelRules.kt
package io.github.landwarderer.futon.mihon.model

/** Source-model rules that are independent of Android and of the extension runtime. */
object MihonModelRules {

    /**
     * Mihon/Aniyomi's scanlator is a credit, not a translation branch. Groups become branches only when they offer
     * competing versions of one chapter number; otherwise every chapter stays on one list, as Mihon shows it
     * (Komga credits per-book translators, so a series with partly filled metadata would otherwise split).
     */
    fun <T> scanlatorBranches(chapters: List<T>, scanlator: (T) -> String?, number: (T) -> Float): Boolean {
        val owners = HashMap<Float, String?>()
        for (chapter in chapters) {
            val group = scanlator(chapter)?.takeIf(String::isNotBlank)
            val key = number(chapter)
            if (key !in owners) owners[key] = group else if (owners[key] != group) return true
        }
        return false
    }
}
