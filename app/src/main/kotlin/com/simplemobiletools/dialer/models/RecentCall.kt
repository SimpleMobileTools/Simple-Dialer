package com.simplemobiletools.dialer.models

import android.telephony.PhoneNumberUtils
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.extensions.normalizePhoneNumber

data class RecentCall(
    var id: Int, var phoneNumber: String, var name: String, var photoUri: String, var startTS: Int, var duration: Int, var type: Int,
    var neighbourIDs: ArrayList<Int>, val simID: Int
) {
    fun doesContainPhoneNumber(text: String): Boolean {
        val normalizedText = text.normalizePhoneNumber()
        return PhoneNumberUtils.compare(phoneNumber.normalizePhoneNumber(), normalizedText) ||
            phoneNumber.contains(text) ||
            phoneNumber.normalizePhoneNumber().contains(normalizedText) ||
            phoneNumber.contains(normalizedText)
    }

    fun getFormattedDuration(hoursString: String, minutesString: String, secondsString: String): String {
        val strings = arrayListOf<String>()
        val hours = duration / 3600
        val minutes = duration % 3600 / 60
        val seconds = duration % 60

        if (hours > 0) {
            strings.add(hoursString.format(hours))
        }
        if (minutes > 0 || hours > 0) {
            strings.add(minutesString.format(minutes))
        }
        strings.add(secondsString.format(seconds))

        return strings.joinToString(separator = " ")
    }
}
