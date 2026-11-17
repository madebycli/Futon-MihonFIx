// Ported from Kototoro (Kototoro-app/Kototoro, Apache-2.0):
// core-ui/src/commonMain/kotlin/org/skepsun/kototoro/core/ui/chapters/ChapterGrouping.kt
// as of madebycli/Kototoro 404e596c79e4170f5aa30ea0cccf286dc76b53e8.
package io.github.landwarderer.futon.details.domain

/*
 * Chapter volume and group-name sections, generic over the chapter model. The list stays one normal list;
 * a header is only inserted where the volume changes or a new group (scanlator) name starts.
 */

sealed interface ChapterSection<out T> {
    /** A volume header: [customName] (the chapter group) wins; [volume] <= 0 means an unknown volume. */
    data class Header(val volume: Int, val customName: String?) : ChapterSection<Nothing>

    data class Item<T>(val chapter: T) : ChapterSection<T>
}

/** A lone "Unknown volume" header says nothing; only group when some chapter has a volume or group name. */
fun <T> shouldShowVolumeHeaders(chapters: List<T>, volume: (T) -> Int, scanlator: (T) -> String?): Boolean =
    chapters.any { volume(it) > 0 || !scanlator(it).isNullOrBlank() }

/** Inserts a header whenever the volume changes or a new group name starts, in list order. */
fun <T> withVolumeSections(
    chapters: List<T>,
    volume: (T) -> Int,
    scanlator: (T) -> String?,
): List<ChapterSection<T>> {
    if (!shouldShowVolumeHeaders(chapters, volume, scanlator)) return chapters.map { ChapterSection.Item(it) }
    var previousVolume = -1
    var previousCustom: String? = null
    val result = ArrayList<ChapterSection<T>>((chapters.size * 1.4).toInt())
    for (chapter in chapters) {
        val custom = scanlator(chapter)?.takeIf { it.isNotBlank() }
        val chapterVolume = volume(chapter)
        if (chapterVolume != previousVolume || (custom != null && custom != previousCustom)) {
            result += ChapterSection.Header(chapterVolume, custom)
            previousVolume = chapterVolume
            previousCustom = custom
        }
        result += ChapterSection.Item(chapter)
    }
    return result
}
