package com.simplemobiletools.dialer.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_CALL_PHONE
import com.simplemobiletools.commons.helpers.PERMISSION_WRITE_CONTACTS
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.interfaces.ItemMoveCallback
import com.simplemobiletools.commons.interfaces.ItemTouchHelperContract
import com.simplemobiletools.commons.interfaces.StartReorderDragListener
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.extensions.areMultipleSIMsAvailable
import com.simplemobiletools.dialer.extensions.callContactWithSim
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.extensions.startContactDetailsIntent
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import java.util.*

class ContactsAdapter(
    activity: SimpleActivity,
    var contacts: ArrayList<SimpleContact>,
    recyclerView: MyRecyclerView,
    val refreshItemsListener: RefreshItemsListener? = null,
    highlightText: String = "",
    val showDeleteButton: Boolean = true,
    private val enableDrag: Boolean = false,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), ItemTouchHelperContract {

    private var textToHighlight = highlightText
    private var fontSize = activity.getTextSize()
    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener? = null
    var onDragEndListener: (() -> Unit)? = null

    init {
        setupDragListener(true)

        if (enableDrag) {
            touchHelper = ItemTouchHelper(ItemMoveCallback(this))
            touchHelper!!.attachToRecyclerView(recyclerView)

            startReorderDragListener = object : StartReorderDragListener {
                override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                    touchHelper?.startDrag(viewHolder)
                }
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_contacts

    override fun prepareActionMode(menu: Menu) {
        val hasMultipleSIMs = activity.areMultipleSIMsAvailable()
        val isOneItemSelected = isOneItemSelected()
        val selectedNumber = "tel:${getSelectedPhoneNumber()}"

        menu.apply {
            findItem(R.id.cab_call_sim_1).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_call_sim_2).isVisible = hasMultipleSIMs && isOneItemSelected
            findItem(R.id.cab_remove_default_sim).isVisible = isOneItemSelected && activity.config.getCustomSIM(selectedNumber) != ""

            findItem(R.id.cab_delete).isVisible = showDeleteButton
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected && isOreoPlus()
            findItem(R.id.cab_view_details).isVisible = isOneItemSelected
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_call_sim_1 -> callContact(true)
            R.id.cab_call_sim_2 -> callContact(false)
            R.id.cab_remove_default_sim -> removeDefaultSIM()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_send_sms -> sendSMS()
            R.id.cab_view_details -> viewContactDetails()
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.rawId

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.rawId == key }

    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_contact_without_number, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, true, true) { itemView, layoutPosition ->
            setupView(itemView, contact, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    fun updateItems(newItems: ArrayList<SimpleContact>, highlightText: String = "") {
        if (newItems.hashCode() != contacts.hashCode()) {
            contacts = newItems.clone() as ArrayList<SimpleContact>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    @SuppressLint("MissingPermission")
    private fun callContact(useSimOne: Boolean) {
        val number = getSelectedPhoneNumber() ?: return
        activity.callContactWithSim(number, useSimOne)
    }

    private fun removeDefaultSIM() {
        val phoneNumber = getSelectedPhoneNumber() ?: return
        activity.config.removeCustomSIM("tel:$phoneNumber")
        finishActMode()
    }

    private fun sendSMS() {
        val numbers = ArrayList<String>()
        getSelectedItems().map { simpleContact ->
            val contactNumbers = simpleContact.phoneNumbers
            val primaryNumber = contactNumbers.firstOrNull { it.isPrimary }
            val normalizedNumber = primaryNumber?.normalizedNumber ?: contactNumbers.firstOrNull()?.normalizedNumber

            if (normalizedNumber != null) {
                numbers.add(normalizedNumber)
            }
        }

        val recipient = TextUtils.join(";", numbers)
        activity.launchSendSMSIntent(recipient)
    }

    private fun viewContactDetails() {
        val contact = getSelectedItems().firstOrNull() ?: return
        activity.startContactDetailsIntent(contact)
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().firstOrNull() ?: return
        val items = if (itemsCnt == 1) {
            "\"${firstItem.name}\""
        } else {
            resources.getQuantityString(R.plurals.delete_contacts, itemsCnt, itemsCnt)
        }

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            activity.handlePermission(PERMISSION_WRITE_CONTACTS) {
                deleteContacts()
            }
        }
    }

    private fun deleteContacts() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val contactsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        contacts.removeAll(contactsToRemove)
        val idsToRemove = contactsToRemove.map { it.rawId }.toMutableList() as ArrayList<Int>

        SimpleContactsHelper(activity).deleteContactRawIDs(idsToRemove) {
            activity.runOnUiThread {
                if (contacts.isEmpty()) {
                    refreshItemsListener?.refreshItems()
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    private fun getSelectedItems() = contacts.filter { selectedKeys.contains(it.rawId) } as ArrayList<SimpleContact>

    private fun getSelectedPhoneNumber(): String? {
        val numbers = getSelectedItems().firstOrNull()?.phoneNumbers
        val primaryNumber = numbers?.firstOrNull { it.isPrimary }
        return primaryNumber?.normalizedNumber ?: numbers?.firstOrNull()?.normalizedNumber
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val contact = contacts.firstOrNull { selectedKeys.contains(it.rawId) } ?: return
        val manager = activity.shortcutManager
        if (manager.isRequestPinShortcutSupported) {
            SimpleContactsHelper(activity).getShortcutImage(contact.photoUri, contact.name) { image ->
                activity.runOnUiThread {
                    activity.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                        val intent = Intent(action).apply {
                            data = Uri.fromParts("tel", contact.phoneNumbers.first().normalizedNumber, null)
                        }

                        val shortcut = ShortcutInfo.Builder(activity, contact.hashCode().toString())
                            .setShortLabel(contact.name)
                            .setIcon(Icon.createWithBitmap(image))
                            .setIntent(intent)
                            .build()

                        manager.requestPinShortcut(shortcut, null)
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.findViewById<ImageView>(R.id.item_contact_image))
        }
    }

    private fun setupView(view: View, contact: SimpleContact, holder: ViewHolder) {
        view.apply {
            findViewById<FrameLayout>(R.id.item_contact_frame).isSelected = selectedKeys.contains(contact.rawId)
            findViewById<TextView>(R.id.item_contact_name).apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                text = if (textToHighlight.isEmpty()) contact.name else {
                    if (contact.name.contains(textToHighlight, true)) {
                        contact.name.highlightTextPart(textToHighlight, properPrimaryColor)
                    } else {
                        contact.name.highlightTextFromNumbers(textToHighlight, properPrimaryColor)
                    }
                }
            }

            val dragIcon = findViewById<ImageView>(R.id.drag_handle_icon)
            if (enableDrag && textToHighlight.isEmpty()) {
                dragIcon.apply {
                    beVisibleIf(selectedKeys.isNotEmpty())
                    applyColorFilter(textColor)
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            startReorderDragListener?.requestDrag(holder)
                        }
                        false
                    }
                }
            } else {
                dragIcon.apply {
                    beGone()
                    setOnTouchListener(null)
                }
            }

            if (!activity.isDestroyed) {
                SimpleContactsHelper(context).loadContactImage(contact.photoUri, findViewById(R.id.item_contact_image), contact.name)
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        activity.config.isCustomOrderSelected = true

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(contacts, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(contacts, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        onDragEndListener?.invoke()
    }
}
