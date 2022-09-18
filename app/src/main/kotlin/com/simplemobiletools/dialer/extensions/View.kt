package com.simplemobiletools.dialer.extensions

import android.graphics.Rect
import android.view.View
import android.widget.EditText

val View.boundingBox
    get() = Rect().also { getGlobalVisibleRect(it) }

fun EditText.disableKeyboard() {
    showSoftInputOnFocus = false
}
