package com.simplemobiletools.dialer.helpers

import android.content.res.Resources
import com.simplemobiletools.dialer.App
import com.simplemobiletools.dialer.R
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashMap

private class PhoneNumberMap {
    companion object {
        fun init(): HashMap<Char, String> {
            val keymap = HashMap<Char, String>()

            // init hashmap for keypad
            val map: HashMap<String, String> = hashMapOf(
                "2" to App.context.getString(R.string.dial_two),
                "3" to App.context.getString(R.string.dial_three),
                "4" to App.context.getString(R.string.dial_four),
                "5" to App.context.getString(R.string.dial_five),
                "6" to App.context.getString(R.string.dial_six),
                "7" to App.context.getString(R.string.dial_seven),
                "8" to App.context.getString(R.string.dial_eight),
                "9" to App.context.getString(R.string.dial_nine)
            )

            for ((key, value) in map) {
                for (i in value.indices) {
                    keymap[value[i]] = key
                }
            }

            return keymap
        }
    }
}

class ExtPhoneNumberUtils {
    companion object {
        private val keymap = PhoneNumberMap.init()

        fun convertKeypadLettersToDigits(input: String) : String {
            val result = StringBuilder()
            for (c in input.toUpperCase(Locale.getDefault())) {
                if (c in keymap) {
                    result.append(keymap[c])
                }
            }
            return result.toString()
        }
    }
}
