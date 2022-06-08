package com.simplemobiletools.dialer.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.RecentCallsAdapter
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.models.RecentCall
import kotlinx.android.synthetic.main.dialog_show_grouped_calls.view.*

class ShowGroupedCallsDialog(val activity: BaseSimpleActivity, callIds: ArrayList<Int>) {
    private var dialog: AlertDialog? = null
    private var view = activity.layoutInflater.inflate(R.layout.dialog_show_grouped_calls, null)

    init {
        view.apply {
            RecentsHelper(activity).getRecentCalls(false) { allRecents ->
                val recents = allRecents.filter { callIds.contains(it.id) }.toMutableList() as ArrayList<RecentCall>
                activity.runOnUiThread {
                    RecentCallsAdapter(activity as SimpleActivity, recents, select_grouped_calls_list, null, false) {
                    }.apply {
                        select_grouped_calls_list.adapter = this
                    }
                }
            }
        }

        dialog = AlertDialog.Builder(activity)
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
