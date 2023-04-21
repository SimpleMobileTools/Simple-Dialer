package com.simplemobiletools.dialer.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.SMT_PRIVATE
import com.simplemobiletools.commons.extensions.getVisibleContactSources
import com.simplemobiletools.commons.helpers.ContactsHelper
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.models.contacts.*
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.FilterContactSourcesAdapter
import com.simplemobiletools.dialer.extensions.config
import kotlinx.android.synthetic.main.dialog_filter_contact_sources.view.*

class FilterContactSourcesDialog(val activity: SimpleActivity, private val callback: () -> Unit) {
    private var dialog: AlertDialog? = null
    private val view = activity.layoutInflater.inflate(R.layout.dialog_filter_contact_sources, null)
    private var contactSources = ArrayList<ContactSource>()
    private var contacts = ArrayList<Contact>()
    private var isContactSourcesReady = false
    private var isContactsReady = false

    init {
            val contactHelper = ContactsHelper(activity)
            contactHelper.getContactSources { contactSources ->
            contactSources.mapTo(this@FilterContactSourcesDialog.contactSources) { it.copy() }
            isContactSourcesReady = true
            processDataIfReady()
        }

        contactHelper.getContacts(getAll = true, showOnlyContactsWithNumbers = true) {
            it.mapTo(contacts) { contact -> contact.copy() }
            val privateCursor = activity.getMyContactsCursor(false, true)
            val privateContacts = MyContactsContentProvider.getContacts(activity, privateCursor)
            this.contacts.addAll(privateContacts)
            isContactsReady = true
            processDataIfReady()
        }
    }

    private fun processDataIfReady() {
        if (!isContactSourcesReady) {
            return
        }

        val contactSourcesWithCount = ArrayList<ContactSource>()
        for (contactSource in contactSources) {
            val count = if (isContactsReady) {
                contacts.filter { it.source == contactSource.name }.count()
            } else {
                -1
            }
            contactSourcesWithCount.add(contactSource.copy(count = count))
        }

        contactSources.clear()
        contactSources.addAll(contactSourcesWithCount)

        activity.runOnUiThread {
            val selectedSources = activity.getVisibleContactSources()
            view.filter_contact_sources_list.adapter = FilterContactSourcesAdapter(activity, contactSourcesWithCount, selectedSources)

            if (dialog == null) {
                activity.getAlertDialogBuilder()
                    .setPositiveButton(R.string.ok) { dialogInterface, i -> confirmContactSources() }
                    .setNegativeButton(R.string.cancel, null)
                    .apply {
                        activity.setupDialogStuff(view, this) { alertDialog ->
                            dialog = alertDialog
                        }
                    }
            }
        }
    }

    private fun confirmContactSources() {
        val selectedContactSources = (view.filter_contact_sources_list.adapter as FilterContactSourcesAdapter).getSelectedContactSources()
        val ignoredContactSources = contactSources.filter { !selectedContactSources.contains(it) }.map {
            if (it.type == SMT_PRIVATE) SMT_PRIVATE else it.getFullIdentifier()
        }.toHashSet()

        if (activity.getVisibleContactSources() != ignoredContactSources) {
            activity.config.ignoredContactSources = ignoredContactSources
            callback()
        }
        dialog?.dismiss()
    }
}
