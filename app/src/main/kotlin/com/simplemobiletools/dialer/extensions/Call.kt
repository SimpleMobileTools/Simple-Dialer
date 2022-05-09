package com.simplemobiletools.dialer.extensions

import android.os.Build
import android.telecom.Call
import android.telecom.Call.STATE_CONNECTING
import android.telecom.Call.STATE_DIALING
import android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT

private val OUTGOING_CALL_STATES = arrayOf(STATE_CONNECTING, STATE_DIALING, STATE_SELECT_PHONE_ACCOUNT)

fun Call.getStateCompat(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        details.state
    } else {
        @Suppress("DEPRECATION")
        state
    }
}

fun Call.isOutgoing(): Boolean {
    return OUTGOING_CALL_STATES.contains(getStateCompat())
}
