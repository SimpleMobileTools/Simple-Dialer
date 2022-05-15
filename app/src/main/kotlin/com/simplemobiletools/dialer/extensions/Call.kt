package com.simplemobiletools.dialer.extensions

import android.telecom.Call
import android.telecom.Call.STATE_CONNECTING
import android.telecom.Call.STATE_DIALING
import android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT
import com.simplemobiletools.commons.helpers.isSPlus

private val OUTGOING_CALL_STATES = arrayOf(STATE_CONNECTING, STATE_DIALING, STATE_SELECT_PHONE_ACCOUNT)

@Suppress("DEPRECATION")
fun Call?.getStateCompat(): Int {
    return if (this == null) {
        Call.STATE_DISCONNECTED
    } else if (isSPlus()) {
        details.state
    } else {
        state
    }
}

fun Call.isOutgoing(): Boolean {
    return OUTGOING_CALL_STATES.contains(getStateCompat())
}

fun Call.hasCapability(capability: Int): Boolean = details.callCapabilities and capability != 0

fun Call.hasProperty(property: Int): Boolean = details.hasProperty(property)

fun Call?.isConference(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
