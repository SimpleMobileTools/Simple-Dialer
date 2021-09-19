package com.simplemobiletools.dialer.activities

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MINUTE_SECONDS
import com.simplemobiletools.commons.helpers.isOreoMr1Plus
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.extensions.addCharacter
import com.simplemobiletools.dialer.extensions.audioManager
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.extensions.getHandleToUse
import com.simplemobiletools.dialer.helpers.CallContactAvatarHelper
import com.simplemobiletools.dialer.helpers.CallManager
import com.simplemobiletools.dialer.models.CallContact
import java.util.Timer
import java.util.TimerTask
import kotlinx.android.synthetic.main.activity_call.*
import kotlinx.android.synthetic.main.dialpad.*

class CallActivity : SimpleActivity() {
    private var isSpeakerOn = false
    private var isMicrophoneOn = true
    private var isCallEnded = false
    private var callDuration = 0
    private var callContact: CallContact? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var callTimer = Timer()
    private val callContactAvatarHelper by lazy { CallContactAvatarHelper(this) }

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
        callTimer.cancel()
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
        call_decline.setOnClickListener {
            endCall()
        }

        call_accept.setOnClickListener {
            acceptCall()
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
    }

    private fun toggleMicrophone() {
        isMicrophoneOn = !isMicrophoneOn
        val drawable = if (isMicrophoneOn) R.drawable.ic_microphone_vector else R.drawable.ic_microphone_off_vector
        call_toggle_microphone.setImageDrawable(getDrawable(drawable))
        audioManager.isMicrophoneMute = !isMicrophoneOn
        CallManager.inCallService?.setMuted(!isMicrophoneOn)
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
            caller_number_label.text = callContact!!.number
        } else {
            caller_number_label.beGone()
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

        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
            callTimer.cancel()
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
        try {
            callTimer.scheduleAtFixedRate(getCallTimerUpdateTask(), 1000, 1000)
        } catch (ignored: Exception) {
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

    private fun getCallTimerUpdateTask() = object : TimerTask() {
        override fun run() {
            callDuration++
            runOnUiThread {
                if (!isCallEnded) {
                    call_status_label.text = callDuration.getFormattedDuration()
                }
            }
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
        if (proximityWakeLock == null || proximityWakeLock?.isHeld == false) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "com.simplemobiletools.dialer.pro:wake_lock")
            proximityWakeLock!!.acquire(10 * MINUTE_SECONDS * 1000L)
        }
    }
}
