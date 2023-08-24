package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.getTextSize
import com.simplemobiletools.commons.helpers.SORT_BY_FIRST_NAME
import com.simplemobiletools.commons.helpers.SORT_BY_SURNAME
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.dialer.activities.MainActivity
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.adapters.RecentCallsAdapter
import com.simplemobiletools.dialer.databinding.FragmentLettersLayoutBinding
import com.simplemobiletools.dialer.databinding.FragmentRecentsBinding
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.Config

abstract class MyViewPagerFragment<BINDING : MyViewPagerFragment.InnerBinding>(context: Context, attributeSet: AttributeSet) :
    RelativeLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null
    protected lateinit var innerBinding: BINDING
    private lateinit var config: Config

    fun setupFragment(activity: SimpleActivity) {
        config = activity.config
        if (this.activity == null) {
            this.activity = activity

            setupFragment()
            setupColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), activity.getProperPrimaryColor())
        }
    }

    fun startNameWithSurnameChanged(startNameWithSurname: Boolean) {
        if (this !is RecentsFragment) {
            (innerBinding.fragmentList?.adapter as? ContactsAdapter)?.apply {
                config.sorting = if (startNameWithSurname) SORT_BY_SURNAME else SORT_BY_FIRST_NAME
                (this@MyViewPagerFragment.activity!! as MainActivity).refreshFragments()
            }
        }
    }

    fun finishActMode() {
        (innerBinding.fragmentList?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
        (innerBinding.recentsList?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    fun fontSizeChanged() {
        if (this is RecentsFragment) {
            (innerBinding.recentsList?.adapter as? RecentCallsAdapter)?.apply {
                fontSize = activity.getTextSize()
                notifyDataSetChanged()
            }
        } else {
            (innerBinding.fragmentList?.adapter as? ContactsAdapter)?.apply {
                fontSize = activity.getTextSize()
                notifyDataSetChanged()
            }
        }
    }

    abstract fun setupFragment()

    abstract fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int)

    abstract fun onSearchClosed()

    abstract fun onSearchQueryChanged(text: String)

    interface InnerBinding {
        val fragmentList: MyRecyclerView?
        val recentsList: MyRecyclerView?
    }

    class LettersInnerBinding(val binding: FragmentLettersLayoutBinding) : InnerBinding {
        override val fragmentList: MyRecyclerView = binding.fragmentList
        override val recentsList = null
    }

    class RecentsInnerBinding(val binding: FragmentRecentsBinding) : InnerBinding {
        override val fragmentList = null
        override val recentsList = binding.recentsList
    }
}
