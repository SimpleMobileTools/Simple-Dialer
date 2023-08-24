package com.simplemobiletools.dialer.adapters

import android.view.Menu
import android.view.ViewGroup
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.databinding.ItemSpeedDialBinding
import com.simplemobiletools.dialer.interfaces.RemoveSpeedDialListener
import com.simplemobiletools.dialer.models.SpeedDial

class SpeedDialAdapter(
    activity: SimpleActivity, var speedDialValues: List<SpeedDial>, private val removeListener: RemoveSpeedDialListener,
    recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_delete_only

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> deleteSpeedDial()
        }
    }

    override fun getSelectableItemCount() = speedDialValues.size

    override fun getIsItemSelectable(position: Int) = speedDialValues[position].isValid()

    override fun getItemSelectionKey(position: Int) = speedDialValues.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = speedDialValues.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemSpeedDialBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val speedDial = speedDialValues[position]
        holder.bindView(speedDial, true, true) { itemView, layoutPosition ->
            val binding = ItemSpeedDialBinding.bind(itemView)
            setupView(binding, speedDial)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = speedDialValues.size

    private fun getSelectedItems() = speedDialValues.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<SpeedDial>

    private fun deleteSpeedDial() {
        val ids = getSelectedItems().map { it.id }.toMutableList() as ArrayList<Int>
        removeListener.removeSpeedDial(ids)
        finishActMode()
    }

    private fun setupView(binding: ItemSpeedDialBinding, speedDial: SpeedDial) {
        binding.apply {
            var displayName = "${speedDial.id}. "
            displayName += if (speedDial.isValid()) speedDial.displayName else ""

            speedDialLabel.apply {
                text = displayName
                isSelected = selectedKeys.contains(speedDial.hashCode())
                setTextColor(textColor)
            }
        }
    }
}
