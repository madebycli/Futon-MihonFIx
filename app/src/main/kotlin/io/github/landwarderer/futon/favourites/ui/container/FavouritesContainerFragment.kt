package io.github.landwarderer.futon.favourites.ui.container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import androidx.appcompat.view.ActionMode
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import io.github.landwarderer.futon.core.ui.util.TopOverlay
import androidx.core.view.ViewCompat
import io.github.landwarderer.futon.main.ui.owners.AppBarOwner
import com.google.android.material.appbar.AppBarLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.nav.router
import io.github.landwarderer.futon.core.ui.BaseFragment
import io.github.landwarderer.futon.core.ui.util.ActionModeListener
import io.github.landwarderer.futon.core.ui.util.RecyclerViewOwner
import io.github.landwarderer.futon.core.ui.util.ReversibleActionObserver
import io.github.landwarderer.futon.core.util.ext.addMenuProvider
import io.github.landwarderer.futon.core.util.ext.findCurrentPagerFragment
import io.github.landwarderer.futon.core.util.ext.observe
import io.github.landwarderer.futon.core.util.ext.observeEvent
import io.github.landwarderer.futon.core.util.ext.recyclerView
import io.github.landwarderer.futon.core.util.ext.setTabsEnabled
import io.github.landwarderer.futon.core.util.ext.setTextAndVisible
import io.github.landwarderer.futon.databinding.FragmentFavouritesContainerBinding
import io.github.landwarderer.futon.databinding.ItemEmptyStateBinding
import com.google.android.material.R as materialR

