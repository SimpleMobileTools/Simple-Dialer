package com.simplemobiletools.dialer.activities

import android.os.Bundle
import com.google.gson.Gson
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.helpers.ContactsHelper
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.models.PhoneNumber
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.adapters.SpeedDialAdapter
import com.simplemobiletools.dialer.dialogs.SelectContactDialog
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.interfaces.RemoveSpeedDialListener
import com.simplemobiletools.dialer.models.SpeedDial
import kotlinx.android.synthetic.main.activity_manage_speed_dial.*

class ManageSpeedDialActivity : SimpleActivity(), RemoveSpeedDialListener {
    private var allContacts = ArrayList<Contact>()
    private var speedDialValues = ArrayList<SpeedDial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_speed_dial)

        updateMaterialActivityViews(manage_speed_dial_coordinator, manage_speed_dial_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(manage_speed_dial_scrollview, manage_speed_dial_toolbar)

        speedDialValues = config.getSpeedDialValues()
        updateAdapter()
        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts = contacts
        }

        updateTextColors(manage_speed_dial_scrollview)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(manage_speed_dial_toolbar, NavigationIcon.Arrow)
    }

    override fun onStop() {
        super.onStop()
        config.speedDial = Gson().toJson(speedDialValues)
    }

    private fun updateAdapter() {
        SpeedDialAdapter(this, speedDialValues, this, speed_dial_list) {
            val clickedContact = it as SpeedDial
            if (allContacts.isEmpty()) {
                return@SpeedDialAdapter
            }

            SelectContactDialog(this, allContacts) { selectedContact ->
                if (selectedContact.phoneNumbers.size > 1) {
                    val radioItems = selectedContact.phoneNumbers.mapIndexed { index, item ->
                        RadioItem(index, item.normalizedNumber, item)
                    }
                    val userPhoneNumbersList = selectedContact.phoneNumbers.map { it.value }
                    val checkedItemId = userPhoneNumbersList.indexOf(clickedContact.number)
                    RadioGroupDialog(this, ArrayList(radioItems), checkedItemId = checkedItemId) { selectedValue ->
                        val selectedNumber = selectedValue as PhoneNumber
                        speedDialValues.first { it.id == clickedContact.id }.apply {
                            displayName = selectedContact.getNameToDisplay()
                            number = selectedNumber.normalizedNumber
                        }
                        updateAdapter()
                    }
                } else {
                    speedDialValues.first { it.id == clickedContact.id }.apply {
                        displayName = selectedContact.getNameToDisplay()
                        number = selectedContact.phoneNumbers.first().normalizedNumber
                    }
                    updateAdapter()
                }

            }
        }.apply {
            speed_dial_list.adapter = this
        }
    }

    override fun removeSpeedDial(ids: ArrayList<Int>) {
        ids.forEach {
            val dialId = it
            speedDialValues.first { it.id == dialId }.apply {
                displayName = ""
                number = ""
            }
        }
        updateAdapter()
    }
}
