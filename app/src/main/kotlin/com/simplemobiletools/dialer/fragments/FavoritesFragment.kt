package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.google.gson.Gson
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.CallConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.commons.views.MyLinearLayoutManager
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.databinding.FragmentFavoritesBinding
import com.simplemobiletools.dialer.databinding.FragmentLettersLayoutBinding
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.Converters
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import java.util.Locale

class FavoritesFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LetterLayout>(context, attributeSet),
    RefreshItemsListener {
    private var allContacts = ArrayList<Contact>()
    lateinit var binding: FragmentFavoritesBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentFavoritesBinding.bind(this)
        innerBinding = LetterLayout(FragmentLettersLayoutBinding.bind(binding.root))
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        innerBinding.fragmentPlaceholder.text = context.getString(placeholderResId)
        innerBinding.fragmentPlaceholder2.beGone()
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        with(innerBinding) {
            fragmentPlaceholder.setTextColor(textColor)
            (fragmentList.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)

            letterFastscroller.textColor = textColor.getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller!!)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts = contacts

            val blockedNumbers: List<String> = context.getBlockedNumbers().map { it.number }
            val phoneNumbers: List<String> = allContacts.map { it.phoneNumbers }.flatten().map { it.value }
            val nonBlockedNumbers = phoneNumbers.filter { !blockedNumbers.contains(it) }
            val nonBlockedContacts = allContacts.filter { contact ->
                contact.phoneNumbers.any { phoneNumber ->
                    nonBlockedNumbers.contains(phoneNumber.value)
                }
            }

            allContacts = ArrayList(nonBlockedContacts)


            if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                val privateCursor = context?.getMyContactsCursor(true, true)
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor).map {
                    it.copy(starred = 1)
                }
                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }
            }
            val favorites = allContacts.filter { it.starred == 1 } as ArrayList<Contact>

            allContacts = if (activity?.config?.isCustomOrderSelected == true) {
                sortByCustomOrder(favorites)
            } else {
                favorites
            }

            activity?.runOnUiThread {
                gotContacts(allContacts)
                callback?.invoke()
            }
        }
    }

    private fun gotContacts(contacts: ArrayList<Contact>) {
        setupLetterFastScroller(contacts)
        with(innerBinding) {
            if (contacts.isEmpty()) {
                fragmentPlaceholder.beVisible()
                fragmentList.beGone()
            } else {
                fragmentPlaceholder.beGone()
                fragmentList.beVisible()

                updateListAdapter()
            }
        }
    }

    private fun updateListAdapter() {
        val viewType = context.config.viewType
        setViewType(viewType)

        val currAdapter = innerBinding.fragmentList.adapter as ContactsAdapter?
        if (currAdapter == null) {
            ContactsAdapter(
                activity = activity as SimpleActivity,
                contacts = allContacts,
                recyclerView = innerBinding.fragmentList,
                refreshItemsListener = this,
                viewType = viewType,
                showDeleteButton = false,
                enableDrag = true,
            ) {
                if (context.config.showCallConfirmation) {
                    CallConfirmationDialog(activity as SimpleActivity, (it as Contact).getNameToDisplay()) {
                        activity?.apply {
                            initiateCall(it) { launchCallIntent(it) }
                        }
                    }
                } else {
                    activity?.apply {
                        initiateCall(it as Contact) { launchCallIntent(it) }
                    }
                }
            }.apply {
                innerBinding.fragmentList.adapter = this

                onDragEndListener = {
                    val adapter = innerBinding.fragmentList?.adapter
                    if (adapter is ContactsAdapter) {
                        val items = adapter.contacts
                        saveCustomOrderToPrefs(items)
                        setupLetterFastScroller(items)
                    }
                }

                onSpanCountListener = { newSpanCount ->
                    context.config.contactsGridColumnCount = newSpanCount
                }
            }

            if (context.areSystemAnimationsEnabled) {
                innerBinding.fragmentList?.scheduleLayoutAnimation()
            }
        } else {
            currAdapter.viewType = viewType
            currAdapter.updateItems(allContacts)
        }
    }

    fun columnCountChanged() {
        (innerBinding.fragmentList.layoutManager as MyGridLayoutManager).spanCount = context!!.config.contactsGridColumnCount
        innerBinding.fragmentList.adapter?.apply {
            notifyItemRangeChanged(0, allContacts.size)
        }
    }

    private fun sortByCustomOrder(favorites: List<Contact>): ArrayList<Contact> {
        val favoritesOrder = activity!!.config.favoritesContactsOrder

        if (favoritesOrder.isEmpty()) {
            return ArrayList(favorites)
        }

        val orderList = Converters().jsonToStringList(favoritesOrder)
        val map = orderList.withIndex().associate { it.value to it.index }
        val sorted = favorites.sortedBy { map[it.contactId.toString()] }

        return ArrayList(sorted)
    }

    private fun saveCustomOrderToPrefs(items: List<Contact>) {
        activity?.apply {
            val orderIds = items.map { it.contactId }
            val orderGsonString = Gson().toJson(orderIds)
            config.favoritesContactsOrder = orderGsonString
        }
    }

    private fun setupLetterFastScroller(contacts: List<Contact>) {
        innerBinding.letterFastscroller?.setupWithRecyclerView(innerBinding.fragmentList, { position ->
            try {
                val name = contacts[position].getNameToDisplay()
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.uppercase(Locale.getDefault()).normalizeString())
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    override fun onSearchClosed() {
        innerBinding.fragmentPlaceholder.beVisibleIf(allContacts.isEmpty())
        (innerBinding.fragmentList.adapter as? ContactsAdapter)?.updateItems(allContacts)
        setupLetterFastScroller(allContacts)
    }

    override fun onSearchQueryChanged(text: String) {
        val contacts = allContacts.filter {
            it.name.contains(text, true) || it.doesContainPhoneNumber(text)
        }.sortedByDescending {
            it.name.startsWith(text, true)
        }.toMutableList() as ArrayList<Contact>

        innerBinding.fragmentPlaceholder.beVisibleIf(contacts.isEmpty())
        (innerBinding.fragmentList.adapter as? ContactsAdapter)?.updateItems(contacts, text)
        setupLetterFastScroller(contacts)
    }

    private fun setViewType(viewType: Int) {
        val spanCount = context.config.contactsGridColumnCount

        val layoutManager = if (viewType == VIEW_TYPE_GRID) {
            innerBinding.letterFastscroller.beGone()
            MyGridLayoutManager(context, spanCount)
        } else {
            innerBinding.letterFastscroller.beVisible()
            MyLinearLayoutManager(context)
        }
        innerBinding.fragmentList?.layoutManager = layoutManager
    }
}