@AndroidEntryPoint
class FavouritesContainerFragment : BaseFragment<FragmentFavouritesContainerBinding>(),
	ActionModeListener,
	RecyclerViewOwner,
	ViewStub.OnInflateListener,
	View.OnClickListener {

	private val viewModel: FavouritesContainerViewModel by viewModels()

	override val recyclerView: RecyclerView?
		get() = (findCurrentFragment() as? RecyclerViewOwner)?.recyclerView

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentFavouritesContainerBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentFavouritesContainerBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val pagerAdapter = FavouritesContainerAdapter(this)
		binding.pager.adapter = pagerAdapter
		binding.pager.offscreenPageLimit = 1
		binding.pager.recyclerView?.isNestedScrollingEnabled = false
		TabLayoutMediator(
			binding.tabs,
			binding.pager,
			FavouritesTabConfigurationStrategy(pagerAdapter, viewModel, router),
		).attach()
		binding.stubEmpty.setOnInflateListener(this)
		actionModeDelegate.addListener(this)
		viewModel.categories.observe(viewLifecycleOwner, pagerAdapter)
		viewModel.isEmpty.observe(viewLifecycleOwner, ::onEmptyStateChanged)
		addMenuProvider(FavouritesContainerMenuProvider(router))
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.pager))
	}

	override fun onDestroyView() {
		bindStatusBarStop(false)
		actionModeDelegate.removeListener(this)
		super.onDestroyView()
	}

	private var defaultIndicator: Drawable? = null

	private val tabsLayoutListener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
		if (bottom - top != oldBottom - oldTop) updatePagerOverlay()
	}

	/** Whether the tabs are the floating bar now; a late layout callback must not bring its padding back. */
	private var isTabsFloating = false

	/** The pager starts at the top, behind the floating tabs; its lists start below them (TopOverlay). */
	private fun updatePagerOverlay() {
		if (!isTabsFloating) return
		val binding = viewBinding ?: return
		val tabs = binding.tabs
		val gap = (FLOATING_TABS_GAP_DP * resources.displayMetrics.density).toInt()
		val block = tabs.height + (tabs.layoutParams as ViewGroup.MarginLayoutParams).topMargin
		binding.pager.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = -block }
		TopOverlay.set(binding.pager, block + gap)
	}

	/**
	 * The floating tabs move with the content and the search bar, but stop just below the status bar instead of
	 * sliding under the clock and the status icons; the lists of the pager stop with them, so they stay right below
	 * the tabs (no overlap, and no gap when another category is opened).
	 */
	private val statusBarStop = AppBarLayout.OnOffsetChangedListener { appBar, verticalOffset ->
		val binding = viewBinding ?: return@OnOffsetChangedListener
		val statusBar = ViewCompat.getRootWindowInsets(binding.root)
			?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
		// The content is laid out from the app bar's top (its height reaches up behind it), so the tabs sit at
		// verticalOffset + the app bar's height; they may not go above the status bar.
		val naturalTop = verticalOffset + appBar.height
		val push = (statusBar - naturalTop).coerceAtLeast(0).toFloat()
		binding.tabs.translationY = push
		binding.pager.translationY = push
	}
	private var isStatusBarStopBound = false

	private fun bindStatusBarStop(bind: Boolean) {
		val appBar = (activity as? AppBarOwner)?.appBar ?: return
		if (bind && !isStatusBarStopBound) {
			appBar.addOnOffsetChangedListener(statusBarStop)
			isStatusBarStopBound = true
		} else if (!bind && isStatusBarStopBound) {
			appBar.removeOnOffsetChangedListener(statusBarStop)
			isStatusBarStopBound = false
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		applyFloatingTabs(TopOverlay.of(v))
		return insets
	}

	/**
	 * With the floating top bar the category tabs float too: a rounded bar with a shadow below the search bar, and the
	 * lists of the pager scroll behind it. Like the plain row they move with the content, so they leave and come back
	 * with the search bar exactly as before, and every category starts at the same height. Without it the tabs are
	 * the plain row above the pager, as before.
	 */
	private fun applyFloatingTabs(topOverlay: Int) {
		val binding = viewBinding ?: return
		val tabs = binding.tabs
		val density = resources.displayMetrics.density
		val isFloating = topOverlay > 0
		isTabsFloating = isFloating
		if (isFloating) {
			val margin = (FLOATING_TABS_MARGIN_DP * density).toInt()
			tabs.updateLayoutParams<ViewGroup.MarginLayoutParams> {
				topMargin = topOverlay + (FLOATING_TABS_GAP_DP * density).toInt()
				marginStart = margin
				marginEnd = margin
			}
			if (tabs.background !is MaterialShapeDrawable) {
				tabs.background = MaterialShapeDrawable(
					ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build(),
				).apply {
					fillColor = ColorStateList.valueOf(MaterialColors.getColor(tabs, materialR.attr.colorSurfaceContainer))
				}
				tabs.clipToOutline = true
			}
			tabs.elevation = FLOATING_TABS_ELEVATION_DP * density
			// The indicator line sits a little above the bottom, inside the rounded bar, instead of on its edge.
			if (defaultIndicator == null) {
				defaultIndicator = tabs.tabSelectedIndicator
				val inset = (FLOATING_TABS_INDICATOR_INSET_DP * density).toInt()
				val line = defaultIndicator?.constantState?.newDrawable(resources)?.mutate() ?: defaultIndicator
				tabs.setSelectedTabIndicator(InsetDrawable(line, 0, 0, 0, inset))
				tabs.setSelectedTabIndicatorHeight((line?.intrinsicHeight?.takeIf { it > 0 } ?: (3 * density).toInt()) + inset)
			}
			// Follows every size change of the tabs (they grow once the category names are there), so the lists of
			// the pager always start below them and never under them.
			tabs.removeOnLayoutChangeListener(tabsLayoutListener)
			tabs.addOnLayoutChangeListener(tabsLayoutListener)
			tabs.doOnLayout { updatePagerOverlay() }
			bindStatusBarStop(true)
		} else {
			tabs.updateLayoutParams<ViewGroup.MarginLayoutParams> {
				topMargin = 0
				marginStart = 0
				marginEnd = 0
			}
			tabs.background = null
			tabs.clipToOutline = false
			tabs.elevation = 0f
			tabs.translationY = 0f
			binding.pager.translationY = 0f
			tabs.removeOnLayoutChangeListener(tabsLayoutListener)
			bindStatusBarStop(false)
			defaultIndicator?.let {
				tabs.setSelectedTabIndicator(it)
				tabs.setSelectedTabIndicatorHeight(-1)
				defaultIndicator = null
			}
			binding.pager.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = 0 }
			if (binding.pager.getTag(R.id.tag_top_overlay) != null) {
				TopOverlay.set(binding.pager, 0)
			}
		}
	}

	override fun onActionModeStarted(mode: ActionMode) {
		viewBinding?.run {
			pager.isUserInputEnabled = false
			tabs.setTabsEnabled(false)
		}
	}

	override fun onActionModeFinished(mode: ActionMode) {
		viewBinding?.run {
			pager.isUserInputEnabled = true
			tabs.setTabsEnabled(true)
		}
	}

	override fun onInflate(stub: ViewStub?, inflated: View) {
		val stubBinding = ItemEmptyStateBinding.bind(inflated)
		stubBinding.icon.setImageAsync(R.drawable.ic_empty_favourites)
		stubBinding.textPrimary.setText(R.string.text_empty_holder_primary)
		stubBinding.textSecondary.setTextAndVisible(R.string.empty_favourite_categories)
		stubBinding.buttonRetry.setTextAndVisible(R.string.manage)
		stubBinding.buttonRetry.setOnClickListener(this)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_retry -> router.openFavoriteCategories()
		}
	}

	private fun onEmptyStateChanged(isEmpty: Boolean) {
		viewBinding?.run {
			pager.isGone = isEmpty
			tabs.isGone = isEmpty
			stubEmpty.isVisible = isEmpty
		}
	}

	private fun findCurrentFragment(): Fragment? {
		return childFragmentManager.findCurrentPagerFragment(
			viewBinding?.pager ?: return null,
		)
	}

	private companion object {

		const val FLOATING_TABS_MARGIN_DP = 12
		const val FLOATING_TABS_GAP_DP = 4
		const val FLOATING_TABS_ELEVATION_DP = 6
		const val FLOATING_TABS_INDICATOR_INSET_DP = 6
	}
}
