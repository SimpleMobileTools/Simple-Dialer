package com.simplemobiletools.dialer.dialogs

import android.annotation.SuppressLint
import android.telecom.PhoneAccountHandle
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.extensions.getAvailableSIMCardLabels
import kotlinx.android.synthetic.main.dialog_select_sim.view.*

@SuppressLint("MissingPermission")
class SelectSIMDialog(val activity: BaseSimpleActivity, val phoneNumber: String, val callback: (handle: PhoneAccountHandle?) -> Unit) {
    private var dialog: AlertDialog? = null
    private val view = activity.layoutInflater.inflate(R.layout.dialog_select_sim, null)

    init {
        val radioGroup = view.select_sim_radio_group
        view.apply {
            select_sim_remember_holder.setOnClickListener {
                select_sim_remember.toggle()
            }
        }

        activity.getAvailableSIMCardLabels().forEachIndexed { index, SIMAccount ->
            val radioButton = (activity.layoutInflater.inflate(R.layout.radio_button, null) as RadioButton).apply {
                text = "${index + 1} - ${SIMAccount.label}"
                id = index
                setOnClickListener { selectedSIM(SIMAccount.handle, SIMAccount.label) }
            }
            radioGroup!!.addView(radioButton, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        dialog = AlertDialog.Builder(activity)
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun selectedSIM(handle: PhoneAccountHandle, label: String) {
        if (view.select_sim_remember.isChecked) {
            activity.config.saveCustomSIM(phoneNumber, label)
        }

        callback(handle)
        dialog?.dismiss()
    }
}
