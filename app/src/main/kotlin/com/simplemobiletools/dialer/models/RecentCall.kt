package com.simplemobiletools.dialer.models

import android.telephony.PhoneNumberUtils
import com.simplemobiletools.commons.extensions.normalizePhoneNumber

// model used at displaying recent calls, for contacts with multiple numbers specifify the number and type
data class RecentCall(
    val id: Int,
    val phoneNumber: String,
    val name: String,
    val photoUri: String,
    val startTS: Int,
    val duration: Int,
    val type: Int,
    val neighbourIDs: ArrayList<Int>,
    val simID: Int,
    val specificNumber: String,
    val specificType: String,
    val isUnknownNumber: Boolean,
) {
    fun doesContainPhoneNumber(text: String): Boolean {
        val normalizedText = text.normalizePhoneNumber()
        return PhoneNumberUtils.compare(phoneNumber.normalizePhoneNumber(), normalizedText) ||
            phoneNumber.contains(text) ||
            phoneNumber.normalizePhoneNumber().contains(normalizedText) ||
            phoneNumber.contains(normalizedText)
    }
}
