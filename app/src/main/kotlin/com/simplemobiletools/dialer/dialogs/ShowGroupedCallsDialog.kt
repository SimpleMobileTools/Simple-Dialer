package com.simplemobiletools.dialer.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.RecentCallsAdapter
import com.simplemobiletools.dialer.databinding.DialogShowGroupedCallsBinding
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.models.RecentCall

class ShowGroupedCallsDialog(val activity: BaseSimpleActivity, callIds: ArrayList<Int>) {
    private var dialog: AlertDialog? = null
    private val binding: DialogShowGroupedCallsBinding = DialogShowGroupedCallsBinding.inflate(activity.layoutInflater, null, false)

    init {
        with(binding) {
            root.apply {
                RecentsHelper(activity).getRecentCalls(false) { allRecents ->
                    val recents = allRecents.filter { callIds.contains(it.id) }.toMutableList() as ArrayList<RecentCall>
                    activity.runOnUiThread {
                        RecentCallsAdapter(activity as SimpleActivity, recents, binding.selectGroupedCallsList, null, false) {
                        }.apply {
                            selectGroupedCallsList.adapter = this
                        }
                    }
                }
            }

            activity.getAlertDialogBuilder()
                .apply {
                    activity.setupDialogStuff(root, this) { alertDialog ->
                        dialog = alertDialog
                    }
                }
        }
    }
}
