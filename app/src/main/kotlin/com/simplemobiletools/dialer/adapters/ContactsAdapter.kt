package com.simplemobiletools.dialer.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.getAdjustedPrimaryColor
import com.simplemobiletools.commons.extensions.getTextSize
import com.simplemobiletools.commons.extensions.highlightTextFromNumbers
import com.simplemobiletools.commons.extensions.highlightTextPart
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener

class ContactsAdapter(activity: SimpleActivity, var contacts: ArrayList<SimpleContact>, recyclerView: MyRecyclerView, val refreshItemsListener: RefreshItemsListener? = null,
                      highlightText: String = "", itemClick: (Any) -> Unit) : MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private var textToHighlight = highlightText
    private var adjustedPrimaryColor = activity.getAdjustedPrimaryColor()
    private var fontSize = activity.getTextSize()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_contacts

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected() && isOreoPlus()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_create_shortcut -> createShortcut()
        }
    }

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.rawId

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.rawId == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_contact_without_number, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, true, true) { itemView, layoutPosition ->
            setupView(itemView, contact)
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

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().first()
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

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val contact = contacts.firstOrNull { selectedKeys.contains(it.rawId) } ?: return
        val manager = activity.getSystemService(ShortcutManager::class.java)!!
        if (manager.isRequestPinShortcutSupported) {
            getShortcutImage(contact.photoUri, contact.name) { image ->
                activity.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                    val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                    val intent = Intent(action).apply {
                        data = Uri.fromParts("tel", contact.phoneNumber, null)
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

    private fun getShortcutImage(path: String, placeholderName: String, callback: (image: Bitmap) -> Unit) {
        ensureBackgroundThread {
            val placeholder = BitmapDrawable(activity.resources, SimpleContactsHelper(activity).getContactLetterIcon(placeholderName))
            try {
                val options = RequestOptions()
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .error(placeholder)
                    .centerCrop()

                val size = resources.getDimension(R.dimen.shortcut_size).toInt()
                val bitmap = Glide.with(activity).asBitmap()
                    .load(path)
                    .placeholder(placeholder)
                    .apply(options)
                    .apply(RequestOptions.circleCropTransform())
                    .into(size, size)
                    .get()

                activity.runOnUiThread {
                    callback(bitmap)
                }
            } catch (ignored: Exception) {
                activity.runOnUiThread {
                    callback(placeholder.bitmap)
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

    private fun setupView(view: View, contact: SimpleContact) {
        view.apply {
            findViewById<FrameLayout>(R.id.item_contact_frame).isSelected = selectedKeys.contains(contact.rawId)
            findViewById<TextView>(R.id.item_contact_name).apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                text = if (textToHighlight.isEmpty()) contact.name else {
                    if (contact.name.contains(textToHighlight, true)) {
                        contact.name.highlightTextPart(textToHighlight, adjustedPrimaryColor)
                    } else {
                        contact.name.highlightTextFromNumbers(textToHighlight, adjustedPrimaryColor)
                    }
                }

            }

            SimpleContactsHelper(context).loadContactImage(contact.photoUri, findViewById(R.id.item_contact_image), contact.name)
        }
    }
}
