package com.simplemobiletools.dialer.helpers

import java.util.Timer
import java.util.TimerTask

class CallDurationHelper {
    private var callTimer: Timer? = null
    private var callDuration = 0
    private var callback: ((durationSecs: Int) -> Unit)? = null

    fun onDurationChange(callback: (durationSecs: Int) -> Unit) {
        this.callback = callback
    }

    fun start() {
        try {
            callDuration = 0
            callTimer = Timer()
            callTimer?.scheduleAtFixedRate(getTimerUpdateTask(), 1000, 1000)
        } catch (ignored: Exception) {
        }
    }

    fun cancel() {
        callTimer?.cancel()
    }

    private fun getTimerUpdateTask() = object : TimerTask() {
        override fun run() {
            callDuration++
            callback?.invoke(callDuration)
        }
    }
}
