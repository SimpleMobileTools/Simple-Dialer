package com.simplemobiletools.dialer

import android.app.Application
import android.content.Context
import com.simplemobiletools.commons.extensions.checkUseEnglish

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        context = this;
        checkUseEnglish()
    }


    companion object {
        lateinit var context: Context
    }

}
