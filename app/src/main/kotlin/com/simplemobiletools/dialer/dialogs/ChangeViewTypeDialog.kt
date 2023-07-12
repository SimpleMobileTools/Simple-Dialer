package com.simplemobiletools.dialer.dialogs

import android.view.View
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.VIEW_TYPE_GRID
import com.simplemobiletools.commons.helpers.VIEW_TYPE_LIST
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.GRID_MAX_SPAN_COUNT
import com.simplemobiletools.dialer.helpers.GRID_MIN_SPAN_COUNT
import kotlinx.android.synthetic.main.dialog_change_view_type.view.change_view_type_dialog_radio
import kotlinx.android.synthetic.main.dialog_change_view_type.view.change_view_type_dialog_radio_grid
import kotlinx.android.synthetic.main.dialog_change_view_type.view.change_view_type_dialog_radio_list
import kotlinx.android.synthetic.main.dialog_change_view_type.view.grid_span_count_slider

class ChangeViewTypeDialog(val activity: BaseSimpleActivity, val path: String = "", showFolderCheck: Boolean = true, val callback: () -> Unit) {
    private var view: View
    private var config = activity.config

    init {
        view = activity.layoutInflater.inflate(R.layout.dialog_change_view_type, null).apply {
            val viewToCheck = when (config.viewType) {
                VIEW_TYPE_GRID -> change_view_type_dialog_radio_grid.id
                else -> change_view_type_dialog_radio_list.id
            }

            change_view_type_dialog_radio_grid.setOnCheckedChangeListener { buttonView, isChecked ->
                grid_span_count_slider.beVisibleIf(isChecked)
            }
            grid_span_count_slider.value = config.gridLayoutSpanCount.toFloat()
            grid_span_count_slider.stepSize = 1f
            grid_span_count_slider.valueFrom = GRID_MIN_SPAN_COUNT.toFloat()
            grid_span_count_slider.valueTo = GRID_MAX_SPAN_COUNT.toFloat()

            change_view_type_dialog_radio.check(viewToCheck)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun dialogConfirmed() {
        val viewType = if (view.change_view_type_dialog_radio_grid.isChecked) {
            config.gridLayoutSpanCount = view.grid_span_count_slider.value.toInt()
            VIEW_TYPE_GRID
        } else {
            VIEW_TYPE_LIST
        }
        config.viewType = viewType
        callback()
    }
}
