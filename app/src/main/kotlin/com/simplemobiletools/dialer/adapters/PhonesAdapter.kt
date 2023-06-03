package com.simplemobiletools.dialer.adapters

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.models.PhoneNumber
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import kotlinx.android.synthetic.main.item_phone_number.view.select_phone_number_holder
import kotlinx.android.synthetic.main.item_phone_number.view.select_phone_number_radio

class PhonesAdapter(
    val activity: SimpleActivity,
    private val phones: ArrayList<PhoneNumber>,
    val onPhoneNumberSelected: (PhoneNumber) -> Unit,
) : RecyclerView.Adapter<PhonesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = activity.layoutInflater.inflate(R.layout.item_phone_number, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val phoneNumber = phones[position]
        holder.bindView(phoneNumber)
    }

    override fun getItemCount() = phones.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(phoneNumber: PhoneNumber): View {
            itemView.apply {
                val displayName = phoneNumber.normalizedNumber
                select_phone_number_radio.text = displayName
                select_phone_number_holder.setOnClickListener { onPhoneNumberSelected(phoneNumber) }
            }
            return itemView
        }
    }
}
