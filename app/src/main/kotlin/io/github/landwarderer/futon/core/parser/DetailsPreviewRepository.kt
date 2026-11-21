package io.github.landwarderer.futon.core.parser

import org.koitharu.kotatsu.parsers.model.Manga

/**
 * A repository that can load the details of a manga (title, description, tags) without its chapter list. Some
 * extensions read a long chapter list for minutes; the details screen shows these details meanwhile.
 */
interface DetailsPreviewRepository {

	/** The details without the chapter list, or null when they are not available that way. */
	suspend fun getDetailsPreview(manga: Manga): Manga?
}
