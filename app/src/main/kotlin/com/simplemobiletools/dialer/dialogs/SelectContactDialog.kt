package com.simplemobiletools.dialer.dialogs

import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.commons.views.MySearchMenu
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import kotlinx.android.synthetic.main.dialog_select_contact.view.*
import java.util.Locale

class SelectContactDialog(val activity: SimpleActivity, val contacts: List<Contact>, val callback: (selectedContact: Contact) -> Unit) {
    private var dialog: AlertDialog? = null
    private var view = activity.layoutInflater.inflate(R.layout.dialog_select_contact, null)
    private val searchView = view.contact_search_view
    private val searchEditText = view.findViewById<EditText>(R.id.top_toolbar_search)
    private val searchViewAppBarLayout = view.findViewById<View>(R.id.top_app_bar_layout)

    init {
        view.apply {
            letter_fastscroller.textColor = context.getProperTextColor().getColorStateList()
            letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
            letter_fastscroller_thumb.textColor = context.getProperPrimaryColor().getContrastColor()
            letter_fastscroller_thumb.thumbColor = context.getProperPrimaryColor().getColorStateList()

            setupLetterFastScroller(contacts)
            configureSearchView()

            select_contact_list.adapter = ContactsAdapter(activity, contacts.toMutableList(), select_contact_list, allowLongClick = false) {
                callback(it as Contact)
                dialog?.dismiss()
            }
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(R.string.cancel, null)
            .setOnKeyListener { _, i, keyEvent ->
                if (keyEvent.action == KeyEvent.ACTION_UP && i == KeyEvent.KEYCODE_BACK) {
                    backPressed()
                }
                true
            }
            .apply {
                activity.setupDialogStuff(view, this, R.string.choose_contact) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun setupLetterFastScroller(contacts: List<Contact>) {
        view.letter_fastscroller.setupWithRecyclerView(view.select_contact_list, { position ->
            try {
                val name = contacts[position].getNameToDisplay()
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.uppercase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    private fun configureSearchView() = with(searchView) {
        updateHintText(context.getString(R.string.search_contacts))
        searchEditText.imeOptions = EditorInfo.IME_ACTION_DONE

        toggleHideOnScroll(true)
        setupMenu()
        setSearchViewListeners()
        updateSearchViewUi()
    }

    private fun MySearchMenu.updateSearchViewUi() {
        getToolbar().beInvisible()
        updateColors()
        setBackgroundColor(Color.TRANSPARENT)
        searchViewAppBarLayout.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun MySearchMenu.setSearchViewListeners() {
        onSearchOpenListener = {
            updateSearchViewLeftIcon(R.drawable.ic_cross_vector)
        }
        onSearchClosedListener = {
            searchEditText.clearFocus()
            activity.hideKeyboard(searchEditText)
            updateSearchViewLeftIcon(R.drawable.ic_search_vector)
        }

        onSearchTextChangedListener = { text ->
            filterContactListBySearchQuery(text)
        }
    }

    private fun updateSearchViewLeftIcon(iconResId: Int) = with(view.findViewById<ImageView>(R.id.top_toolbar_search_icon)) {
        post {
            setImageResource(iconResId)
        }
    }

    private fun filterContactListBySearchQuery(query: String) {
        val adapter = view.select_contact_list.adapter as? ContactsAdapter
        var contactsToShow = contacts
        if (query.isNotEmpty()) {
            contactsToShow = contacts.filter { it.name.contains(query, true) }
        }
        checkPlaceholderVisibility(contactsToShow)

        if (adapter?.contacts != contactsToShow) {
            adapter?.updateItems(contactsToShow)
            setupLetterFastScroller(contactsToShow)

            view.select_contact_list.apply {
                post {
                    scrollToPosition(0)
                }
            }
        }
    }

    private fun checkPlaceholderVisibility(contacts: List<Contact>) = with(view) {
        contacts_empty_placeholder.beVisibleIf(contacts.isEmpty())

        if (contact_search_view.isSearchOpen) {
            contacts_empty_placeholder.text = context.getString(R.string.no_items_found)
        }

        letter_fastscroller.beVisibleIf(contacts_empty_placeholder.isGone())
    }

    private fun backPressed() {
        if (searchView.isSearchOpen) {
            searchView.closeSearch()
        } else {
            dialog?.dismiss()
        }
    }
}
