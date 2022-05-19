package com.simplemobiletools.dialer.helpers

import android.annotation.SuppressLint
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.simplemobiletools.dialer.extensions.getStateCompat
import com.simplemobiletools.dialer.extensions.hasCapability
import com.simplemobiletools.dialer.extensions.isConference
import java.util.concurrent.CopyOnWriteArraySet

const val TAG = "SimpleDialer:CallManager"
const val TAG2 = TAG /*"SimpleDialer:CallState"*/

// inspired by https://github.com/Chooloo/call_manage
class CallManager {
    companion object {
        @SuppressLint("StaticFieldLeak")
        var inCallService: InCallService? = null
        private var call: Call? = null
        private val calls = mutableListOf<Call>()
        private val listeners = CopyOnWriteArraySet<CallManagerListener>()

        fun onCallAdded(call: Call) {
            this.call = call
            calls.add(call)
            Log.d(TAG, "onCallAdded (${calls.size}): $call")
            for (listener in listeners) {
                listener.onPrimaryCallChanged(call)
            }
            call.registerCallback(object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    Log.d(TAG, "onStateChanged: $call")
                    updateState()
                }

                override fun onDetailsChanged(call: Call, details: Call.Details) {
                    Log.d(TAG, "onDetailsChanged")
                    updateState()
                }

                override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
                    Log.d(TAG, "onConferenceableCallsChanged (${conferenceableCalls.size}): $call")
                    updateState()
                }
            })
        }

        fun onCallRemoved(call: Call) {
            calls.remove(call)
            Log.d(TAG, "onCallRemoved (${calls.size}): $call")
            updateState()
        }

        fun getPhoneState(): PhoneState {
            return when (calls.size) {
                0 -> {
                    Log.d(TAG2, "No call")
                    NoCall
                }
                1 -> {
                    Log.d(TAG2, "Single call")
                    SingleCall(calls.first())
                }
                2 -> {
                    Log.d(TAG2, "Two calls")
                    val active = calls.find { it.getStateCompat() == Call.STATE_ACTIVE }
                    val newCall = calls.find { it.getStateCompat() == Call.STATE_CONNECTING || it.getStateCompat() == Call.STATE_DIALING }
                    val onHold = calls.find { it.getStateCompat() == Call.STATE_HOLDING }
                    if (active != null && newCall != null) {
                        TwoCalls(newCall, active)
                    } else if (newCall != null && onHold != null) {
                        TwoCalls(newCall, onHold)
                    } else if (active != null && onHold != null) {
                        TwoCalls(active, onHold)
                    } else {
                        TwoCalls(calls[0], calls[1])
                    }
                }
                else -> {
                    val conference = calls.find { it.isConference() }!!
                    val secondCall = if (conference.children.size + 1 != calls.size) {
                        calls.filter { !it.isConference() }
                            .subtract(conference.children.toSet())
                            .firstOrNull()
                    } else {
                        null
                    }
                    Log.d(TAG2, "Conference call (${conference.children.size} children)")
                    Log.d(TAG2, "secondCall: $secondCall")
                    if (secondCall == null) {
                        Log.d(TAG2, "Conference call (single)")
                        SingleCall(conference)
                    } else {
                        val newCallState = secondCall.getStateCompat()
                        if (newCallState == Call.STATE_ACTIVE || newCallState == Call.STATE_CONNECTING || newCallState == Call.STATE_DIALING) {
                            Log.d(TAG2, "Conference call and regular call (conference on hold)")
                            TwoCalls(secondCall, conference)
                        } else {
                            Log.d(TAG2, "Conference call and regular call (regular call on hold)")
                            TwoCalls(conference, secondCall)
                        }
                    }
                }
            }
        }

        private fun updateState() {
            val primaryCall = when (val phoneState = getPhoneState()) {
                is NoCall -> null
                is SingleCall -> phoneState.call
                is TwoCalls -> phoneState.active
            }
            var notify = true
            if (primaryCall == null) {
                call = null
            } else if (primaryCall != call) {
                call = primaryCall
                for (listener in listeners) {
                    listener.onPrimaryCallChanged(primaryCall)
                }
                notify = false
            }
            if (notify) {
                for (listener in listeners) {
                    listener.onStateChanged()
                }
            }
        }

        fun getPrimaryCall(): Call? {
            return call
        }

        fun getConferenceCalls(): List<Call> {
            return calls.find { it.isConference() }?.children ?: emptyList()
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

        fun swap() {
            if (calls.size > 1) {
                calls.find { it.getStateCompat() == Call.STATE_HOLDING }?.unhold()
            }
        }

        fun merge() {
            val conferenceableCalls = call!!.conferenceableCalls
            if (conferenceableCalls.isNotEmpty()) {
                call!!.conference(conferenceableCalls.first())
            } else {
                if (call!!.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                    call!!.mergeConference()
                }
            }
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
    }
}

interface CallManagerListener {
    fun onStateChanged()
    fun onPrimaryCallChanged(call: Call)
}

sealed class PhoneState
object NoCall : PhoneState()
class SingleCall(val call: Call) : PhoneState()
class TwoCalls(val active: Call, val onHold: Call) : PhoneState()
