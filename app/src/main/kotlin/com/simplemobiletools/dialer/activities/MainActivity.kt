package com.simplemobiletools.dialer.activities

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.viewpager.widget.ViewPager
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.dialer.BuildConfig
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.adapters.ViewPagerAdapter
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.fragments.MyViewPagerFragment
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.helpers.tabsList
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_contacts.*
import kotlinx.android.synthetic.main.fragment_favorites.*
import kotlinx.android.synthetic.main.fragment_recents.*
import java.util.*

class MainActivity : SimpleActivity() {
    private var isSearchOpen = false
    private var searchMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupTabColors()

        if (isDefaultDialer()) {
            checkContactPermissions()
        } else {
            launchSetDefaultDialerIntent()
        }
    }

    override fun onResume() {
        super.onResume()
        val adjustedPrimaryColor = getAdjustedPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, adjustedPrimaryColor.getContrastColor())
        main_dialpad_button.apply {
            setImageDrawable(dialpadIcon)
            background.applyColorFilter(adjustedPrimaryColor)
        }

        main_tabs_holder.setBackgroundColor(config.backgroundColor)

        getInactiveTabIndexes(viewpager.currentItem).forEach {
            main_tabs_holder.getTabAt(it)?.icon?.applyColorFilter(config.textColor)
        }

        main_tabs_holder.setSelectedTabIndicatorColor(adjustedPrimaryColor)
        main_tabs_holder.getTabAt(viewpager.currentItem)?.icon?.applyColorFilter(adjustedPrimaryColor)
        getAllFragments().forEach {
            it?.setupColors(config.textColor, config.primaryColor, getAdjustedPrimaryColor())
        }

        if (!isSearchOpen) {
            refreshItems()
        }

        checkShortcuts()
        Handler().postDelayed({
            recents_fragment?.refreshItems()
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        config.lastUsedViewPagerPage = viewpager.currentItem
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.apply {
            findItem(R.id.clear_call_history).isVisible = getCurrentFragment() == recents_fragment

            setupSearch(this)
            updateMenuItemColors(this)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear_call_history -> clearCallHistory()
            R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
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
                getCurrentFragment()?.onSearchClosed()
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
        val lastUsedPage = getDefaultTab()
        main_tabs_holder.apply {
            background = ColorDrawable(config.backgroundColor)
            setSelectedTabIndicatorColor(getAdjustedPrimaryColor())
            getTabAt(lastUsedPage)?.select()
            getTabAt(lastUsedPage)?.icon?.applyColorFilter(getAdjustedPrimaryColor())

            getInactiveTabIndexes(lastUsedPage).forEach {
                getTabAt(it)?.icon?.applyColorFilter(config.textColor)
            }
        }
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until tabsList.size).filter { it != activeIndex }

    private fun initFragments() {
        viewpager.offscreenPageLimit = 2
        viewpager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                searchMenuItem?.collapseActionView()
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

        main_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                it.icon?.applyColorFilter(config.textColor)
            },
            tabSelectedAction = {
                viewpager.currentItem = it.position
                it.icon?.applyColorFilter(getAdjustedPrimaryColor())
            }
        )

        main_tabs_holder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            val tab = main_tabs_holder.newTab().setIcon(getTabIcon(index))
            main_tabs_holder.addTab(tab, index, getDefaultTab() == index)
        }

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        main_tabs_holder.onGlobalLayout {
            Handler().postDelayed({
                main_tabs_holder.getTabAt(getDefaultTab())?.select()
                invalidateOptionsMenu()
            }, 100L)
        }

        main_dialpad_button.setOnClickListener {
            Intent(applicationContext, DialpadActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_person_vector
            1 -> R.drawable.ic_star_on_vector
            else -> R.drawable.ic_clock_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, config.textColor)
    }

    private fun refreshItems() {
        if (isDestroyed || isFinishing) {
            return
        }

        if (viewpager.adapter == null) {
            viewpager.adapter = ViewPagerAdapter(this)
            viewpager.currentItem = getDefaultTab()
            viewpager.onGlobalLayout {
                refreshFragments()
            }
        } else {
            refreshFragments()
        }
    }

    private fun refreshFragments() {
        contacts_fragment?.refreshItems()
        favorites_fragment?.refreshItems()
        recents_fragment?.refreshItems()
    }

    private fun getAllFragments() = arrayListOf(contacts_fragment, favorites_fragment, recents_fragment).toMutableList() as ArrayList<MyViewPagerFragment?>

    private fun getCurrentFragment(): MyViewPagerFragment? = when (viewpager.currentItem) {
        0 -> contacts_fragment
        1 -> favorites_fragment
        else -> recents_fragment
    }

    private fun getDefaultTab(): Int {
        return when (config.defaultTab) {
            TAB_LAST_USED -> config.lastUsedViewPagerPage
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> 1
            else -> 2
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
            FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons),
            FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }
}
