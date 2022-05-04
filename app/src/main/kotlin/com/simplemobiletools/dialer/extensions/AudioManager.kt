package com.simplemobiletools.dialer.extensions

import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isSPlus

fun AudioManager.hasExternalSpeaker(): Boolean {
    val audioDevices = getDevices(AudioManager.GET_DEVICES_INPUTS)
    if (isSPlus()) {
        if (audioDevices.find {
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            } != null) {
            return true
        }
    }
    if (isOreoPlus()) {
        if (audioDevices.find {
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            } != null) {
            return true
        }
    }
    if (audioDevices.find {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        } != null) {
        return true
    }
    return false
}
