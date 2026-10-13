package io.github.landwarderer.futon.details.domain

import io.github.landwarderer.futon.core.model.unwrap
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource

/** Pure presentation rule for the external Keiyoushi Comix source, including restored source IDs. */
object ComixChapterGrouping {
    const val SOURCE_ID = 7537715367149829912L
    private const val PACKAGE_NAME = "eu.kanade.tachiyomi.extension.en.comix"
    private val chapterTitle = Regex("^Chapter\\s+(\\d+(?:\\.\\d+)?)(?:\\s*:\\s*.*)?$")

    fun shouldCombine(source: MangaSource, chapters: List<MangaChapter>): Boolean {
        val identity = source.unwrap()
        if (identity.name != "MIHON_$SOURCE_ID") return false
        if (identity is MihonMangaSource && identity.pkgName != PACKAGE_NAME) return false
        if (chapters.isEmpty()) return false
        val numbers = HashSet<Pair<Int, Float>>(chapters.size)
        for (chapter in chapters) {
            val number = chapter.number
            // Futon can synthesize a fallback number for unnumbered extension chapters. Verify
            // against Comix's actual numbered title before hiding alternative-group controls.
            val suppliedNumber = chapterTitle.matchEntire(chapter.title.orEmpty())
                ?.groupValues?.get(1)?.toFloatOrNull()
            if (!number.isFinite() || number < 0 || suppliedNumber != number) return false
            if (!numbers.add(chapter.volume to number)) return false
        }
        return true
    }
}

fun Manga.hasCombinedComixChapters(): Boolean = ComixChapterGrouping.shouldCombine(source, chapters.orEmpty())

fun Manga.chapterListForBranch(branch: String?): List<MangaChapter> = if (hasCombinedComixChapters()) {
    chapters.orEmpty().sortedBy { it.number }
} else {
    getChapters(branch)
}
