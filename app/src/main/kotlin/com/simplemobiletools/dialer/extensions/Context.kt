package com.simplemobiletools.dialer.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.telephony.SubscriptionManager
import com.simplemobiletools.commons.extensions.telecomManager
import com.simplemobiletools.dialer.helpers.Config
import com.simplemobiletools.dialer.models.SIMAccount

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.audioManager: AudioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

@SuppressLint("MissingPermission")
fun Context.getAvailableSIMCardLabels(): ArrayList<SIMAccount> {
    val SIMAccounts = ArrayList<SIMAccount>()
    try {
        telecomManager.callCapablePhoneAccounts.forEachIndexed { index, account ->
            val phoneAccount = telecomManager.getPhoneAccount(account)
            var label = phoneAccount.label.toString()
            var address = phoneAccount.address.toString()
            if (address.startsWith("tel:") && address.substringAfter("tel:").isNotEmpty()) {
                address = Uri.decode(address.substringAfter("tel:"))
                label += " ($address)"
            }

            val SIM = SIMAccount(index + 1, phoneAccount.accountHandle, label, address.substringAfter("tel:"))
            SIMAccounts.add(SIM)
        }
    } catch (ignored: Exception) {
    }
    return SIMAccounts
}

@SuppressLint("MissingPermission")
fun Context.areMultipleSIMsAvailable(): Boolean {
    return try {
        telecomManager.callCapablePhoneAccounts.size > 1
    } catch (ignored: Exception) {
        false
    }
}

@SuppressLint("MissingPermission")
fun Context.getSimIds(): List<String> {
    val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    val availableSIMs = subscriptionManager.activeSubscriptionInfoList
    val simIds = mutableListOf<String>()
    if (availableSIMs.size > 0) {
        for (subscriptionInfo in availableSIMs) {
            simIds.add(subscriptionInfo.iccId)
        }
    }
    return simIds
}
