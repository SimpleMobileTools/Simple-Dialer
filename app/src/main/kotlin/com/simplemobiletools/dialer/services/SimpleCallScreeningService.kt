package com.simplemobiletools.dialer.services

import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.helpers.SimpleContactsHelper

@RequiresApi(Build.VERSION_CODES.N)
class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val simpleContactsHelper = SimpleContactsHelper(this)
        val number = Uri.decode(callDetails.handle.toString()).substringAfter("tel:").replace("+", "")
        val isBlocked = baseConfig.blockUnknownNumbers && !simpleContactsHelper.exists(number)
        val response = CallResponse.Builder()
            .setDisallowCall(isBlocked)
            .setRejectCall(isBlocked)
            .setSkipCallLog(isBlocked)
            .setSkipNotification(isBlocked)
            .build()
        respondToCall(callDetails, response)
    }
}
