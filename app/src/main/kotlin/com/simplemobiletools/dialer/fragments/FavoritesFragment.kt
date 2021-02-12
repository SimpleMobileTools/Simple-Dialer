package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import kotlinx.android.synthetic.main.fragment_letters_layout.view.*
import java.util.*

class FavoritesFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), RefreshItemsListener {
    private var allContacts = ArrayList<SimpleContact>()

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        fragment_placeholder.text = context.getString(placeholderResId)
        fragment_fab.beGone()
        fragment_placeholder_2.beGone()
    }

    override fun setupColors(textColor: Int, primaryColor: Int, adjustedPrimaryColor: Int) {
        (fragment_list?.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)

        letter_fastscroller.textColor = textColor.getColorStateList()
        letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
        letter_fastscroller_thumb.textColor = adjustedPrimaryColor.getContrastColor()
        letter_fastscroller_thumb.thumbColor = adjustedPrimaryColor.getColorStateList()
    }

    override fun refreshItems() {
        val privateCursor = context?.getMyFavoriteContactsCursor()?.loadInBackground()
        SimpleContactsHelper(context).getAvailableContacts(true) { contacts ->
            allContacts = contacts

            val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
            if (privateContacts.isNotEmpty()) {
                allContacts.addAll(privateContacts)
                allContacts.sort()
            }

            activity?.runOnUiThread {
                gotContacts(contacts)
            }
        }
    }

    private fun gotContacts(contacts: ArrayList<SimpleContact>) {
        setupLetterFastscroller(contacts)
        if (contacts.isEmpty()) {
            fragment_placeholder.beVisible()
            fragment_list.beGone()
        } else {
            fragment_placeholder.beGone()
            fragment_list.beVisible()

            val currAdapter = fragment_list.adapter
            if (currAdapter == null) {
                ContactsAdapter(activity as SimpleActivity, contacts, fragment_list, this, showDeleteButton = false) {
                    val phoneNumbers = (it as SimpleContact).phoneNumbers
                    if (phoneNumbers.size <= 1) {
                        activity?.launchCallIntent(it.phoneNumbers.first())
                    } else {
                        val items = ArrayList<RadioItem>()
                        phoneNumbers.forEachIndexed { index, phoneNumber ->
                            items.add(RadioItem(index, phoneNumber))
                        }

                        RadioGroupDialog(activity!!, items) {
                            activity?.launchCallIntent(phoneNumbers[it as Int])
                        }
                    }
                }.apply {
                    fragment_list.adapter = this
                }
            } else {
                (currAdapter as ContactsAdapter).updateItems(contacts)
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

    override fun onSearchClosed() {
        fragment_placeholder.beVisibleIf(allContacts.isEmpty())
        (fragment_list.adapter as? ContactsAdapter)?.updateItems(allContacts)
        setupLetterFastscroller(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
        val contacts = allContacts.filter {
            it.name.contains(text, true) || it.doesContainPhoneNumber(text)
        }.toMutableList() as ArrayList<SimpleContact>

        fragment_placeholder.beVisibleIf(contacts.isEmpty())
        (fragment_list.adapter as? ContactsAdapter)?.updateItems(contacts, text)
        setupLetterFastscroller(contacts)
    }
}
