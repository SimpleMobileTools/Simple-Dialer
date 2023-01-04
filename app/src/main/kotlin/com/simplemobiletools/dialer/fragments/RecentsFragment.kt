package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import com.simplemobiletools.commons.dialogs.CallConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CALL_LOG
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.RecentCallsAdapter
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import com.simplemobiletools.dialer.models.RecentCall
import kotlinx.android.synthetic.main.fragment_recents.view.*

class RecentsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), RefreshItemsListener {
    private var allRecentCalls = ArrayList<RecentCall>()

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

        (recents_list?.adapter as? RecentCallsAdapter)?.apply {
            initDrawables()
            updateTextColor(textColor)
        }
    }

    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(false, true)
        val groupSubsequentCalls = context?.config?.groupSubsequentCalls ?: false
        RecentsHelper(context).getRecentCalls(groupSubsequentCalls) { recents ->
            SimpleContactsHelper(context).getAvailableContacts(false) { contacts ->
                val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)

                recents.filter { it.phoneNumber == it.name }.forEach { recent ->
                    var wasNameFilled = false
                    if (privateContacts.isNotEmpty()) {
                        val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(recent.phoneNumber) }
                        if (privateContact != null) {
                            recent.name = privateContact.name
                            wasNameFilled = true
                        }
                    }

                    if (!wasNameFilled) {
                        val contact = contacts.firstOrNull { it.phoneNumbers.first().normalizedNumber == recent.phoneNumber }
                        if (contact != null) {
                            recent.name = contact.name
                        }
                    }
                }

                allRecentCalls = recents
                activity?.runOnUiThread {
                    gotRecents(recents)
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
                RecentCallsAdapter(activity as SimpleActivity, recents, recents_list, this, true) {
                    val recentCall = it as RecentCall
                    if (context.config.showCallConfirmation) {
                        CallConfirmationDialog(activity as SimpleActivity, recentCall.name) {
                            activity?.launchCallIntent(recentCall.phoneNumber)
                        }
                    } else {
                        activity?.launchCallIntent(recentCall.phoneNumber)
                    }
                }.apply {
                    recents_list.adapter = this
                }

                if (context.areSystemAnimationsEnabled) {
                    recents_list.scheduleLayoutAnimation()
                }
            } else {
                (currAdapter as RecentCallsAdapter).updateItems(recents)
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
        (recents_list.adapter as? RecentCallsAdapter)?.updateItems(allRecentCalls)
    }

    override fun onSearchQueryChanged(text: String) {
        val recentCalls = allRecentCalls.filter {
            it.name.contains(text, true) || it.doesContainPhoneNumber(text)
        }.sortedByDescending {
            it.name.startsWith(text, true)
        }.toMutableList() as ArrayList<RecentCall>

        recents_placeholder.beVisibleIf(recentCalls.isEmpty())
        (recents_list.adapter as? RecentCallsAdapter)?.updateItems(recentCalls, text)
    }
}
