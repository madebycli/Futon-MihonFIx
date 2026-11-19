package io.github.landwarderer.futon.main.ui

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.core.view.size
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.bookmarks.ui.AllBookmarksFragment
import io.github.landwarderer.futon.core.nav.AppRouter
import io.github.landwarderer.futon.core.prefs.AppMode
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.NavLabelMode
import io.github.landwarderer.futon.core.prefs.NavItem
import io.github.landwarderer.futon.core.ui.util.RecyclerViewOwner
import io.github.landwarderer.futon.core.ui.widgets.SlidingBottomNavigationView
import io.github.landwarderer.futon.core.util.ext.buildBundle
import io.github.landwarderer.futon.core.util.ext.setContentDescriptionAndTooltip
import io.github.landwarderer.futon.core.util.ext.smoothScrollToTop
import io.github.landwarderer.futon.databinding.NavigationRailFabBinding
import io.github.landwarderer.futon.explore.ui.ExploreFragment
import io.github.landwarderer.futon.favourites.ui.container.FavouritesContainerFragment
import io.github.landwarderer.futon.history.ui.HistoryListFragment
import io.github.landwarderer.futon.local.ui.LocalListFragment
import io.github.landwarderer.futon.novel.ui.local.NovelLocalListFragment
import io.github.landwarderer.futon.suggestions.ui.SuggestionsFragment
import io.github.landwarderer.futon.tracker.ui.feed.FeedFragment
import io.github.landwarderer.futon.tracker.ui.updates.UpdatesFragment
import java.util.LinkedList
import com.google.android.material.R as materialR

/**
 * One tag per tab. The tab fragments are created when first opened and then kept, only hidden, so that switching tabs
 * shows the previous content at once instead of building the screen and loading it again. This follows the way
 * Kototoro (Navigation 3 entries with their own ViewModelStore and saved state) and Komikku (Voyager TabNavigator with
 * a ScreenModel per tab and saveableState) keep a top-level tab alive.
 */
private const val TAG_PRIMARY_PREFIX = "primary:"

