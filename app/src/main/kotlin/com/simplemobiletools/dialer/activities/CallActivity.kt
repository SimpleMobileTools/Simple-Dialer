package com.simplemobiletools.dialer.activities

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MINUTE_SECONDS
import com.simplemobiletools.commons.helpers.isOreoMr1Plus
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.dialer.App
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.extensions.addCharacter
import com.simplemobiletools.dialer.extensions.audioManager
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.extensions.getHandleToUse
import com.simplemobiletools.dialer.helpers.CallContactAvatarHelper
import com.simplemobiletools.dialer.helpers.CallManager
import com.simplemobiletools.dialer.models.CallContact
import kotlinx.android.synthetic.main.activity_call.*
import kotlinx.android.synthetic.main.dialpad.*

class CallActivity : SimpleActivity() {
    companion object {
        fun getStartIntent(context: Context): Intent {
            val openAppIntent = Intent(context, CallActivity::class.java)
            openAppIntent.flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
            return openAppIntent
        }
    }

    private var isSpeakerOn = false
    private var isMicrophoneOn = true
    private var isCallEnded = false
    private var callContact: CallContact? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var callDuration = 0
    private val callContactAvatarHelper by lazy { CallContactAvatarHelper(this) }
    private val callDurationHelper by lazy { (application as App).callDurationHelper }
    private var dragDownX = 0f
    private var stopAnimation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        updateTextColors(call_holder)
        initButtons()

        audioManager.mode = AudioManager.MODE_IN_CALL

        CallManager.getCallContact(applicationContext) { contact ->
            callContact = contact
            val avatar = callContactAvatarHelper.getCallContactAvatar(contact)
            runOnUiThread {
                updateOtherPersonsInfo(avatar)
                checkCalledSIMCard()
            }
        }

        addLockScreenFlags()

