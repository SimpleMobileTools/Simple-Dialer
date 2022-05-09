package com.simplemobiletools.dialer.helpers

import android.content.Context
import android.net.Uri
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.extensions.getPhoneNumberTypeText
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.dialer.models.CallContact

// inspired by https://github.com/Chooloo/call_manage
class CallManager {
    companion object {
        var call: Call? = null
        var inCallService: InCallService? = null

        fun accept() {
            call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun reject() {
            if (call != null) {
                if (call!!.state == Call.STATE_RINGING) {
                    call!!.reject(false, null)
                } else {
                    call!!.disconnect()
                }
            }
        }

        fun registerCallback(callback: Call.Callback) {
            call?.registerCallback(callback)
        }

        fun unregisterCallback(callback: Call.Callback) {
            call?.unregisterCallback(callback)
        }

        fun getState() = if (call == null) {
            Call.STATE_DISCONNECTED
        } else {
            call!!.state
        }

        fun keypad(c: Char) {
            call?.playDtmfTone(c)
            call?.stopDtmfTone()
        }

        fun getCallContact(context: Context, callback: (CallContact?) -> Unit) {
            val privateCursor = context.getMyContactsCursor(false, true)
            ensureBackgroundThread {
                val callContact = CallContact("", "", "", "")
                val handle = try {
                    call?.details?.handle?.toString()
                } catch (e: NullPointerException) {
                    null
                }

                if (handle == null) {
                    callback(callContact)
                    return@ensureBackgroundThread
                }

                val uri = Uri.decode(handle)
                if (uri.startsWith("tel:")) {
                    val number = uri.substringAfter("tel:")
                    SimpleContactsHelper(context).getAvailableContacts(false) { contacts ->
                        val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
                        if (privateContacts.isNotEmpty()) {
                            contacts.addAll(privateContacts)
                        }

                        val contactsWithMultipleNumbers = contacts.filter { it.phoneNumbers.size > 1 }
                        val numbersToContactIDMap = HashMap<String, Int>()
                        contactsWithMultipleNumbers.forEach { contact ->
                            contact.phoneNumbers.forEach { phoneNumber ->
                                numbersToContactIDMap[phoneNumber.value] = contact.contactId
                                numbersToContactIDMap[phoneNumber.normalizedNumber] = contact.contactId
                            }
                        }

                        callContact.number = number
                        val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                        if (contact != null) {
                            callContact.name = contact.name
                            callContact.photoUri = contact.photoUri

                            if (contact.phoneNumbers.size > 1) {
                                val specificPhoneNumber = contact.phoneNumbers.firstOrNull { it.value == number }
                                if (specificPhoneNumber != null) {
                                    callContact.numberLabel = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                                }
                            }
                        } else {
                            callContact.name = number
                        }
                        callback(callContact)
                    }
                }
            }
        }

        fun getCallDuration(): Int {
            return if (call != null) {
                ((System.currentTimeMillis() - call!!.details.connectTimeMillis) / 1000).toInt()
            } else {
                0
            }
        }
    }
}
