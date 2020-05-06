package com.simplemobiletools.dialer.extensions

import android.content.Context
import com.simplemobiletools.dialer.helpers.Config

val Context.config: Config get() = Config.newInstance(applicationContext)
