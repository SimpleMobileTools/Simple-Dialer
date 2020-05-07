package com.simplemobiletools.dialer.helpers

import android.content.Context
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.dialer.models.RecentCall

class RecentsHelper(val context: Context) {
    fun getRecentCalls(callback: (ArrayList<RecentCall>) -> Unit) {
        ensureBackgroundThread {
            val recents = ArrayList<RecentCall>()
            callback(recents)
        }
    }
}