        CallManager.registerCallback(callCallback)
        updateCallState(CallManager.getState())
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.unregisterCallback(callCallback)
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock!!.release()
        }
    }

    override fun onBackPressed() {
        if (dialpad_wrapper.isVisible()) {
            dialpad_wrapper.beGone()
            return
        } else {
            super.onBackPressed()
        }

        val callState = CallManager.getState()
        if (callState == Call.STATE_CONNECTING || callState == Call.STATE_DIALING) {
            endCall()
        }
    }

    private fun initButtons() {
        if (config.disableSwipeToAnswer) {
            call_draggable.beGone()
            call_draggable_background.beGone()
            call_left_arrow.beGone()
            call_right_arrow.beGone()

            call_decline.setOnClickListener {
                endCall()
            }

            call_accept.setOnClickListener {
                acceptCall()
            }
        } else {
            handleSwipe()
        }

        call_toggle_microphone.setOnClickListener {
            toggleMicrophone()
        }

        call_toggle_speaker.setOnClickListener {
            toggleSpeaker()
        }

        call_dialpad.setOnClickListener {
            toggleDialpadVisibility()
        }

        dialpad_close.setOnClickListener {
            dialpad_wrapper.beGone()
        }

        call_end.setOnClickListener {
            endCall()
        }

        dialpad_0_holder.setOnClickListener { dialpadPressed('0') }
        dialpad_1_holder.setOnClickListener { dialpadPressed('1') }
        dialpad_2_holder.setOnClickListener { dialpadPressed('2') }
        dialpad_3_holder.setOnClickListener { dialpadPressed('3') }
        dialpad_4_holder.setOnClickListener { dialpadPressed('4') }
        dialpad_5_holder.setOnClickListener { dialpadPressed('5') }
        dialpad_6_holder.setOnClickListener { dialpadPressed('6') }
        dialpad_7_holder.setOnClickListener { dialpadPressed('7') }
        dialpad_8_holder.setOnClickListener { dialpadPressed('8') }
        dialpad_9_holder.setOnClickListener { dialpadPressed('9') }

        dialpad_0_holder.setOnLongClickListener { dialpadPressed('+'); true }
        dialpad_asterisk_holder.setOnClickListener { dialpadPressed('*') }
        dialpad_hashtag_holder.setOnClickListener { dialpadPressed('#') }

        dialpad_wrapper.setBackgroundColor(config.backgroundColor)
        arrayOf(call_toggle_microphone, call_toggle_speaker, call_dialpad, dialpad_close, call_sim_image).forEach {
            it.applyColorFilter(config.textColor)
        }

        call_sim_id.setTextColor(config.textColor.getContrastColor())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleSwipe() {
        var minDragX = 0f
        var maxDragX = 0f
        var initialDraggableX = 0f
        var initialLeftArrowX = 0f
        var initialRightArrowX = 0f
        var initialLeftArrowScaleX = 0f
        var initialLeftArrowScaleY = 0f
        var initialRightArrowScaleX = 0f
        var initialRightArrowScaleY = 0f
        var leftArrowTranslation = 0f
        var rightArrowTranslation = 0f

        call_accept.onGlobalLayout {
            minDragX = call_decline.left.toFloat()
            maxDragX = call_accept.left.toFloat()
            initialDraggableX = call_draggable.left.toFloat()
            initialLeftArrowX = call_left_arrow.x
            initialRightArrowX = call_right_arrow.x
            initialLeftArrowScaleX = call_left_arrow.scaleX
            initialLeftArrowScaleY = call_left_arrow.scaleY
            initialRightArrowScaleX = call_right_arrow.scaleX
            initialRightArrowScaleY = call_right_arrow.scaleY
            leftArrowTranslation = -call_decline.x
            rightArrowTranslation = call_decline.x

            call_left_arrow.applyColorFilter(getColor(R.color.md_red_400))
            call_right_arrow.applyColorFilter(getColor(R.color.md_green_400))

            startArrowAnimation(call_left_arrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
            startArrowAnimation(call_right_arrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
        }

        var lock = false
        call_draggable.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    call_draggable_background.animate().alpha(0f)
                    stopAnimation = true
                    call_left_arrow.animate().alpha(0f)
                    call_right_arrow.animate().alpha(0f)
                    lock = false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
                    call_draggable.animate().x(initialDraggableX).withEndAction {
                        call_draggable_background.animate().alpha(0.2f)
                    }
                    call_draggable.setImageDrawable(getDrawable(R.drawable.ic_phone_down_vector))
                    call_left_arrow.animate().alpha(1f)
                    call_right_arrow.animate().alpha(1f)
                    stopAnimation = false
                    startArrowAnimation(call_left_arrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
                    startArrowAnimation(call_right_arrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
                }
                MotionEvent.ACTION_MOVE -> {
                    call_draggable.x = Math.min(maxDragX, Math.max(minDragX, event.rawX - dragDownX))
                    when {
                        call_draggable.x >= maxDragX - 50f -> {
                            if (!lock) {
                                lock = true
                                call_draggable.performHapticFeedback()
                                acceptCall()
                            }
                        }
                        call_draggable.x <= minDragX + 50f -> {
                            if (!lock) {
                                lock = true
                                call_draggable.performHapticFeedback()
                                endCall()
                            }
                        }
                        call_draggable.x > initialDraggableX -> {
                            lock = false
                            call_draggable.setImageDrawable(getDrawable(R.drawable.ic_phone_green_vector))
                        }
                        call_draggable.x <= initialDraggableX -> {
                            lock = false
                            call_draggable.setImageDrawable(getDrawable(R.drawable.ic_phone_down_red_vector))
                        }
                    }
                }
            }
            true
        }
    }

    private fun startArrowAnimation(arrow: ImageView, initialX: Float, initialScaleX: Float, initialScaleY: Float, translation: Float) {
        arrow.apply {
            alpha = 1f
            x = initialX
            scaleX = initialScaleX
            scaleY = initialScaleY
            animate()
                .alpha(0f)
                .translationX(translation)
                .scaleXBy(-0.5f)
                .scaleYBy(-0.5f)
                .setDuration(1000)
                .withEndAction {
                    if (!stopAnimation) {
                        startArrowAnimation(this, initialX, initialScaleX, initialScaleY, translation)
                    }
                }
        }
    }

    private fun dialpadPressed(char: Char) {
        CallManager.keypad(char)
        dialpad_input.addCharacter(char)
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        val drawable = if (isSpeakerOn) R.drawable.ic_speaker_on_vector else R.drawable.ic_speaker_off_vector
        call_toggle_speaker.setImageDrawable(getDrawable(drawable))
        audioManager.isSpeakerphoneOn = isSpeakerOn

        val newRoute = if (isSpeakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        CallManager.inCallService?.setAudioRoute(newRoute)
        call_toggle_speaker.contentDescription = getString(if (isSpeakerOn) R.string.turn_speaker_off else R.string.turn_speaker_on)
    }

    private fun toggleMicrophone() {
        isMicrophoneOn = !isMicrophoneOn
        val drawable = if (isMicrophoneOn) R.drawable.ic_microphone_vector else R.drawable.ic_microphone_off_vector
        call_toggle_microphone.setImageDrawable(getDrawable(drawable))
        audioManager.isMicrophoneMute = !isMicrophoneOn
        CallManager.inCallService?.setMuted(!isMicrophoneOn)
        call_toggle_microphone.contentDescription = getString(if (isMicrophoneOn) R.string.turn_microphone_off else R.string.turn_microphone_on)
    }

    private fun toggleDialpadVisibility() {
        if (dialpad_wrapper.isVisible()) {
            dialpad_wrapper.beGone()
        } else {
            dialpad_wrapper.beVisible()
        }
    }

    private fun updateOtherPersonsInfo(avatar: Bitmap?) {
        if (callContact == null) {
            return
        }

        caller_name_label.text = if (callContact!!.name.isNotEmpty()) callContact!!.name else getString(R.string.unknown_caller)
        if (callContact!!.number.isNotEmpty() && callContact!!.number != callContact!!.name) {
            caller_number.text = callContact!!.number

            if (callContact!!.numberLabel.isNotEmpty()) {
                caller_number.text = "${callContact!!.number} - ${callContact!!.numberLabel}"
            }
        } else {
            caller_number.beGone()
        }

        if (avatar != null) {
            caller_avatar.setImageBitmap(avatar)
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkCalledSIMCard() {
        try {
            val accounts = telecomManager.callCapablePhoneAccounts
            if (accounts.size > 1) {
                accounts.forEachIndexed { index, account ->
                    if (account == CallManager.call?.details?.accountHandle) {
                        call_sim_id.text = "${index + 1}"
                        call_sim_id.beVisible()
                        call_sim_image.beVisible()
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun updateCallState(state: Int) {
        when (state) {
            Call.STATE_RINGING -> callRinging()
            Call.STATE_ACTIVE -> callStarted()
            Call.STATE_DISCONNECTED -> endCall()
            Call.STATE_CONNECTING, Call.STATE_DIALING -> initOutgoingCallUI()
            Call.STATE_SELECT_PHONE_ACCOUNT -> showPhoneAccountPicker()
        }

        val statusTextId = when (state) {
            Call.STATE_RINGING -> R.string.is_calling
            Call.STATE_DIALING -> R.string.dialing
            else -> 0
        }

        if (statusTextId != 0) {
            call_status_label.text = getString(statusTextId)
        }
    }

    private fun acceptCall() {
        CallManager.accept()
    }

    private fun initOutgoingCallUI() {
        initProximitySensor()
        incoming_call_holder.beGone()
        ongoing_call_holder.beVisible()
    }

    private fun callRinging() {
        incoming_call_holder.beVisible()
    }

    private fun callStarted() {
        initProximitySensor()
        incoming_call_holder.beGone()
        ongoing_call_holder.beVisible()
        callDurationHelper.onDurationChange {
            callDuration = it
            runOnUiThread {
                if (!isCallEnded) {
                    call_status_label.text = callDuration.getFormattedDuration()
                }
            }
        }
    }

    private fun showPhoneAccountPicker() {
        if (callContact != null) {
            getHandleToUse(intent, callContact!!.number) { handle ->
                CallManager.call?.phoneAccountSelected(handle, false)
            }
        }
    }

    private fun endCall() {
        CallManager.reject()
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock!!.release()
        }

        if (isCallEnded) {
            finishAndRemoveTask()
            return
        }

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (ignored: Exception) {
        }

        isCallEnded = true
        if (callDuration > 0) {
            runOnUiThread {
                call_status_label.text = "${callDuration.getFormattedDuration()} (${getString(R.string.call_ended)})"
                Handler().postDelayed({
                    finishAndRemoveTask()
                }, 3000)
            }
        } else {
            call_status_label.text = getString(R.string.call_ended)
            finish()
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            updateCallState(state)
        }
    }

    @SuppressLint("NewApi")
    private fun addLockScreenFlags() {
        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        if (isOreoPlus()) {
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
    }

    private fun initProximitySensor() {
        if (!config.disableProximitySensor && (proximityWakeLock == null || proximityWakeLock?.isHeld == false)) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "com.simplemobiletools.dialer.pro:wake_lock")
            proximityWakeLock!!.acquire(60 * MINUTE_SECONDS * 1000L)
        }
    }
}
