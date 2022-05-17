package com.simplemobiletools.dialer.services

import android.app.KeyguardManager
import android.content.Context
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.simplemobiletools.dialer.activities.CallActivity
import com.simplemobiletools.dialer.extensions.getStateCompat
import com.simplemobiletools.dialer.extensions.isOutgoing
import com.simplemobiletools.dialer.extensions.powerManager
import com.simplemobiletools.dialer.helpers.CallManager
import com.simplemobiletools.dialer.helpers.CallNotificationManager

const val TAG = "SimpleDialer:CallService"

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state != Call.STATE_DISCONNECTED) {
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        val isScreenLocked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        if (!powerManager.isInteractive || call.isOutgoing() || isScreenLocked) {
            startActivity(CallActivity.getStartIntent(this))
            if (call.getStateCompat() != Call.STATE_RINGING) {
                callNotificationManager.setupNotification()
            }
        } else {
            callNotificationManager.setupNotification()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: $call")
        call.unregisterCallback(callListener)
        CallManager.onCallRemoved(call)
        if (CallManager.calls.isEmpty()) {
            CallManager.call = null
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            startActivity(CallActivity.getStartIntent(this))
        }
        Log.d(TAG, "onCallRemoved: calls=${CallManager.calls.size}")
    }

    override fun onDestroy() {
        super.onDestroy()
        callNotificationManager.cancelNotification()
    }
}
