package com.simplemobiletools.dialer.activities

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import androidx.viewpager.widget.ViewPager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.dialer.BuildConfig
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.adapters.ViewPagerAdapter
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_contacts.*
import kotlinx.android.synthetic.main.fragment_recents.*

class MainActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        setupTabColors()
        checkContactPermissions()
    }

    override fun onResume() {
        super.onResume()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, getFABIconColor())
        main_dialpad_button.apply {
            setImageDrawable(dialpadIcon)
            background.applyColorFilter(getAdjustedPrimaryColor())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        config.lastUsedViewPagerPage = viewpager.currentItem
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        updateMenuItemColors(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                handlePermission(PERMISSION_GET_ACCOUNTS) {
                    initFragments()
                }
            } else {
                initFragments()
            }
        }
    }

    private fun setupTabColors() {
        val lastUsedPage = config.lastUsedViewPagerPage
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
        viewpager.offscreenPageLimit = tabsList.size - 1
        viewpager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                main_tabs_holder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                invalidateOptionsMenu()
            }
        })

        viewpager.onGlobalLayout {
            refreshItems(ALL_TABS_MASK)
        }

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
            main_tabs_holder.addTab(tab, index, config.lastUsedViewPagerPage == index)
        }

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        main_tabs_holder.onGlobalLayout {
            Handler().postDelayed({
                main_tabs_holder.getTabAt(config.lastUsedViewPagerPage)?.select()
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
            else -> R.drawable.ic_clock_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, config.textColor)
    }

    fun refreshItems(refreshTabsMask: Int) {
        if (isDestroyed || isFinishing) {
            return
        }

        if (viewpager.adapter == null) {
            viewpager.adapter = ViewPagerAdapter(this)
            viewpager.currentItem = config.lastUsedViewPagerPage
        }

        if (refreshTabsMask and CONTACTS_TAB_MASK != 0) {
            SimpleContactsHelper(this).getAvailableContacts { contacts ->
                runOnUiThread {
                    contacts_fragment.refreshContacts(contacts)
                }
            }
        }

        if (refreshTabsMask and RECENTS_TAB_MASK != 0) {
            RecentsHelper(this).getRecentCalls { recents ->
                runOnUiThread {
                    recents_fragment.updateRecents(recents)
                }
            }
        }
    }

    private fun getAllFragments() = arrayListOf(contacts_fragment)

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
            FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons),
            FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons)
        )

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }
}
