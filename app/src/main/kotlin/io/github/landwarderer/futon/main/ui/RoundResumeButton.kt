// The look follows ContinueReadingFab of Kototoro (https://github.com/Kototoro-app/Kototoro, Apache License 2.0):
// app/src/main/kotlin/org/skepsun/kototoro/main/ui/compose/KototoroAppBottomChrome.kt (ContinueReadingFab,
// ResumeActionArtwork): a circle as large as the bar, 6 dp shadow, the cover cropped under a 38 % black scrim with a
// white read icon, or the icon on primaryContainer while there is no cover.
package io.github.landwarderer.futon.main.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.google.android.material.color.MaterialColors
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.util.ext.getThemeDrawable
import io.github.landwarderer.futon.image.ui.CoverImageView
import org.koitharu.kotatsu.parsers.model.Manga
import kotlin.math.roundToInt
import com.google.android.material.R as materialR

@SuppressLint("ViewConstructor")
class RoundResumeButton(context: Context) : FrameLayout(context) {

	private val density = resources.displayMetrics.density
	private val cover = CoverImageView(context)
	private val scrim = View(context)
	private val icon = ImageView(context)
	private var coverUrl: String? = null

	init {
		id = R.id.roundFab
		outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, view.width, view.height)
			}
		}
		clipToOutline = true
		elevation = 6 * density
		isClickable = true
		isFocusable = true
		contentDescription = context.getString(R.string._continue)
		tooltipText = contentDescription
		foreground = context.getThemeDrawable(androidx.appcompat.R.attr.selectableItemBackground)
		cover.scaleType = ImageView.ScaleType.CENTER_CROP
		cover.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
		scrim.setBackgroundColor(Color.argb((0.38f * 255).roundToInt(), 0, 0, 0))
		addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
		icon.setImageResource(R.drawable.ic_read)
		icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		addView(icon, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
		setManga(null)
	}

	/** Shows the cover of [manga], or the plain button while there is none. */
	fun setManga(manga: Manga?) {
		val url = manga?.coverUrl?.takeUnless { it.isBlank() }
		val hasCover = url != null
		cover.isVisible = hasCover
		scrim.isVisible = hasCover
		if (hasCover && url != coverUrl) {
			cover.setImageAsync(url, manga)
		}
		coverUrl = url
		setBackgroundColor(
			if (hasCover) Color.TRANSPARENT else MaterialColors.getColor(this, materialR.attr.colorPrimaryContainer),
		)
		ImageViewCompat.setImageTintList(
			icon,
			ColorStateList.valueOf(
				if (hasCover) Color.WHITE else MaterialColors.getColor(this, materialR.attr.colorOnPrimaryContainer),
			),
		)
	}
}
