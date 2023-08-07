package com.simplemobiletools.dialer.activities

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony.Sms.Intents.SECRET_CODE_ACTION
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.dialogs.CallConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.contacts.Contact
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.databinding.ActivityDialpadBinding
import com.simplemobiletools.dialer.databinding.DialpadBinding
import com.simplemobiletools.dialer.extensions.*
import com.simplemobiletools.dialer.helpers.DIALPAD_TONE_LENGTH_MS
import com.simplemobiletools.dialer.helpers.ToneGeneratorHelper
import com.simplemobiletools.dialer.models.SpeedDial
import java.util.*
import kotlin.math.roundToInt

class DialpadActivity : SimpleActivity() {

    private lateinit var dialpadActivityBinding: ActivityDialpadBinding
    private var allContacts = ArrayList<Contact>()
    private var speedDialValues = ArrayList<SpeedDial>()
    private val russianCharsMap = HashMap<Char, Int>()
    private var hasRussianLocale = false
    private var privateCursor: Cursor? = null
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialpadActivityBinding = ActivityDialpadBinding.inflate(layoutInflater)
        setContentView(dialpadActivityBinding.root)
        hasRussianLocale = Locale.getDefault().language == "ru"

        updateMaterialActivityViews(
            dialpadActivityBinding.dialpadCoordinator,
            dialpadActivityBinding.dialpadHolder,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(dialpadActivityBinding.dialpadList, dialpadActivityBinding.dialpadToolbar)
        updateNavigationBarColor(getProperBackgroundColor())

        if (checkAppSideloading()) {
            return
        }

        if (config.hideDialpadNumbers) {

            dialpadActivityBinding.dialpadWrapper.dialpad1Holder.isVisible = false
            dialpadActivityBinding.dialpadWrapper.dialpad2Holder.isVisible = false
            dialpadActivityBinding.dialpadWrapper.dialpad3Holder.isVisible = false
            dialpadActivityBinding.dialpadWrapper.dialpad4Holder.isVisible = false
            dialpadActivityBinding.dialpadWrapper.dialpad5Holder.isVisible = false
            dialpadActivityBinding.dialpadWrapper.dialpad6Holder.isVisible = false
            dialpadActivityBinding.dialpadWrapper.dialpad7Holder.isVisible = false
            dialpadActivityBinding.dialpadWrapper.dialpad8Holder.isVisible = false
            dialpadActivityBinding.dialpadWrapper.dialpad9Holder.isVisible = false
            dialpadActivityBinding.dialpadWrapper.dialpadPlusHolder.isVisible = true
            dialpadActivityBinding.dialpadWrapper.dialpad0Holder.visibility = View.INVISIBLE
        }

        arrayOf(
            dialpadActivityBinding.dialpadWrapper.dialpad0Holder,
            dialpadActivityBinding.dialpadWrapper.dialpad1Holder,
            dialpadActivityBinding.dialpadWrapper.dialpad2Holder,
            dialpadActivityBinding.dialpadWrapper.dialpad3Holder,
            dialpadActivityBinding.dialpadWrapper.dialpad4Holder,
            dialpadActivityBinding.dialpadWrapper.dialpad5Holder,
            dialpadActivityBinding.dialpadWrapper.dialpad6Holder,
            dialpadActivityBinding.dialpadWrapper.dialpad7Holder,
            dialpadActivityBinding.dialpadWrapper.dialpad8Holder,
            dialpadActivityBinding.dialpadWrapper.dialpad9Holder,
            dialpadActivityBinding.dialpadWrapper.dialpadPlusHolder,
            dialpadActivityBinding.dialpadWrapper.dialpadAsteriskHolder,
            dialpadActivityBinding.dialpadWrapper.dialpadHashtagHolder
        ).forEach {
            it.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
            it.background?.alpha = LOWER_ALPHA_INT
        }

        setupOptionsMenu()
        speedDialValues = config.getSpeedDialValues()
        privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)

        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)

        if (hasRussianLocale) {
            initRussianChars()

            dialpadActivityBinding.dialpadWrapper.dialpad2Letters.append("\nАБВГ")
            dialpadActivityBinding.dialpadWrapper.dialpad3Letters.append("\nДЕЁЖЗ")
            dialpadActivityBinding.dialpadWrapper.dialpad4Letters.append("\nИЙКЛ")
            dialpadActivityBinding.dialpadWrapper.dialpad5Letters.append("\nМНОП")
            dialpadActivityBinding.dialpadWrapper.dialpad6Letters.append("\nРСТУ")
            dialpadActivityBinding.dialpadWrapper.dialpad7Letters.append("\nФХЦЧ")
            dialpadActivityBinding.dialpadWrapper.dialpad8Letters.append("\nШЩЪЫ")
            dialpadActivityBinding.dialpadWrapper.dialpad9Letters.append("\nЬЭЮЯ")

            val fontSize = resources.getDimension(R.dimen.small_text_size)
            arrayOf(
                dialpadActivityBinding.dialpadWrapper.dialpad2Letters,
                dialpadActivityBinding.dialpadWrapper.dialpad3Letters,
                dialpadActivityBinding.dialpadWrapper.dialpad4Letters,
                dialpadActivityBinding.dialpadWrapper.dialpad5Letters,
                dialpadActivityBinding.dialpadWrapper.dialpad6Letters,
                dialpadActivityBinding.dialpadWrapper.dialpad7Letters,
                dialpadActivityBinding.dialpadWrapper.dialpad8Letters,
                dialpadActivityBinding.dialpadWrapper.dialpad9Letters
            ).forEach {
                it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }
        }

        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad1Holder, '1')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad2Holder, '2')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad3Holder, '3')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad4Holder, '4')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad5Holder, '5')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad6Holder, '6')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad7Holder, '7')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad8Holder, '8')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad9Holder, '9')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpad0Holder, '0')
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpadPlusHolder, '+', longClickable = false)
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpadAsteriskHolder, '*', longClickable = false)
        setupCharClick(dialpadActivityBinding.dialpadWrapper.dialpadHashtagHolder, '#', longClickable = false)

        dialpadActivityBinding.dialpadClearChar.setOnClickListener { clearChar(it) }
        dialpadActivityBinding.dialpadClearChar.setOnLongClickListener { clearInput(); true }
        dialpadActivityBinding.dialpadCallButton.setOnClickListener { initCall(dialpadActivityBinding.dialpadInput.value, 0) }

        dialpadActivityBinding.dialpadInput.onTextChangeListener { dialpadValueChanged(it) }
        dialpadActivityBinding.dialpadInput.requestFocus()
        dialpadActivityBinding.dialpadInput.disableKeyboard()

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { allContacts ->
            gotContacts(allContacts)
        }

        val properPrimaryColor = getProperPrimaryColor()
        val callIconId = if (areMultipleSIMsAvailable()) {
            val callIcon = resources.getColoredDrawableWithColor(R.drawable.ic_phone_two_vector, properPrimaryColor.getContrastColor())

            dialpadActivityBinding.dialpadCallTwoButton.setImageDrawable(callIcon)
            dialpadActivityBinding.dialpadCallTwoButton.background.applyColorFilter(properPrimaryColor)
            dialpadActivityBinding.dialpadCallTwoButton.beVisible()
            dialpadActivityBinding.dialpadCallTwoButton.setOnClickListener {
                initCall(dialpadActivityBinding.dialpadInput.value, 1)
            }

            R.drawable.ic_phone_one_vector
        } else {
            R.drawable.ic_phone_vector
        }

        val callIcon = resources.getColoredDrawableWithColor(callIconId, properPrimaryColor.getContrastColor())
        dialpadActivityBinding.dialpadCallButton.setImageDrawable(callIcon)
        dialpadActivityBinding.dialpadCallButton.background.applyColorFilter(properPrimaryColor)

        dialpadActivityBinding.letterFastscroller.textColor = getProperTextColor().getColorStateList()
        dialpadActivityBinding.letterFastscroller.pressedTextColor = properPrimaryColor
        dialpadActivityBinding.letterFastscrollerThumb.setupWithFastScroller(dialpadActivityBinding.letterFastscroller)
        dialpadActivityBinding.letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        dialpadActivityBinding.letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(dialpadActivityBinding.dialpadHolder)
        dialpadActivityBinding.dialpadClearChar.applyColorFilter(getProperTextColor())
        updateNavigationBarColor(getProperBackgroundColor())
        setupToolbar(dialpadActivityBinding.dialpadToolbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu() {
        dialpadActivityBinding.dialpadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_number_to_contact -> addNumberToContact()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun checkDialIntent(): Boolean {
        return if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) && intent.data != null && intent.dataString?.contains("tel:") == true) {
            val number = Uri.decode(intent.dataString).substringAfter("tel:")
            dialpadActivityBinding.dialpadInput.setText(number)
            dialpadActivityBinding.dialpadInput.setSelection(number.length)
            true
        } else {
            false
        }
    }

    private fun addNumberToContact() {
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, dialpadActivityBinding.dialpadInput.value)
            launchActivityIntent(this)
        }
    }

    private fun dialpadPressed(char: Char, view: View?) {
        dialpadActivityBinding.dialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearChar(view: View) {
        dialpadActivityBinding.dialpadInput.dispatchKeyEvent(dialpadActivityBinding.dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInput() {
        dialpadActivityBinding.dialpadInput.setText("")
    }

    private fun gotContacts(newContacts: ArrayList<Contact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        runOnUiThread {
            if (!checkDialIntent() && dialpadActivityBinding.dialpadInput.value.isEmpty()) {
                dialpadValueChanged("")
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun dialpadValueChanged(text: String) {
        val len = text.length
        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            val secretCode = text.substring(4, text.length - 4)
            if (isOreoPlus()) {
                if (isDefaultDialer()) {
                    getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
                } else {
                    launchSetDefaultDialerIntent()
                }
            } else {
                val intent = Intent(SECRET_CODE_ACTION, Uri.parse("android_secret_code://$secretCode"))
                sendBroadcast(intent)
            }
            return
        }

        (dialpadActivityBinding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

        val filtered = allContacts.filter {
            var convertedName = PhoneNumberUtils.convertKeypadLettersToDigits(it.name.normalizeString())

            if (hasRussianLocale) {
                var currConvertedName = ""
                convertedName.lowercase(Locale.getDefault()).forEach { char ->
                    val convertedChar = russianCharsMap.getOrElse(char) { char }
                    currConvertedName += convertedChar
                }
                convertedName = currConvertedName
            }

            it.doesContainPhoneNumber(text) || (convertedName.contains(text, true))
        }.sortedWith(compareBy {
            !it.doesContainPhoneNumber(text)
        }).toMutableList() as ArrayList<Contact>


        dialpadActivityBinding.letterFastscroller.setupWithRecyclerView(dialpadActivityBinding.dialpadList, { position ->
            try {
                val name = filtered[position].getNameToDisplay()
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.uppercase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })

        ContactsAdapter(
            activity = this,
            contacts = filtered,
            recyclerView = dialpadActivityBinding.dialpadList,
            highlightText = text
        ) {
            val contact = it as Contact
            if (config.showCallConfirmation) {
                CallConfirmationDialog(this@DialpadActivity, contact.getNameToDisplay()) {
                    startCallIntent(contact.getPrimaryNumber() ?: return@CallConfirmationDialog)
                }
            } else {
                startCallIntent(contact.getPrimaryNumber() ?: return@ContactsAdapter)
            }
        }.apply {
            dialpadActivityBinding.dialpadList.adapter = this
        }

        dialpadActivityBinding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
        dialpadActivityBinding.dialpadList.beVisibleIf(filtered.isNotEmpty())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && isDefaultDialer()) {
            dialpadValueChanged(dialpadActivityBinding.dialpadInput.value)
        }
    }

    private fun initCall(number: String = dialpadActivityBinding.dialpadInput.value, handleIndex: Int) {
        if (number.isNotEmpty()) {
            if (handleIndex != -1 && areMultipleSIMsAvailable()) {
                if (config.showCallConfirmation) {
                    CallConfirmationDialog(this, number) {
                        callContactWithSim(number, handleIndex == 0)
                    }
                } else {
                    callContactWithSim(number, handleIndex == 0)
                }
            } else {
                if (config.showCallConfirmation) {
                    CallConfirmationDialog(this, number) {
                        startCallIntent(number)
                    }
                } else {
                    startCallIntent(number)
                }
            }
        }
    }

    private fun speedDial(id: Int): Boolean {
        if (dialpadActivityBinding.dialpadInput.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, -1)
                return true
            }
        }
        return false
    }

    private fun initRussianChars() {
        russianCharsMap['а'] = 2; russianCharsMap['б'] = 2; russianCharsMap['в'] = 2; russianCharsMap['г'] = 2
        russianCharsMap['д'] = 3; russianCharsMap['е'] = 3; russianCharsMap['ё'] = 3; russianCharsMap['ж'] = 3; russianCharsMap['з'] = 3
        russianCharsMap['и'] = 4; russianCharsMap['й'] = 4; russianCharsMap['к'] = 4; russianCharsMap['л'] = 4
        russianCharsMap['м'] = 5; russianCharsMap['н'] = 5; russianCharsMap['о'] = 5; russianCharsMap['п'] = 5
        russianCharsMap['р'] = 6; russianCharsMap['с'] = 6; russianCharsMap['т'] = 6; russianCharsMap['у'] = 6
        russianCharsMap['ф'] = 7; russianCharsMap['х'] = 7; russianCharsMap['ц'] = 7; russianCharsMap['ч'] = 7
        russianCharsMap['ш'] = 8; russianCharsMap['щ'] = 8; russianCharsMap['ъ'] = 8; russianCharsMap['ы'] = 8
        russianCharsMap['ь'] = 9; russianCharsMap['э'] = 9; russianCharsMap['ю'] = 9; russianCharsMap['я'] = 9
    }

    private fun startDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else {
            val result = speedDial(char.digitToInt())
            if (result) {
                stopDialpadTone(char)
                clearChar(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed({
                            performLongClick(view, char)
                        }, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }
}
