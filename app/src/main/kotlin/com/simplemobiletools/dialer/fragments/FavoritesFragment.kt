package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import com.google.gson.Gson
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.CallConfirmationDialog
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
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.Converters
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
        fragment_placeholder_2.beGone()
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        fragment_placeholder.setTextColor(textColor)
        (fragment_list?.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)

        letter_fastscroller.textColor = textColor.getColorStateList()
        letter_fastscroller.pressedTextColor = properPrimaryColor
        letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
        letter_fastscroller_thumb.textColor = properPrimaryColor.getContrastColor()
        letter_fastscroller_thumb.thumbColor = properPrimaryColor.getColorStateList()
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(true, true)
        SimpleContactsHelper(context).getAvailableContacts(true) { contacts ->
            allContacts = contacts

            val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
            if (privateContacts.isNotEmpty()) {
                allContacts.addAll(privateContacts)
                allContacts.sort()
            }

            if (activity!!.config.isCustomOrderSelected) {
                allContacts = sortByCustomOrder(allContacts)
            }

            activity?.runOnUiThread {
                gotContacts(allContacts)
                callback?.invoke()
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
                ContactsAdapter(
                    activity = activity as SimpleActivity,
                    contacts = contacts,
                    recyclerView = fragment_list,
                    refreshItemsListener = this,
                    showDeleteButton = false,
                    enableDrag = true,
                ) {
                    if (context.config.showCallConfirmation) {
                        CallConfirmationDialog(activity as SimpleActivity, (it as SimpleContact).name) {
                            callContact(it)
                        }
                    } else {
                        callContact(it as SimpleContact)
                    }
                }.apply {
                    fragment_list.adapter = this

                    onDragEndListener = {
                        val adapter = fragment_list?.adapter
                        if (adapter is ContactsAdapter) {
                            val items = adapter.contacts
                            saveCustomOrderToPrefs(items)
                            setupLetterFastscroller(items)
                        }
                    }
                }

                if (context.areSystemAnimationsEnabled) {
                    fragment_list.scheduleLayoutAnimation()
                }
            } else {
                (currAdapter as ContactsAdapter).updateItems(contacts)
            }
        }
    }

    private fun sortByCustomOrder(favorites: List<SimpleContact>): ArrayList<SimpleContact> {
        val favoritesOrder = activity!!.config.favoritesContactsOrder

        if (favoritesOrder.isEmpty()) {
            return ArrayList(favorites)
        }

        val orderList = Converters().jsonToStringList(favoritesOrder)
        val map = orderList.withIndex().associate { it.value to it.index }
        val sorted = favorites.sortedBy { map[it.contactId.toString()] }

        return ArrayList(sorted)
    }

    private fun saveCustomOrderToPrefs(items: ArrayList<SimpleContact>) {
        activity?.apply {
            val orderIds = items.map { it.contactId }
            val orderGsonString = Gson().toJson(orderIds)
            config.favoritesContactsOrder = orderGsonString
        }
    }

    private fun callContact(simpleContact: SimpleContact) {
        val phoneNumbers = simpleContact.phoneNumbers
        if (phoneNumbers.size <= 1) {
            activity?.launchCallIntent(phoneNumbers.first().normalizedNumber)
        } else {
            val primaryNumber = simpleContact.phoneNumbers.find { it.isPrimary }
            if (primaryNumber != null) {
                activity?.launchCallIntent(primaryNumber.value)
            } else {
                val items = ArrayList<RadioItem>()
                phoneNumbers.forEachIndexed { index, phoneNumber ->
                    val type = context.getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
                    items.add(RadioItem(index, "${phoneNumber.normalizedNumber} ($type)", phoneNumber.normalizedNumber))
                }

                RadioGroupDialog(activity!!, items) {
                    activity?.launchCallIntent(it as String)
                }
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        letter_fastscroller.setupWithRecyclerView(fragment_list, { position ->
            try {
                val name = contacts[position].name
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
        val contacts = allContacts.filter {
            it.name.contains(text, true) || it.doesContainPhoneNumber(text)
        }.sortedByDescending {
            it.name.startsWith(text, true)
        }.toMutableList() as ArrayList<SimpleContact>

        fragment_placeholder.beVisibleIf(contacts.isEmpty())
        (fragment_list.adapter as? ContactsAdapter)?.updateItems(contacts, text)
        setupLetterFastscroller(contacts)
    }
}
