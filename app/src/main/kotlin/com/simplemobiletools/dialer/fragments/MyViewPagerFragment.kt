package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.simplemobiletools.commons.extensions.underlineText
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.Config
import com.simplemobiletools.dialer.models.Contact
import kotlinx.android.synthetic.main.fragment_letters_layout.view.*

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : CoordinatorLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null
    protected var allContacts = ArrayList<Contact>()

    private var lastHashCode = 0
    private lateinit var config: Config

    fun setupFragment(activity: SimpleActivity) {
        config = activity.config
        if (this.activity == null) {
            this.activity = activity

            fragment_placeholder_2?.underlineText()
        }
    }
}
