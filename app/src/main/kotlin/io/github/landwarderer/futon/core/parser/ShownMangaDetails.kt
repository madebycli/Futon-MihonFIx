package io.github.landwarderer.futon.core.parser

import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The details (with chapters) the details screen shows right now, in memory only. A dialog opened from that screen,
 * such as the download dialog, works with the same chapters at once instead of waiting for a slow source to load them
 * again; manga passed between screens carry no chapters.
 */
@Singleton
class ShownMangaDetails @Inject constructor() {

	private val entries = object : LinkedHashMap<Long, Manga>(MAX_ENTRIES, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Manga>?) = size > MAX_ENTRIES
	}

	fun put(manga: Manga) {
		if (manga.chapters.isNullOrEmpty()) return
		synchronized(entries) { entries[manga.id] = manga }
	}

	fun get(mangaId: Long): Manga? = synchronized(entries) { entries[mangaId] }

	private companion object {
		const val MAX_ENTRIES = 8
	}
}
