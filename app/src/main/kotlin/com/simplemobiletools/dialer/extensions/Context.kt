package com.simplemobiletools.dialer.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.simplemobiletools.commons.extensions.telecomManager
import com.simplemobiletools.dialer.helpers.Config
import com.simplemobiletools.dialer.models.SIMAccount

val Context.config: Config get() = Config.newInstance(applicationContext)

@SuppressLint("MissingPermission")
fun Context.getAvailableSIMCardLabels(): ArrayList<SIMAccount> {
    val SIMAccounts = ArrayList<SIMAccount>()
    telecomManager.callCapablePhoneAccounts.forEach { account ->
        val phoneAccount = telecomManager.getPhoneAccount(account)
        var label = phoneAccount.label.toString()
        var address = phoneAccount.address.toString()
        if (address.startsWith("tel:") && address.substringAfter("tel:").isNotEmpty()) {
            address = Uri.decode(address.substringAfter("tel:"))
            label += " ($address)"
        }
        val SIM = SIMAccount(phoneAccount.accountHandle, label)
        SIMAccounts.add(SIM)
    }
    return SIMAccounts
}
