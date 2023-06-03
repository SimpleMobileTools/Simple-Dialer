package com.simplemobiletools.dialer.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.models.PhoneNumber
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.PhonesAdapter
import kotlinx.android.synthetic.main.dialog_select_phone_number.view.select_phone_number_list

class SelectNumberDialog(val activity: SimpleActivity, private val phoneNumbers: ArrayList<PhoneNumber>, val callback: (selectedNumber: PhoneNumber) -> Unit) {
    private var dialog: AlertDialog? = null
    private var view = activity.layoutInflater.inflate(R.layout.dialog_select_phone_number, null)

    init {
        view.apply {
            select_phone_number_list.adapter = PhonesAdapter(activity, phoneNumbers) {
                callback(it)
                dialog?.dismiss()
            }
        }

        activity.getAlertDialogBuilder().setNegativeButton(R.string.cancel, null).apply {
            activity.setupDialogStuff(view, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }
}