class MainNavigationDelegate(
	private val navBar: NavigationBarView,
	private val fragmentManager: FragmentManager,
	private val settings: AppSettings,
) : OnBackPressedCallback(false),
	NavigationBarView.OnItemSelectedListener,
	NavigationBarView.OnItemReselectedListener, View.OnClickListener {

	private val listeners = LinkedList<OnFragmentChangedListener>()
	val navRailHeader = (navBar as? NavigationRailView)?.headerView?.let {
		NavigationRailFabBinding.bind(it)
	}

	/** The tab that is shown: the one tab fragment that is not hidden. */
	val primaryFragment: Fragment?
		get() = fragmentManager.fragments.firstOrNull { it.isTab() && !it.isHidden }

	private fun Fragment.isTab() = tag?.startsWith(TAG_PRIMARY_PREFIX) == true

	init {
		navBar.setOnItemSelectedListener(this)
		navBar.setOnItemReselectedListener(this)
		navRailHeader?.run {
			root.updateLayoutParams<FrameLayout.LayoutParams> {
				gravity = Gravity.TOP or Gravity.CENTER
			}
			val horizontalPadding = (navBar as NavigationRailView).itemActiveIndicatorMarginHorizontal
			root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			buttonExpand.setOnClickListener(this@MainNavigationDelegate)
			buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
			railFab.isExtended = false
			railFab.isAnimationEnabled = false
		}
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		return if (onNavigationItemSelected(item.itemId)) {
			item.isChecked = true
			true
		} else {
			false
		}
	}

	override fun onNavigationItemReselected(item: MenuItem) {
		onNavigationItemReselected()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_expand -> {
				if (navBar is NavigationRailView) {
					setNavbarIsExpanded(!navBar.isExpanded)
				}
			}
		}
	}

	override fun handleOnBackPressed() {
		navBar.selectedItemId = firstItem()?.itemId ?: return
	}

	fun onCreate(lifecycleOwner: LifecycleOwner, savedInstanceState: Bundle?) {
		if (navBar.menu.isEmpty()) {
			createMenu(settings.mainNavItems, navBar.menu)
		}
		observeSettings(lifecycleOwner)
		syncTabLifecycles()
		notifyMenuStateChanged()
		val fragment = primaryFragment
		if (fragment != null) {
			onFragmentChanged(fragment, fromUser = false)
			val itemId = getItemId(fragment)
			if (navBar.selectedItemId != itemId) {
				navBar.selectedItemId = itemId
			}
		} else {
			val itemId = if (savedInstanceState == null) {
				firstItem()?.itemId ?: navBar.selectedItemId
			} else {
				navBar.selectedItemId
			}
			onNavigationItemSelected(itemId)
		}
	}

	fun observeTitle() = callbackFlow {
		val listener = OnFragmentChangedListener { f, _ ->
			trySendBlocking(getItemId(f))
		}
		addOnFragmentChangedListener(listener)
		awaitClose { removeOnFragmentChangedListener(listener) }
	}.map {
		navBar.menu.findItem(it)?.title
	}

	fun setCounter(item: NavItem, counter: Int) {
		setCounter(item.id, counter)
	}

	fun syncSelectedItem() {
		val fragment = primaryFragment ?: return
		onFragmentChanged(fragment, fromUser = false)
		val itemId = getItemId(fragment)
		if (navBar.selectedItemId != itemId) {
			navBar.selectedItemId = itemId
		}
	}

	private fun setCounter(@IdRes id: Int, counter: Int) {
		if (counter == 0) {
			navBar.getBadge(id)?.isVisible = false
		} else {
			val badge = navBar.getOrCreateBadge(id)
			if (counter < 0) {
				badge.clearNumber()
			} else {
				badge.number = counter
			}
			badge.isVisible = true
		}
		notifyMenuStateChanged()
	}

	/** The floating bar shows the sections of [navBar]; it redraws when they changed. */
	private fun notifyMenuStateChanged() {
		(navBar as? SlidingBottomNavigationView)?.notifyMenuStateChanged()
	}

	fun setItemVisibility(@IdRes itemId: Int, isVisible: Boolean) {
		val item = navBar.menu.findItem(itemId) ?: return
		item.isVisible = isVisible
		notifyMenuStateChanged()
		if (item.isChecked && !isVisible) {
			navBar.selectedItemId = firstItem()?.itemId ?: return
		}
	}

	fun addOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.add(listener)
	}

	fun removeOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.remove(listener)
	}

	private fun onNavigationItemSelected(@IdRes itemId: Int): Boolean {
		val newFragment = when (itemId) {
			R.id.nav_history -> HistoryListFragment::class.java
			R.id.nav_favorites -> FavouritesContainerFragment::class.java
			R.id.nav_explore -> ExploreFragment::class.java
			R.id.nav_feed -> FeedFragment::class.java
			R.id.nav_local -> if (settings.appMode == AppMode.NOVEL) NovelLocalListFragment::class.java else LocalListFragment::class.java
			R.id.nav_suggestions -> SuggestionsFragment::class.java
			R.id.nav_bookmarks -> AllBookmarksFragment::class.java
			R.id.nav_updated -> UpdatesFragment::class.java
			else -> return false
		}
		if (!setPrimaryFragment(newFragment)) {
			// probably already selected
			onNavigationItemReselected()
		}
		return true
	}

	fun reloadCurrentFragment() {
		val itemId = navBar.selectedItemId
		if (itemId == R.id.nav_local) {
			val fragmentClass = if (settings.appMode == AppMode.NOVEL) {
				NovelLocalListFragment::class.java
			} else {
				LocalListFragment::class.java
			}
			if (fragmentManager.isStateSaved || fragmentClass.isInstance(primaryFragment)) return
			showTab(fragmentClass, fromUser = false)
		}
	}

	private fun getItemId(fragment: Fragment) = when (fragment) {
		is HistoryListFragment -> R.id.nav_history
		is FavouritesContainerFragment -> R.id.nav_favorites
		is ExploreFragment -> R.id.nav_explore
		is FeedFragment -> R.id.nav_feed
		is LocalListFragment, is NovelLocalListFragment -> R.id.nav_local
		is SuggestionsFragment -> R.id.nav_suggestions
		is AllBookmarksFragment -> R.id.nav_bookmarks
		is UpdatesFragment -> R.id.nav_updated
		else -> 0
	}

	private fun setPrimaryFragment(fragmentClass: Class<out Fragment>): Boolean {
		if (fragmentManager.isStateSaved || fragmentClass.isInstance(primaryFragment)) {
			return false
		}
		showTab(fragmentClass, fromUser = true)
		return true
	}

	/** Shows the tab of [fragmentClass], creating it on the first visit, and hides the tab that was shown. */
	private fun showTab(fragmentClass: Class<out Fragment>, fromUser: Boolean) {
		val tag = TAG_PRIMARY_PREFIX + fragmentClass.name
		val current = primaryFragment
		val existing = fragmentManager.findFragmentByTag(tag)
		val target = existing ?: instantiateFragment(fragmentClass).also {
			it.arguments = buildBundle(1) {
				putBoolean(AppRouter.KEY_IS_BOTTOMTAB, true)
			}
			it.enterTransition = MaterialFadeThrough()
			it.exitTransition = MaterialFadeThrough()
		}
		val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
		if (current != null && current !== target) {
			// STARTED, not RESUMED: a hidden tab keeps its content up to date but does not own the menu or the toolbar.
			transaction.hide(current).setMaxLifecycle(current, Lifecycle.State.STARTED)
		}
		if (existing != null) {
			transaction.show(existing).setMaxLifecycle(existing, Lifecycle.State.RESUMED)
		} else {
			transaction.add(R.id.container, target, tag)
		}
		transaction
			.runOnCommit { onFragmentChanged(target, fromUser = fromUser) }
			.commit()
	}

	/**
	 * After the activity was recreated every restored tab fragment is brought back to RESUMED, hidden or not. Hidden
	 * tabs are held at STARTED again, as when they were hidden. Tabs whose section is no longer in the bar (the
	 * sections were changed in the settings, which recreates this screen) are removed instead of staying hidden.
	 */
	private fun syncTabLifecycles() {
		if (fragmentManager.isStateSaved) return
		val tabs = fragmentManager.fragments.filter { it.isTab() }
		val orphans = tabs.filter { navBar.menu.findItem(getItemId(it)) == null }
		if (orphans.isEmpty() && tabs.none { it.isHidden }) return
		val transaction = fragmentManager.beginTransaction()
		for (tab in tabs) {
			if (tab in orphans) {
				transaction.remove(tab)
			} else {
				transaction.setMaxLifecycle(tab, if (tab.isHidden) Lifecycle.State.STARTED else Lifecycle.State.RESUMED)
			}
		}
		transaction.commitNow()
	}

	/**
	 * Frees the hidden tabs when the system runs short of memory: they are rebuilt, and load again, when they are
	 * opened next. The shown tab stays.
	 */
	fun releaseHiddenTabs() {
		if (fragmentManager.isStateSaved) return
		val hidden = fragmentManager.fragments.filter { it.isTab() && it.isHidden }
		if (hidden.isEmpty()) return
		val transaction = fragmentManager.beginTransaction()
		hidden.forEach { transaction.remove(it) }
		transaction.commitAllowingStateLoss()
	}

	private fun onNavigationItemReselected() {
		val recyclerView = (primaryFragment as? RecyclerViewOwner)?.recyclerView ?: return
		recyclerView.smoothScrollToTop()
	}

	private fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		isEnabled = getItemId(fragment) != firstItem()?.itemId
		listeners.forEach { it.onFragmentChanged(fragment, fromUser) }
	}

	private fun createMenu(items: List<NavItem>, menu: Menu) {
		for (item in items) {
			menu.add(Menu.NONE, item.id, Menu.NONE, item.title)
				.setIcon(item.icon)
			if (menu.size >= navBar.maxItemCount) {
				break
			}
		}
	}

	private fun instantiateFragment(fragmentClass: Class<out Fragment>): Fragment {
		val classLoader = navBar.context.classLoader
		return fragmentManager.fragmentFactory.instantiate(classLoader, fragmentClass.name)
	}

	private fun observeSettings(lifecycleOwner: LifecycleOwner) {
		settings.observe(
			AppSettings.KEY_TRACKER_ENABLED,
			AppSettings.KEY_SUGGESTIONS,
			AppSettings.KEY_NAV_LABEL_MODE,
			AppSettings.KEY_NAV_CAPSULE,
		)
			.onEach {
				setItemVisibility(R.id.nav_suggestions, settings.isSuggestionsEnabled)
				setItemVisibility(R.id.nav_feed, settings.isTrackerEnabled)
				setNavbarLabels(settings.navLabelMode)
				navBar.isItemActiveIndicatorEnabled = settings.isNavCapsuleEnabled
			}.launchIn(lifecycleOwner.lifecycleScope)
	}

	private fun firstItem(): MenuItem? {
		val menu = navBar.menu
		for (item in menu) {
			if (item.isVisible) return item
		}
		return null
	}

	/**
	 * The labels of the docked bar and of the rail. The floating bar reads the same setting itself. "Beside the icon"
	 * is a look of the floating bar only; the docked bar and the rail show the label of the selected item below its
	 * icon then, as the docked NavigationBar of Kototoro ignores its indicator style too.
	 */
	private fun setNavbarLabels(mode: NavLabelMode) {
		val isLabeled = mode != NavLabelMode.NONE
		if (navBar is SlidingBottomNavigationView) {
			navBar.minimumHeight = navBar.resources.getDimensionPixelSize(
				if (isLabeled) {
					materialR.dimen.m3_bottom_nav_min_height
				} else {
					R.dimen.nav_bar_height_compact
				},
			)
		}
		navRailHeader?.buttonExpand?.isVisible = isLabeled
		if (!isLabeled) {
			setNavbarIsExpanded(false)
		}
		navBar.labelVisibilityMode = when (mode) {
			NavLabelMode.NONE -> NavigationBarView.LABEL_VISIBILITY_UNLABELED
			NavLabelMode.ALL -> NavigationBarView.LABEL_VISIBILITY_LABELED
			NavLabelMode.SELECTED, NavLabelMode.SELECTED_BESIDE -> NavigationBarView.LABEL_VISIBILITY_SELECTED
		}
		notifyMenuStateChanged()
	}

	private fun setNavbarIsExpanded(value: Boolean) {
		if (navBar !is NavigationRailView) {
			return
		}
		if (value) {
			navBar.expand()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.START
				}
				railFab.extend()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu_open)
				buttonExpand.setContentDescriptionAndTooltip(R.string.collapse)
				val horizontalPadding = navBar.itemActiveIndicatorExpandedMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		} else {
			navBar.collapse()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.CENTER
				}
				railFab.shrink()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu)
				buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
				val horizontalPadding = navBar.itemActiveIndicatorMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		}
	}

	fun interface OnFragmentChangedListener {

		fun onFragmentChanged(fragment: Fragment, fromUser: Boolean)
	}

	companion object {

		const val MAX_ITEM_COUNT = 6
	}
}
