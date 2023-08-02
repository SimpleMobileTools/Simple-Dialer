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
import com.simplemobiletools.dialer.databinding.FragmentContactsBinding
import com.simplemobiletools.dialer.databinding.FragmentLettersLayoutBinding
import com.simplemobiletools.dialer.extensions.launchCreateNewContactIntent
import com.simplemobiletools.dialer.extensions.startContactDetailsIntent
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import java.util.Locale

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.LetterLayout>(context, attributeSet),
    RefreshItemsListener {
    private var allContacts = ArrayList<Contact>()

    lateinit var binding: FragmentContactsBinding
    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentContactsBinding.bind(this)
        innerBinding = LetterLayout(FragmentLettersLayoutBinding.bind(binding.root))
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        innerBinding.fragmentPlaceholder.text = context.getString(placeholderResId)

        val placeholderActionResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.create_new_contact
        } else {
            R.string.request_access
        }

        innerBinding.fragmentPlaceholder2.apply {
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
        with(innerBinding) {
            (fragmentList.adapter as? MyRecyclerViewAdapter)?.updateTextColor(textColor)

            fragmentPlaceholder.setTextColor(textColor)
            fragmentPlaceholder2.setTextColor(properPrimaryColor)

            letterFastscroller.textColor = textColor.getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(innerBinding.letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    fun isBlocked(number: String): Boolean {
        val normalizedNumber = number.normalizePhoneNumber()
        val blockedNumbers = context.getBlockedNumbers()
        return blockedNumbers.any { it.normalizedNumber == normalizedNumber }
    }
    override fun refreshItems(callback: (() -> Unit)?) {
        val privateCursor = context?.getMyContactsCursor(false, true)
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
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }
            }
            (activity as? MainActivity)?.cacheContacts(allContacts)

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
                fragmentPlaceholder2.beVisible()
                fragmentList.beGone()
            } else {
                fragmentPlaceholder.beGone()
                fragmentPlaceholder2.beGone()
                fragmentList.beVisible()

                val currAdapter = fragmentList.adapter
                if (currAdapter == null) {
                    fragmentList.let {
                        ContactsAdapter(
                            activity = activity as SimpleActivity,
                            contacts = contacts,
                            recyclerView = it,
                            refreshItemsListener = this@ContactsFragment
                        ) {
                            val contact = it as Contact
                            activity?.startContactDetailsIntent(contact)
                        }.apply {
                            fragmentList.adapter = this
                        }
                    }

                    if (context.areSystemAnimationsEnabled) {
                        fragmentList.scheduleLayoutAnimation()
                    }
                } else {
                    (currAdapter as ContactsAdapter).updateItems(contacts)
                }
            }
        }

    }

    private fun setupLetterFastScroller(contacts: ArrayList<Contact>) {
        innerBinding.letterFastscroller.setupWithRecyclerView(innerBinding.fragmentList, { position ->
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

        innerBinding.fragmentPlaceholder.beVisibleIf(filtered.isEmpty())
        (innerBinding.fragmentList.adapter as? ContactsAdapter)?.updateItems(filtered, text)
        setupLetterFastScroller(filtered)
    }

    private fun requestReadContactsPermission() {
        activity?.handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                innerBinding.fragmentPlaceholder.text = context.getString(R.string.no_contacts_found)
                innerBinding.fragmentPlaceholder2.text = context.getString(R.string.create_new_contact)
                ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                    activity?.runOnUiThread {
                        gotContacts(contacts)
                    }
                }
            }
        }
    }
}
