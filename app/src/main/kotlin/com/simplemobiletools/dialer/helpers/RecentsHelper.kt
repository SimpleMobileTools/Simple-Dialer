package com.simplemobiletools.dialer.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog.Calls
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_CALL_LOG
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.getQuestionMarks
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.extensions.getAvailableSIMCardLabels
import com.simplemobiletools.dialer.models.RecentCall

class RecentsHelper(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun getRecentCalls(callback: (ArrayList<RecentCall>) -> Unit) {
        ensureBackgroundThread {
            var recentCalls = ArrayList<RecentCall>()
            if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
                callback(recentCalls)
                return@ensureBackgroundThread
            }

            val uri = Calls.CONTENT_URI
            val projection = arrayOf(
                Calls._ID,
                Calls.NUMBER,
                Calls.CACHED_NAME,
                Calls.CACHED_PHOTO_URI,
                Calls.DATE,
                Calls.DURATION,
                Calls.TYPE,
                "phone_account_address"
            )

            val numberToSimIDMap = HashMap<String, Int>()
            context.getAvailableSIMCardLabels().forEach {
                numberToSimIDMap[it.phoneNumber] = it.id
            }

            val sortOrder = "${Calls._ID} DESC LIMIT 100"

            var previousRecentCallFrom = ""
            context.queryCursor(uri, projection, sortOrder = sortOrder, showErrors = true) { cursor ->
                val id = cursor.getIntValue(Calls._ID)
                val number = cursor.getStringValue(Calls.NUMBER)
                val name = cursor.getStringValue(Calls.CACHED_NAME) ?: number
                val photoUri = cursor.getStringValue(Calls.CACHED_PHOTO_URI) ?: ""
                val startTS = (cursor.getLongValue(Calls.DATE) / 1000L).toInt()
                val duration = cursor.getIntValue(Calls.DURATION)
                val type = cursor.getIntValue(Calls.TYPE)
                val accountAddress = cursor.getStringValue("phone_account_address")
                val simID = numberToSimIDMap[accountAddress] ?: 1
                val neighbourIDs = ArrayList<Int>()
                val recentCall = RecentCall(id, number, name, photoUri, startTS, duration, type, neighbourIDs, simID)

                // if we have 3 missed calls from the same number, show it just once
                if ("$number$name" != previousRecentCallFrom) {
                    recentCalls.add(recentCall)
                } else {
                    recentCalls.lastOrNull()?.neighbourIDs?.add(id)
                }

                previousRecentCallFrom = "$number$name"
            }

            val blockedNumbers = context.getBlockedNumbers()
            recentCalls = recentCalls.filter { !context.isNumberBlocked(it.phoneNumber, blockedNumbers) }.toMutableList() as ArrayList<RecentCall>
            callback(recentCalls)
        }
    }

    @SuppressLint("MissingPermission")
    fun removeRecentCalls(ids: ArrayList<Int>, callback: () -> Unit) {
        ensureBackgroundThread {
            val uri = Calls.CONTENT_URI
            ids.chunked(30).forEach { chunk ->
                val selection = "${Calls._ID} IN (${getQuestionMarks(chunk.size)})"
                val selectionArgs = chunk.map { it.toString() }.toTypedArray()
                context.contentResolver.delete(uri, selection, selectionArgs)
            }
            callback()
        }
    }
}
