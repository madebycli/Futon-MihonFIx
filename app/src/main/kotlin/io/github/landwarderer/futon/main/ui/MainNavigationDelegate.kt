package io.github.landwarderer.futon.main.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import androidx.core.view.allViews
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
import androidx.recyclerview.widget.RecyclerView
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
import io.github.landwarderer.futon.core.prefs.NavItem
import io.github.landwarderer.futon.core.ui.util.RecyclerViewOwner
import io.github.landwarderer.futon.core.ui.widgets.SlidingBottomNavigationView
import io.github.landwarderer.futon.core.util.ext.isLowRamDevice
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
import kotlin.math.abs
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

	private val lowRam = navBar.context.isLowRamDevice()
	private var pendingTab: Class<out Fragment>? = null
	private var pendingFromUser = false
	private var trimRequested = false
	private val applyPending = Runnable {
		if (!fragmentManager.isDestroyed && !fragmentManager.isStateSaved) {
			val target = pendingTab
			pendingTab = null
			// While a page fades, the tab chosen last always wins, also when it is the page that is fading out.
			if (target != null && (fadeAnimator != null || !target.isInstance(primaryFragment))) {
				showTab(target, pendingFromUser)
			}
			if (trimRequested) {
				trimRequested = false
				val hidden = fragmentManager.fragments.filter { it.isTab() && it.isHidden }
				if (hidden.isNotEmpty()) {
					val transaction = fragmentManager.beginTransaction()
					hidden.forEach { transaction.remove(it) }
					transaction.commitNow()
				}
			}
		}
	}

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
		if (pendingTab == null && primaryFragment?.let(::getItemId) == item.itemId) onNavigationItemReselected()
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
		val legacyItem = fragmentManager.findFragmentByTag("primary")?.let(::getItemId)
		syncTabLifecycles()
		val fragment = primaryFragment
		if (fragment != null) {
			onFragmentChanged(fragment, fromUser = false)
			val itemId = getItemId(fragment)
			if (navBar.selectedItemId != itemId) {
				navBar.selectedItemId = itemId
			}
		} else {
			val itemId = legacyItem?.takeIf { navBar.menu.findItem(it)?.isVisible == true } ?: if (savedInstanceState == null) {
				firstItem()?.itemId ?: navBar.selectedItemId
			} else {
				navBar.selectedItemId
			}
			if (itemId != navBar.selectedItemId) navBar.selectedItemId = itemId else onNavigationItemSelected(itemId)
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
		if (pendingTab != null) return
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
	}

	fun setItemVisibility(@IdRes itemId: Int, isVisible: Boolean) {
		val item = navBar.menu.findItem(itemId) ?: return
		item.isVisible = isVisible
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
		if (fragmentManager.isStateSaved) return false
		queueTab(newFragment, fromUser = true)
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
			queueTab(fragmentClass, fromUser = false)
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

	private fun queueTab(fragmentClass: Class<out Fragment>, fromUser: Boolean) {
		pendingFromUser = fromUser || (pendingTab == fragmentClass && pendingFromUser)
		pendingTab = fragmentClass
		schedulePendingChanges()
	}

	fun schedulePendingChanges() {
		navBar.removeCallbacks(applyPending)
		if (pendingTab != null || trimRequested) navBar.post(applyPending)
	}

	/**
	 * Shows the tab of [fragmentClass], creating it on the first visit, and hides the tab that was shown.
	 *
	 * The page that is left fades out first, then the other one fades in, so the two are never visible together.
	 * The fade is done here and not with fragment transitions: these overlapped when the tabs were switched quickly,
	 * and a page fades as one layer, with its shadows (the floating Favourites tabs), instead of view by view. A new
	 * switch during a fade carries on from the page as it is, so nothing is left half transparent.
	 */
	private fun showTab(fragmentClass: Class<out Fragment>, fromUser: Boolean) {
		val generation = ++fadeGeneration
		val current = primaryFragment
		val currentView = current?.view
		if (current != null && fragmentClass.isInstance(current)) {
			// Back to the page that was fading out: bring it back.
			currentView?.let { fade(it, 1f, TAB_FADE_IN_MS) {} }
			return
		}
		if (current == null || currentView == null || !currentView.isAttachedToWindow) {
			fadeAnimator?.cancel()
			commitTab(fragmentClass, fromUser, fadeIn = false)
			return
		}
		// The tab stays alive while hidden: a fling or smooth scroll still running in it would go on moving the
		// app bar and the bottom bar, which the tabs share, and leave them stuck halfway on the new tab.
		currentView.allViews.filterIsInstance<RecyclerView>().forEach { list ->
			list.stopScroll()
			list.stopNestedScroll(ViewCompat.TYPE_TOUCH)
			list.stopNestedScroll(ViewCompat.TYPE_NON_TOUCH)
		}
		fade(currentView, 0f, TAB_FADE_OUT_MS) {
			if (generation != fadeGeneration) return@fade
			if (fragmentManager.isDestroyed || fragmentManager.isStateSaved) {
				// Left the screen meanwhile: switch when it is back (schedulePendingChanges).
				currentView.alpha = 1f
				pendingTab = fragmentClass
				pendingFromUser = fromUser
				return@fade
			}
			commitTab(fragmentClass, fromUser, fadeIn = true)
			// Hidden now; it has to be opaque again when it is shown next time.
			currentView.alpha = 1f
		}
	}

	private fun commitTab(fragmentClass: Class<out Fragment>, fromUser: Boolean, fadeIn: Boolean) {
		val tag = TAG_PRIMARY_PREFIX + fragmentClass.name
		val current = primaryFragment
		val existing = fragmentManager.findFragmentByTag(tag)
		val target = existing ?: instantiateFragment(fragmentClass).also {
			it.arguments = buildBundle(1) {
				putBoolean(AppRouter.KEY_IS_BOTTOMTAB, true)
			}
		}
		val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
		if (current != null && current !== target) {
			// STARTED, not RESUMED: a hidden tab keeps its content up to date but does not own the menu or the toolbar.
			transaction.hide(current).setMaxLifecycle(current, Lifecycle.State.STARTED)
		}
		if (lowRam) {
			// Retain the page being left for Back, and free older hidden pages on low-memory devices.
			fragmentManager.fragments.filter { it.isTab() && it.isHidden && it !== target }.forEach {
				transaction.remove(it)
			}
		}
		if (existing != null) {
			transaction.show(existing).setMaxLifecycle(existing, Lifecycle.State.RESUMED)
		} else {
			transaction.add(R.id.container, target, tag)
		}
		transaction.commitNow()
		val targetView = target.view
		if (fadeIn && targetView != null) {
			// Transparent before its first frame, then faded in.
			targetView.alpha = 0f
			fade(targetView, 1f, TAB_FADE_IN_MS) {}
		} else {
			targetView?.alpha = 1f
		}
		onFragmentChanged(target, fromUser = fromUser)
	}

	private var fadeGeneration = 0
	private var fadeAnimator: Animator? = null

	/** Fades [view] to [alpha] as one layer; the duration is for a full fade and shortened for a part of it. */
	private fun fade(view: View, alpha: Float, fullDuration: Long, onEnd: () -> Unit) {
		fadeAnimator?.cancel()
		val distance = abs(view.alpha - alpha)
		if (distance == 0f) {
			fadeAnimator = null
			onEnd()
			return
		}
		val layerType = view.layerType
		fadeAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, alpha).apply {
			duration = (fullDuration * distance).toLong()
			addListener(object : AnimatorListenerAdapter() {
				private var isCancelled = false

				override fun onAnimationStart(animation: Animator) {
					view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
				}

				override fun onAnimationCancel(animation: Animator) {
					isCancelled = true
				}

				override fun onAnimationEnd(animation: Animator) {
					view.setLayerType(layerType, null)
					if (fadeAnimator === animation) fadeAnimator = null
					if (!isCancelled) onEnd()
				}
			})
			start()
		}
	}

	private fun syncTabLifecycles() {
		val tabs = fragmentManager.fragments.filter { it.isTab() || it.tag == "primary" }
		if (tabs.isEmpty()) return
		val visible = tabs.lastOrNull { it.isTab() && !it.isHidden && navBar.menu.findItem(getItemId(it)) != null }
		val warm = if (lowRam) tabs.lastOrNull { it.isTab() && it.isHidden && navBar.menu.findItem(getItemId(it)) != null } else null
		val retainedTags = mutableSetOf<String?>()
		val transaction = fragmentManager.beginTransaction()
		for (tab in tabs.sortedByDescending { it === visible }) {
			if (!tab.isTab() || navBar.menu.findItem(getItemId(tab)) == null || !retainedTags.add(tab.tag) ||
				(lowRam && tab !== visible && tab !== warm)
			) {
				transaction.remove(tab)
			} else if (tab === visible) {
				transaction.show(tab).setMaxLifecycle(tab, Lifecycle.State.RESUMED)
			} else {
				transaction.hide(tab).setMaxLifecycle(tab, Lifecycle.State.STARTED)
			}
		}
		transaction.commitNow()
	}

	fun releaseHiddenTabs() {
		trimRequested = true
		schedulePendingChanges()
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
		settings.observe(AppSettings.KEY_TRACKER_ENABLED, AppSettings.KEY_SUGGESTIONS, AppSettings.KEY_NAV_LABELS)
			.onEach {
				setItemVisibility(R.id.nav_suggestions, settings.isSuggestionsEnabled)
				setItemVisibility(R.id.nav_feed, settings.isTrackerEnabled)
				setNavbarIsLabeled(settings.isNavLabelsVisible)
			}.launchIn(lifecycleOwner.lifecycleScope)
	}

	private fun firstItem(): MenuItem? {
		val menu = navBar.menu
		for (item in menu) {
			if (item.isVisible) return item
		}
		return null
	}

	private fun setNavbarIsLabeled(value: Boolean) {
		if (navBar is SlidingBottomNavigationView) {
			navBar.minimumHeight = navBar.resources.getDimensionPixelSize(
				if (value) {
					materialR.dimen.m3_bottom_nav_min_height
				} else {
					R.dimen.nav_bar_height_compact
				},
			)
		}
		navRailHeader?.buttonExpand?.isVisible = value
		if (!value) {
			setNavbarIsExpanded(false)
		}
		navBar.labelVisibilityMode = if (value) {
			NavigationBarView.LABEL_VISIBILITY_LABELED
		} else {
			NavigationBarView.LABEL_VISIBILITY_UNLABELED
		}
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

		private const val TAB_FADE_OUT_MS = 90L
		private const val TAB_FADE_IN_MS = 150L
	}
}
