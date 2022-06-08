package com.simplemobiletools.dialer.services

import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.helpers.SimpleContactsHelper

@RequiresApi(Build.VERSION_CODES.N)
class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        if (baseConfig.blockUnknownNumbers) {
            val simpleContactsHelper = SimpleContactsHelper(this)
            val number = Uri.decode(callDetails.handle?.toString()).substringAfter("tel:")
            val privateCursor = getMyContactsCursor(false, true)
            simpleContactsHelper.exists(number, privateCursor) { exists ->
                respondToCall(callDetails, !exists)
            }
        } else {
            respondToCall(callDetails, false)
        }
    }

    private fun respondToCall(callDetails: Call.Details, isBlocked: Boolean) {
        val response = CallResponse.Builder()
            .setDisallowCall(isBlocked)
            .setRejectCall(isBlocked)
            .setSkipCallLog(isBlocked)
            .setSkipNotification(isBlocked)
            .build()

        respondToCall(callDetails, response)
    }
}
