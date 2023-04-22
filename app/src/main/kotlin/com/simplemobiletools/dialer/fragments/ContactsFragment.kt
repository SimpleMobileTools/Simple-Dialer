package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.MainActivity
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.extensions.launchCreateNewContactIntent
import com.simplemobiletools.dialer.extensions.startContactDetailsIntent
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import kotlinx.android.synthetic.main.fragment_letters_layout.view.*
import java.util.*

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), RefreshItemsListener {
    private var allContacts = ArrayList<Contact>()

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        fragment_placeholder.text = context.getString(placeholderResId)

        val placeholderActionResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.create_new_contact
        } else {
            R.string.request_access
        }

        fragment_placeholder_2.apply {
            text = context.getString(placeholderActionResId)
            underlineText()
            setOnClickListener {
                if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
                    activity?.launchCreateNewContactIntent()
                } else {
                    requestReadContactsPermission()
                }
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        (fragment_list?.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)
        fragment_placeholder.setTextColor(textColor)
        fragment_placeholder_2.setTextColor(properPrimaryColor)

        letter_fastscroller.textColor = textColor.getColorStateList()
        letter_fastscroller.pressedTextColor = properPrimaryColor
        letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
        letter_fastscroller_thumb.textColor = properPrimaryColor.getContrastColor()
        letter_fastscroller_thumb.thumbColor = properPrimaryColor.getColorStateList()
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(false, true)
        ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts = contacts

            if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }
            }
            (activity as MainActivity).cacheContacts(allContacts)

            activity?.runOnUiThread {
                gotContacts(contacts)
                callback?.invoke()
            }
        }
    }

    private fun gotContacts(contacts: ArrayList<Contact>) {
        setupLetterFastscroller(contacts)
        if (contacts.isEmpty()) {
            fragment_placeholder.beVisible()
            fragment_placeholder_2.beVisible()
            fragment_list.beGone()
        } else {
            fragment_placeholder.beGone()
            fragment_placeholder_2.beGone()
            fragment_list.beVisible()

            val currAdapter = fragment_list.adapter
            if (currAdapter == null) {
                ContactsAdapter(activity as SimpleActivity, contacts, fragment_list, this) {
                    val contact = it as Contact
                    activity?.startContactDetailsIntent(contact)
                }.apply {
                    fragment_list.adapter = this
                }

                if (context.areSystemAnimationsEnabled) {
                    fragment_list.scheduleLayoutAnimation()
                }
            } else {
                (currAdapter as ContactsAdapter).updateItems(contacts)
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<Contact>) {
        letter_fastscroller.setupWithRecyclerView(fragment_list, { position ->
            try {
                val name = contacts[position].getNameToDisplay()
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.toUpperCase(Locale.getDefault()).normalizeString())
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    override fun onSearchClosed() {
        fragment_placeholder.beVisibleIf(allContacts.isEmpty())
        (fragment_list.adapter as? ContactsAdapter)?.updateItems(allContacts)
        setupLetterFastscroller(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
            val shouldNormalize = text.normalizeString() == text
            val filtered = allContacts.filter {
                getProperText(it.getNameToDisplay(), shouldNormalize).contains(text, true) ||
                    getProperText(it.nickname, shouldNormalize).contains(text, true) ||
                    it.phoneNumbers.any {
                        text.normalizePhoneNumber().isNotEmpty() && it.normalizedNumber.contains(text.normalizePhoneNumber(), true)
                    } ||
                    it.emails.any { it.value.contains(text, true) } ||
                    it.addresses.any { getProperText(it.value, shouldNormalize).contains(text, true) } ||
                    it.IMs.any { it.value.contains(text, true) } ||
                    getProperText(it.notes, shouldNormalize).contains(text, true) ||
                    getProperText(it.organization.company, shouldNormalize).contains(text, true) ||
                    getProperText(it.organization.jobPosition, shouldNormalize).contains(text, true) ||
                    it.websites.any { it.contains(text, true) }
            } as ArrayList

            filtered.sortBy {
                val nameToDisplay = it.getNameToDisplay()
                !getProperText(nameToDisplay, shouldNormalize).startsWith(text, true) && !nameToDisplay.contains(text, true)
            }

        fragment_placeholder.beVisibleIf(filtered.isEmpty())
        (fragment_list.adapter as? ContactsAdapter)?.updateItems(filtered, text)
        setupLetterFastscroller(filtered)
        }
    private fun requestReadContactsPermission() {
        activity?.handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                fragment_placeholder.text = context.getString(R.string.no_contacts_found)
                fragment_placeholder_2.text = context.getString(R.string.create_new_contact)
                ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                    activity?.runOnUiThread {
                        gotContacts(contacts)
                    }
                }
            }
        }
    }
}
