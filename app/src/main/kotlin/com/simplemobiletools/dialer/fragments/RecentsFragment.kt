package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import com.simplemobiletools.commons.dialogs.CallConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ContactsHelper
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CALL_LOG
import com.simplemobiletools.commons.helpers.SMT_PRIVATE
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.RecentCallsAdapter
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.MIN_RECENTS_THRESHOLD
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import com.simplemobiletools.dialer.models.RecentCall
import kotlinx.android.synthetic.main.fragment_recents.view.*

class RecentsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), RefreshItemsListener {
    private var allRecentCalls = ArrayList<RecentCall>()
    private var recentsAdapter: RecentCallsAdapter? = null

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        recents_placeholder.text = context.getString(placeholderResId)
        recents_placeholder_2.apply {
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        recents_placeholder.setTextColor(textColor)
        recents_placeholder_2.setTextColor(properPrimaryColor)

        recentsAdapter?.apply {
            initDrawables()
            updateTextColor(textColor)
        }
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(false, true)
        val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
        val querySize = allRecentCalls.size.coerceAtLeast(MIN_RECENTS_THRESHOLD)
        RecentsHelper(context).getRecentCalls(groupSubsequentCalls, querySize) { recents ->
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)

                allRecentCalls = recents
                    .setNamesIfEmpty(contacts, privateContacts)
                    .hidePrivateContacts(privateContacts, SMT_PRIVATE in context.baseConfig.ignoredContactSources)

                activity?.runOnUiThread {
                    gotRecents(allRecentCalls)
                }
            }
        }
    }

    private fun gotRecents(recents: ArrayList<RecentCall>) {
        if (recents.isEmpty()) {
            recents_placeholder.beVisible()
            recents_placeholder_2.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))
            recents_list.beGone()
        } else {
            recents_placeholder.beGone()
            recents_placeholder_2.beGone()
            recents_list.beVisible()

            val currAdapter = recents_list.adapter
            if (currAdapter == null) {
                recentsAdapter = RecentCallsAdapter(activity as SimpleActivity, recents, recents_list, this, true) {
                    val recentCall = it as RecentCall
                    if (context.config.showCallConfirmation) {
                        CallConfirmationDialog(activity as SimpleActivity, recentCall.name) {
                            activity?.launchCallIntent(recentCall.phoneNumber)
                        }
                    } else {
                        activity?.launchCallIntent(recentCall.phoneNumber)
                    }
                }

                recents_list.adapter = recentsAdapter

                if (context.areSystemAnimationsEnabled) {
                    recents_list.scheduleLayoutAnimation()
                }

                recents_list.endlessScrollListener = object : MyRecyclerView.EndlessScrollListener {
                    override fun updateTop() {}

                    override fun updateBottom() {
                        getMoreRecentCalls()
                    }
                }

            } else {
                recentsAdapter?.updateItems(recents)
            }
        }
    }

    private fun getMoreRecentCalls() {
        val privateCursor = context?.getMyContactsCursor(false, true)
        val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
        val querySize = allRecentCalls.size.plus(MIN_RECENTS_THRESHOLD)
        RecentsHelper(context).getRecentCalls(groupSubsequentCalls, querySize) { recents ->
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)

                allRecentCalls = recents
                    .setNamesIfEmpty(contacts, privateContacts)
                    .hidePrivateContacts(privateContacts, SMT_PRIVATE in context.baseConfig.ignoredContactSources)

                activity?.runOnUiThread {
                    gotRecents(allRecentCalls)
                }
            }
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                recents_placeholder.text = context.getString(R.string.no_previous_calls)
                recents_placeholder_2.beGone()

                val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
                RecentsHelper(context).getRecentCalls(groupSubsequentCalls) { recents ->
                    activity?.runOnUiThread {
                        gotRecents(recents)
                    }
                }
            }
        }
    }

    override fun onSearchClosed() {
        recents_placeholder.beVisibleIf(allRecentCalls.isEmpty())
        recentsAdapter?.updateItems(allRecentCalls)
    }

    override fun onSearchQueryChanged(text: String) {
        val recentCalls = allRecentCalls.filter {
            it.name.contains(text, true) || it.doesContainPhoneNumber(text)
        }.sortedByDescending {
            it.name.startsWith(text, true)
        }.toMutableList() as ArrayList<RecentCall>

        recents_placeholder.beVisibleIf(recentCalls.isEmpty())
        recentsAdapter?.updateItems(recentCalls, text)
    }
}

// hide private contacts from recent calls
private fun List<RecentCall>.hidePrivateContacts(privateContacts: ArrayList<Contact>, shouldHide: Boolean): ArrayList<RecentCall> {
    return if (shouldHide) {
        filterNot { recent ->
            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
            recent.phoneNumber in privateNumbers
        } as ArrayList
    } else {
        this as ArrayList
    }
}

private fun ArrayList<RecentCall>.setNamesIfEmpty(contacts: ArrayList<Contact>, privateContacts: ArrayList<Contact>): ArrayList<RecentCall> {
    val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
    return map { recent ->
        if (recent.phoneNumber == recent.name) {
            val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
            val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }

            when {
                privateContact != null -> recent.copy(name = privateContact.getNameToDisplay())
                contact != null -> recent.copy(name = contact.getNameToDisplay())
                else -> recent
            }
        } else {
            recent
        }
    } as ArrayList
}
