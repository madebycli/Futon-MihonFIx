package io.github.landwarderer.futon.core.prefs

/**
 * Which items of the main navigation show their label.
 *
 * One choice instead of the switches Kototoro has for this (labels on/off, labels always visible, labels below or
 * beside the icon): the combinations of those switches that make sense are exactly these four.
 */
enum class NavLabelMode {

	/** Icons only. */
	NONE,

	/** Every item shows its label below the icon. */
	ALL,

	/** Only the selected item shows its label, below its icon; the others show their icon only. */
	SELECTED,

	/** Only the selected item shows its label, beside its icon in a capsule (Kototoro's "labels at the right"). */
	SELECTED_BESIDE,
	;

	companion object {

		/** The stored choice, or, while there is none yet, the value of the legacy on/off switch for the labels. */
		fun read(stored: String?, legacyLabels: Boolean): NavLabelMode {
			if (stored != null) {
				entries.firstOrNull { it.name == stored }?.let { return it }
			}
			return if (legacyLabels) ALL else NONE
		}
	}
}
