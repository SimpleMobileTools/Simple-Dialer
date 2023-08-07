package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
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
import com.simplemobiletools.dialer.databinding.FragmentRecentsContentBinding
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.MIN_RECENTS_THRESHOLD
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import com.simplemobiletools.dialer.models.RecentCall

class RecentsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.RecentsFragmentLayout>(context, attributeSet),
    RefreshItemsListener {
    private var allRecentCalls = listOf<RecentCall>()
    private var recentsAdapter: RecentCallsAdapter? = null
    lateinit var binding: FragmentRecentsContentBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentRecentsContentBinding.bind(this)
        innerBinding = RecentsFragmentLayout(FragmentRecentsContentBinding.bind(binding.root))
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        innerBinding?.fragmentPlaceholder?.text = context.getString(placeholderResId)
        innerBinding?.fragmentPlaceholder2?.apply {
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {

        innerBinding?.fragmentPlaceholder?.setTextColor(textColor)
        innerBinding?.fragmentPlaceholder2?.setTextColor(properPrimaryColor)

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

    private fun gotRecents(recents: List<RecentCall>) {
        if (recents.isEmpty()) {

            innerBinding?.fragmentPlaceholder?.beVisible()
            innerBinding?.fragmentPlaceholder2?.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))
            innerBinding?.fragmentList?.beGone()
        } else {
            innerBinding?.fragmentPlaceholder?.beGone()
            innerBinding?.fragmentPlaceholder2?.beGone()
            innerBinding?.fragmentList?.beVisible()

            val currAdapter = innerBinding?.fragmentList?.adapter
            if (currAdapter == null) {
                recentsAdapter = RecentCallsAdapter(activity as SimpleActivity, recents.toMutableList(), innerBinding.fragmentList, this, true) {
                    val recentCall = it as RecentCall
                    if (context.config.showCallConfirmation) {
                        CallConfirmationDialog(activity as SimpleActivity, recentCall.name) {
                            activity?.launchCallIntent(recentCall.phoneNumber)
                        }
                    } else {
                        activity?.launchCallIntent(recentCall.phoneNumber)
                    }
                }

                innerBinding.fragmentList?.adapter = recentsAdapter

                if (context.areSystemAnimationsEnabled) {
                    innerBinding.fragmentList?.scheduleLayoutAnimation()
                }

                innerBinding.fragmentList?.endlessScrollListener = object : MyRecyclerView.EndlessScrollListener {
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
                innerBinding?.fragmentPlaceholder?.text = context.getString(R.string.no_previous_calls)
                innerBinding?.fragmentPlaceholder2?.beGone()

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
        innerBinding?.fragmentPlaceholder?.beVisibleIf(allRecentCalls.isEmpty())
        recentsAdapter?.updateItems(allRecentCalls)
    }

    override fun onSearchQueryChanged(text: String) {
        val recentCalls = allRecentCalls.filter {
            it.name.contains(text, true) || it.doesContainPhoneNumber(text)
        }.sortedByDescending {
            it.name.startsWith(text, true)
        }.toMutableList() as ArrayList<RecentCall>

        innerBinding?.fragmentPlaceholder?.beVisibleIf(recentCalls.isEmpty())
        recentsAdapter?.updateItems(recentCalls, text)
    }
}

// hide private contacts from recent calls
private fun List<RecentCall>.hidePrivateContacts(privateContacts: List<Contact>, shouldHide: Boolean): List<RecentCall> {
    return if (shouldHide) {
        filterNot { recent ->
            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
            recent.phoneNumber in privateNumbers
        }
    } else {
        this
    }
}

private fun List<RecentCall>.setNamesIfEmpty(contacts: List<Contact>, privateContacts: List<Contact>): ArrayList<RecentCall> {
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
