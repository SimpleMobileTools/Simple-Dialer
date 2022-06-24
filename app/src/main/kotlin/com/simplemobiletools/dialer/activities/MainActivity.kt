package com.simplemobiletools.dialer.activities

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.provider.CallLog
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.dialer.BuildConfig
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.adapters.ViewPagerAdapter
import com.simplemobiletools.dialer.dialogs.ChangeSortingDialog
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.extensions.launchCreateNewContactIntent
import com.simplemobiletools.dialer.fragments.FavoritesFragment
import com.simplemobiletools.dialer.fragments.MyViewPagerFragment
import com.simplemobiletools.dialer.helpers.OPEN_DIAL_PAD_AT_LAUNCH
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.helpers.tabsList
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_contacts.*
import kotlinx.android.synthetic.main.fragment_favorites.*
import kotlinx.android.synthetic.main.fragment_recents.*

class MainActivity : SimpleActivity() {
    private var isSearchOpen = false
    private var launchedDialer = false
    private var searchMenuItem: MenuItem? = null
    private var storedShowTabs = 0
    private var searchQuery = ""
    var cachedContacts = ArrayList<SimpleContact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false

        if (isDefaultDialer()) {
            checkContactPermissions()

            if (!config.wasOverlaySnackbarConfirmed && !Settings.canDrawOverlays(this)) {
                val snackbar = Snackbar.make(main_holder, R.string.allow_displaying_over_other_apps, Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok) {
                    config.wasOverlaySnackbarConfirmed = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }

                snackbar.setBackgroundTint(getProperBackgroundColor().darkenColor())
                snackbar.setTextColor(getProperTextColor())
                snackbar.setActionTextColor(getProperTextColor())
                snackbar.show()
            }
        } else {
            launchSetDefaultDialerIntent()
        }

