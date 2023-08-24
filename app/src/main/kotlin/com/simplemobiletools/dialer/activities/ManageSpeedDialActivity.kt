package com.simplemobiletools.dialer.activities

import android.os.Bundle
import com.google.gson.Gson
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.ContactsHelper
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.models.PhoneNumber
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.dialer.adapters.SpeedDialAdapter
import com.simplemobiletools.dialer.databinding.ActivityManageSpeedDialBinding
import com.simplemobiletools.dialer.dialogs.SelectContactDialog
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.interfaces.RemoveSpeedDialListener
import com.simplemobiletools.dialer.models.SpeedDial

class ManageSpeedDialActivity : SimpleActivity(), RemoveSpeedDialListener {
    private val binding by viewBinding(ActivityManageSpeedDialBinding::inflate)

    private var allContacts = mutableListOf<Contact>()
    private var speedDialValues = mutableListOf<SpeedDial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            updateMaterialActivityViews(manageSpeedDialCoordinator, manageSpeedDialHolder, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(manageSpeedDialScrollview, manageSpeedDialToolbar)

        }

        speedDialValues = config.getSpeedDialValues()
        updateAdapter()

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts.addAll(contacts)

            val privateCursor = getMyContactsCursor(false, true)
            val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        updateTextColors(binding.manageSpeedDialScrollview)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.manageSpeedDialToolbar, NavigationIcon.Arrow)
    }

    override fun onStop() {
        super.onStop()
        config.speedDial = Gson().toJson(speedDialValues)
    }

    private fun updateAdapter() {
        SpeedDialAdapter(this, speedDialValues, this, binding.speedDialList) {
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
            binding.speedDialList.adapter = this
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
