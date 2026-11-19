package io.github.landwarderer.futon.explore.ui.model

import io.github.landwarderer.futon.core.model.MangaSourceInfo
import io.github.landwarderer.futon.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.util.longHashCode

data class MangaSourceItem(
	val source: MangaSourceInfo,
	val isGrid: Boolean,
	/** Package of the Mihon extension that has a newer version available; shown as a button in the list view. */
	val updatePackage: String? = null,
	val isUpdating: Boolean = false,
) : ListModel {

	val id: Long = source.name.longHashCode()

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is MangaSourceItem && other.source == source
	}
}
