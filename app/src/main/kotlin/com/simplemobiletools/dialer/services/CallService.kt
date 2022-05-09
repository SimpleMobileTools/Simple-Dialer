package com.simplemobiletools.dialer.services

import android.telecom.Call
import android.telecom.InCallService
import com.simplemobiletools.dialer.App
import com.simplemobiletools.dialer.activities.CallActivity
import com.simplemobiletools.dialer.extensions.powerManager
import com.simplemobiletools.dialer.helpers.CallManager
import com.simplemobiletools.dialer.helpers.CallNotificationManager

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }
    private val callDurationHelper by lazy { (application as App).callDurationHelper }

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state != Call.STATE_DISCONNECTED) {
                callNotificationManager.setupNotification()
            }

            if (state == Call.STATE_ACTIVE) {
                callDurationHelper.start()
            } else if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callDurationHelper.cancel()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        if (!powerManager.isInteractive || call.state == Call.STATE_CONNECTING) {
            startActivity(CallActivity.getStartIntent(this))
        }
        CallManager.call = call
        CallManager.inCallService = this
        CallManager.registerCallback(callListener)
        callNotificationManager.setupNotification()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.call = null
        CallManager.inCallService = null
        callNotificationManager.cancelNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.unregisterCallback(callListener)
        callNotificationManager.cancelNotification()
        callDurationHelper.cancel()
    }
}