        setupTabs()
        SimpleContact.sorting = config.sorting
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, launchedDialer)
    }

    override fun onResume() {
        super.onResume()
        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        main_dialpad_button.apply {
            setImageDrawable(dialpadIcon)
            background.applyColorFilter(properPrimaryColor)
        }

        setupTabColors()

        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), getProperPrimaryColor(), getProperPrimaryColor())
        }

        if (!isSearchOpen) {
            if (storedShowTabs != config.showTabs) {
                System.exit(0)
                return
            }
            refreshItems(true)
        }

        checkShortcuts()
        Handler().postDelayed({
            recents_fragment?.refreshItems()
        }, 2000)
    }

    override fun onPause() {
        super.onPause()
        storedShowTabs = config.showTabs
        config.lastUsedViewPagerPage = view_pager.currentItem
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.apply {
            findItem(R.id.clear_call_history).isVisible = getCurrentFragment() == recents_fragment
            findItem(R.id.sort).isVisible = getCurrentFragment() != recents_fragment
            findItem(R.id.create_new_contact).isVisible = getCurrentFragment() == contacts_fragment

            setupSearch(this)
            updateMenuItemColors(this)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear_call_history -> clearCallHistory()
            R.id.create_new_contact -> launchCreateNewContactIntent()
            R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we dont really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            initFragments()
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        searchQuery = newText
                        getCurrentFragment()?.onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                isSearchOpen = true
                main_dialpad_button.beGone()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchClosed()
                }

                isSearchOpen = false
                main_dialpad_button.beVisible()
                return true
            }
        })
    }

    private fun clearCallHistory() {
        ConfirmationDialog(this, "", R.string.clear_history_confirmation) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    recents_fragment?.refreshItems()
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = resources.getDrawable(R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun setupTabColors() {
        val activeView = main_tabs_holder.getTabAt(view_pager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true)

        getInactiveTabIndexes(view_pager.currentItem).forEach { index ->
            val inactiveView = main_tabs_holder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false)
        }

        val bottomBarColor = getBottomTabsBackgroundColor()
        main_tabs_holder.setBackgroundColor(bottomBarColor)
        updateNavigationBarColor(bottomBarColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until tabsList.size).filter { it != activeIndex }

    private fun initFragments() {
        view_pager.offscreenPageLimit = 2
        view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager.SCROLL_STATE_SETTLING) {
                    closeSearch()
                }
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                main_tabs_holder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                invalidateOptionsMenu()
            }
        })

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        main_tabs_holder.onGlobalLayout {
            Handler().postDelayed({
                var wantedTab = getDefaultTab()

                // open the Recents tab if we got here by clicking a missed call notification
                if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                    wantedTab = main_tabs_holder.tabCount - 1

                    ensureBackgroundThread {
                        resetMissedCalls()
                    }
                }

                main_tabs_holder.getTabAt(wantedTab)?.select()
                invalidateOptionsMenu()
            }, 100L)
        }

        main_dialpad_button.setOnClickListener {
            launchDialpad()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun setupTabs() {
        view_pager.adapter = null
        main_tabs_holder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                main_tabs_holder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIcon(index))
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.text = getTabLabel(index)
                    main_tabs_holder.addTab(this)
                }
            }
        }

        main_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false)
            },
            tabSelectedAction = {
                view_pager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true)
            }
        )

        main_tabs_holder.beGoneIf(main_tabs_holder.tabCount == 1)
        storedShowTabs = config.showTabs
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_person_vector
            1 -> R.drawable.ic_star_vector
            else -> R.drawable.ic_clock_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.contacts_tab
            1 -> R.string.favorites_tab
            else -> R.string.call_history_tab
        }

        return resources.getString(stringId)
    }

    private fun refreshItems(openLastTab: Boolean = false) {
        if (isDestroyed || isFinishing) {
            return
        }

        if (view_pager.adapter == null) {
            view_pager.adapter = ViewPagerAdapter(this)
            view_pager.currentItem = if (openLastTab) config.lastUsedViewPagerPage else getDefaultTab()
            view_pager.onGlobalLayout {
                refreshFragments()
            }
        } else {
            refreshFragments()
        }
    }

    private fun launchDialpad() {
        Intent(applicationContext, DialpadActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun refreshFragments() {
        contacts_fragment?.refreshItems()
        favorites_fragment?.refreshItems()
        recents_fragment?.refreshItems()
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment?> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment?>()

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(contacts_fragment)
        }

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(favorites_fragment)
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(recents_fragment)
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment? = getAllFragments().getOrNull(view_pager.currentItem)

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        return when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < main_tabs_holder.tabCount) config.lastUsedViewPagerPage else 0
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> if (showTabsMask and TAB_CONTACTS > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_CALL_HISTORY > 0) {
                    if (showTabsMask and TAB_CONTACTS > 0) {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    // clear the missed calls count. Doesn't seem to always work, but having it can't hurt
    // found at https://android.googlesource.com/platform/packages/apps/Dialer/+/nougat-release/src/com/android/dialer/calllog/MissedCallNotifier.java#181
    private fun resetMissedCalls() {
        val values = ContentValues().apply {
            put(CallLog.Calls.NEW, 0)
            put(CallLog.Calls.IS_READ, 1)
        }

        val selection = "${CallLog.Calls.TYPE} = ?"
        val selectionArgs = arrayOf(CallLog.Calls.MISSED_TYPE.toString())

        try {
            val uri = CallLog.Calls.CONTENT_URI
            contentResolver.update(uri, values, selection, selectionArgs)
        } catch (e: Exception) {
        }
    }

    private fun launchSettings() {
        closeSearch()
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        closeSearch()
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            favorites_fragment?.refreshItems {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                }
            }

            contacts_fragment?.refreshItems {
                if (isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(searchQuery)
                }
            }
        }
    }

    private fun closeSearch() {
        if (isSearchOpen) {
            getAllFragments().forEach {
                it?.onSearchQueryChanged("")
            }
            searchMenuItem?.collapseActionView()
        }
    }

    fun cacheContacts(contacts: List<SimpleContact>) {
        try {
            cachedContacts.clear()
            cachedContacts.addAll(contacts)
        } catch (e: Exception) {
        }
    }
}
