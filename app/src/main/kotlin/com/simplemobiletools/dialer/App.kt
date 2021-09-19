package com.simplemobiletools.dialer

import android.app.Application
import com.simplemobiletools.commons.extensions.checkUseEnglish
import com.simplemobiletools.dialer.helpers.CallDurationHelper

class App : Application() {
    val callDurationHelper by lazy { CallDurationHelper() }
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
    }
}
