package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.getColorStateList
import com.simplemobiletools.commons.extensions.getContrastColor
import com.simplemobiletools.commons.extensions.underlineText
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.Config
import kotlinx.android.synthetic.main.fragment_letters_layout.view.*
import java.util.*
import kotlin.collections.ArrayList

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : CoordinatorLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null
    protected var allContacts = ArrayList<SimpleContact>()

    private var lastHashCode = 0
    private lateinit var config: Config

    fun setupFragment(activity: SimpleActivity) {
        config = activity.config
        if (this.activity == null) {
            this.activity = activity

            fragment_placeholder_2?.underlineText()
        }
    }

    fun finishActMode() {
        (fragment_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    fun refreshContacts(contacts: ArrayList<SimpleContact>) {
        ContactsAdapter(activity as SimpleActivity, contacts, fragment_list, null) {

        }.apply {
            fragment_list.adapter = this
        }

        letter_fastscroller.textColor = config.textColor.getColorStateList()
        setupLetterFastscroller(contacts)
        letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
        letter_fastscroller_thumb.textColor = config.primaryColor.getContrastColor()
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        letter_fastscroller.setupWithRecyclerView(fragment_list, { position ->
            try {
                val name = contacts[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.toUpperCase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }
}
