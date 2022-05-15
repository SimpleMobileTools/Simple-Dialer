package com.simplemobiletools.dialer.helpers

import android.content.Context
import android.net.Uri
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.extensions.getPhoneNumberTypeText
import com.simplemobiletools.commons.helpers.MyContactsContentProvider
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.dialer.extensions.getStateCompat
import com.simplemobiletools.dialer.models.CallContact
import java.util.concurrent.CopyOnWriteArraySet

const val TAG = "SimpleDialer:CallManager"

// inspired by https://github.com/Chooloo/call_manage
class CallManager {
    companion object {
        var call: Call? = null
        var inCallService: InCallService? = null
        val calls = mutableListOf<Call>()
        private val listeners = CopyOnWriteArraySet<CallManagerListener>()

        fun onCallAdded(call: Call) {
            this.call = call
            calls.add(call)
            call.registerCallback(object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    Log.d(TAG, "onStateChanged: $call")
                    for (listener in listeners) {
                        listener.onStateChanged(call, state)
                    }
                    if (state == Call.STATE_HOLDING && calls.size > 1) {
                        for (listener in listeners) {
                            listener.onCallPutOnHold(call)
                        }
                    }
                    if (state == Call.STATE_ACTIVE && calls.size == 1) {
                        for (listener in listeners) {
                            listener.onCallPutOnHold(null)
                        }
                    }
                    if ((state == Call.STATE_CONNECTING || state == Call.STATE_DIALING || state == Call.STATE_ACTIVE) && calls.size > 1) {
                        if (CallManager.call != call) {
                            CallManager.call = call
                            Log.d(TAG, "onCallsChanged")
                            for (listener in listeners) {
                                listener.onCallsChanged(call, getSecondaryCall())
                            }
                        }
                    }
                }
            })
        }

        fun onCallRemoved(call: Call) {
            calls.remove(call)
        }

        fun getPrimaryCall(): Call? {
            return call
        }

        fun getSecondaryCall(): Call? {
            if (calls.size == 1) {
                return null
            }
            return calls.find { it.getStateCompat() == Call.STATE_HOLDING }
        }

        fun accept() {
            call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun reject() {
            if (call != null) {
                if (getState() == Call.STATE_RINGING) {
                    call!!.reject(false, null)
                } else {
                    call!!.disconnect()
                }
            }
        }

        fun toggleHold(): Boolean {
            val isOnHold = getState() == Call.STATE_HOLDING
            if (isOnHold) {
                call?.unhold()
            } else {
                call?.hold()
            }
            return !isOnHold
        }

        val isConference: Boolean
            get() = call?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) ?: false

        fun swap() {
            getSecondaryCall()?.unhold()
        }

        fun merge() {
//            val conferenceableCalls = call!!.conferenceableCalls
//            if (conferenceableCalls.isNotEmpty()) {
//                call!!.conference(conferenceableCalls.first())
//            } else {
//                if (call!!.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
//                    call!!.mergeConference()
//                }
//            }
        }

        fun addListener(listener: CallManagerListener) {
            listeners.add(listener)
        }

        fun removeListener(listener: CallManagerListener) {
            listeners.remove(listener)
        }

        fun getState() = getPrimaryCall()?.getStateCompat()

        fun keypad(c: Char) {
            call?.playDtmfTone(c)
            call?.stopDtmfTone()
        }

        fun getCallContact(context: Context, callback: (CallContact?) -> Unit) {
            return getCallContact(context, call, callback)
        }

        fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
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
                val connectTimeMillis = call!!.details.connectTimeMillis
                if (connectTimeMillis == 0L) {
                    return 0
                }
                ((System.currentTimeMillis() - connectTimeMillis) / 1000).toInt()
            } else {
                0
            }
        }
    }
}

interface CallManagerListener {
    fun onStateChanged(call: Call, state: Int)
    fun onCallPutOnHold(call: Call?)
    fun onCallsChanged(active: Call, onHold: Call?)
}
