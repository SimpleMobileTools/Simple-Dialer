package com.simplemobiletools.dialer.activities

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.dialer.R

open class SimpleActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)
}
