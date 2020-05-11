package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.AttributeSet
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.extensions.config
import kotlinx.android.synthetic.main.fragment_letters_layout.view.*
import java.util.*

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        fragment_placeholder.text = context.getString(placeholderResId)

        val placeholderActionResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.create_new
        } else {
            R.string.request_access
        }

        fragment_placeholder_2.apply {
            text = context.getString(placeholderActionResId)
            setTextColor(context.config.primaryColor)
            underlineText()
            setOnClickListener {
                requestReadContactsPermission()
            }
        }

        letter_fastscroller.textColor = context.config.textColor.getColorStateList()
        letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
        letter_fastscroller_thumb.textColor = context.config.primaryColor.getContrastColor()

        fragment_fab.setOnClickListener {
            Intent(Intent.ACTION_INSERT).apply {
                data = ContactsContract.Contacts.CONTENT_URI

                if (resolveActivity(context.packageManager) != null) {
                    activity?.startActivity(this)
                } else {
                    context.toast(R.string.no_app_found)
                }
            }
        }
    }

    override fun textColorChanged(color: Int) {
        (fragment_list?.adapter as? MyRecyclerViewAdapter)?.updateTextColor(color)
        letter_fastscroller?.textColor = color.getColorStateList()
    }

    override fun primaryColorChanged(color: Int) {
        letter_fastscroller_thumb?.thumbColor = color.getColorStateList()
        letter_fastscroller_thumb?.textColor = color.getContrastColor()
        fragment_fab.background.applyColorFilter(context.getAdjustedPrimaryColor())
    }

    fun refreshContacts(contacts: ArrayList<SimpleContact>) {
        setupLetterFastscroller(contacts)
        if (contacts.isEmpty()) {
            fragment_placeholder.beVisible()
            fragment_placeholder_2.beVisible()
            fragment_list.beGone()
        } else {
            fragment_placeholder.beGone()
            fragment_placeholder_2.beGone()
            fragment_list.beVisible()
            ContactsAdapter(activity as SimpleActivity, contacts, fragment_list) {
                val lookupKey = SimpleContactsHelper(activity!!).getContactLookupKey((it as SimpleContact).rawId.toString())
                val publicUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
                activity!!.launchViewContactIntent(publicUri)
            }.apply {
                fragment_list.adapter = this
            }
        }
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

    private fun requestReadContactsPermission() {
        activity?.handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                fragment_placeholder.text = context.getString(R.string.no_contacts_found)
                fragment_placeholder_2.text = context.getString(R.string.create_new)

                SimpleContactsHelper(context).getAvailableContacts { contacts ->
                    activity?.runOnUiThread {
                        refreshContacts(contacts)
                    }
                }
            }
        }
    }
}
