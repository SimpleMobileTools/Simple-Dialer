package com.simplemobiletools.dialer.services

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.simplemobiletools.dialer.activities.CallActivity
import com.simplemobiletools.dialer.helpers.CallManager
import com.simplemobiletools.dialer.helpers.CallNotificationManager

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }
    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            callNotificationManager.setupNotification()
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val intent = Intent(this, CallActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
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
        CallManager.registerCallback(callListener)
        callNotificationManager.cancelNotification()
    }
}
