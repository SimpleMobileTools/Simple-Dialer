package com.simplemobiletools.dialer.dialogs

import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.simplemobiletools.commons.adapters.SimpleListItemAdapter
import com.simplemobiletools.commons.fragments.BaseBottomSheetDialogFragment
import com.simplemobiletools.commons.models.SimpleListItem
import com.simplemobiletools.dialer.databinding.LayoutSimpleRecyclerViewBinding

// same as BottomSheetChooserDialog but with dynamic updates
class DynamicBottomSheetChooserDialog : BaseBottomSheetDialogFragment() {

    var onItemClick: ((SimpleListItem) -> Unit)? = null
    private lateinit var binding : LayoutSimpleRecyclerViewBinding
    override fun setupContentView(parent: ViewGroup) {
        binding = LayoutSimpleRecyclerViewBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        @Suppress("UNCHECKED_CAST")
        val listItems = arguments?.getParcelableArray(ITEMS) as Array<SimpleListItem>
        getRecyclerViewAdapter().submitList(listItems.toList())
    }

    private fun getRecyclerViewAdapter(): SimpleListItemAdapter {

        var adapter = binding.recyclerView.adapter as? SimpleListItemAdapter
        if (adapter == null) {
            adapter = SimpleListItemAdapter(requireActivity()) {
                onItemClick?.invoke(it)
                dismissAllowingStateLoss()
            }
            binding.recyclerView.adapter = adapter
        }
        return adapter
    }

    fun updateChooserItems(newItems: Array<SimpleListItem>) {
        if (isAdded) {
            getRecyclerViewAdapter().submitList(newItems.toList())
        }
    }

    companion object {
        private const val TAG = "BottomSheetChooserDialog"
        private const val ITEMS = "data"

        fun createChooser(
            fragmentManager: FragmentManager,
            title: Int?,
            items: Array<SimpleListItem>,
            callback: (SimpleListItem) -> Unit
        ): DynamicBottomSheetChooserDialog {
            val extras = Bundle().apply {
                if (title != null) {
                    putInt(BOTTOM_SHEET_TITLE, title)
                }
                putParcelableArray(ITEMS, items)
            }
            return DynamicBottomSheetChooserDialog().apply {
                arguments = extras
                onItemClick = callback
                show(fragmentManager, TAG)
            }
        }
    }
}
