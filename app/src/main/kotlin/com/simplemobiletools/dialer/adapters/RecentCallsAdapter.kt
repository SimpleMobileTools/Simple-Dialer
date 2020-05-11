package com.simplemobiletools.dialer.adapters

import android.graphics.drawable.Drawable
import android.provider.CallLog.Calls
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.models.RecentCall
import kotlinx.android.synthetic.main.item_recent_call.view.*
import java.util.*

class RecentCallsAdapter(activity: SimpleActivity, var recentCalls: ArrayList<RecentCall>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private lateinit var incomingCallIcon: Drawable
    private lateinit var outgoingCallIcon: Drawable
    private var fontSize = activity.getTextSize()

    init {
        initDrawables()
    }

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = recentCalls.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recentCalls.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recentCalls.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_recent_call, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recentCall = recentCalls[position]
        holder.bindView(recentCall, true, false) { itemView, layoutPosition ->
            setupView(itemView, recentCall)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recentCalls.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.item_recents_image)
        }
    }

    fun initDrawables() {
        incomingCallIcon = activity.resources.getColoredDrawableWithColor(R.drawable.ic_incoming_call_vector, activity.config.textColor)
        outgoingCallIcon = activity.resources.getColoredDrawableWithColor(R.drawable.ic_outgoing_call_vector, activity.config.textColor)
    }

    fun updateItems(newItems: ArrayList<RecentCall>) {
        if (newItems.hashCode() != recentCalls.hashCode()) {
            recentCalls = newItems.clone() as ArrayList<RecentCall>
            notifyDataSetChanged()
            finishActMode()
        }
    }

    private fun setupView(view: View, call: RecentCall) {
        view.apply {
            item_recents_name.apply {
                text = call.name
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }

            item_recents_date_time.apply {
                text = call.startTS.formatDateOrTime(context, true)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            item_recents_duration.apply {
                text = call.duration.getFormattedDuration()
                setTextColor(textColor)
                beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            SimpleContactsHelper(context).loadContactImage(call.photoUri, item_recents_image, call.name)

            val drawable = if (call.type == Calls.OUTGOING_TYPE) {
                outgoingCallIcon
            } else {
                incomingCallIcon
            }

            item_recents_type.setImageDrawable(drawable)
        }
    }
}
