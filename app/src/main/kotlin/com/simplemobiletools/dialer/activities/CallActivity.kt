package com.simplemobiletools.dialer.activities

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.SimpleListItem
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.databinding.ActivityCallBinding
import com.simplemobiletools.dialer.databinding.DialpadBinding
import com.simplemobiletools.dialer.dialogs.DynamicBottomSheetChooserDialog
import com.simplemobiletools.dialer.extensions.*
import com.simplemobiletools.dialer.helpers.*
import com.simplemobiletools.dialer.models.AudioRoute
import com.simplemobiletools.dialer.models.CallContact
import kotlin.math.max
import kotlin.math.min

class CallActivity : SimpleActivity() {
    companion object {
        fun getStartIntent(context: Context): Intent {
            val openAppIntent = Intent(context, CallActivity::class.java)
            openAppIntent.flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            return openAppIntent
        }
    }

    private var isSpeakerOn = false
    private var isMicrophoneOff = false
    private var isCallEnded = false
    private var callContact: CallContact? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var screenOnWakeLock: PowerManager.WakeLock? = null
    private var callDuration = 0
    private val callContactAvatarHelper by lazy { CallContactAvatarHelper(this) }
    private val callDurationHandler = Handler(Looper.getMainLooper())
    private var dragDownX = 0f
    private var stopAnimation = false
    private var viewsUnderDialpad = arrayListOf<Pair<View, Float>>()
    private var dialpadHeight = 0f

    private var audioRouteChooserDialog: DynamicBottomSheetChooserDialog? = null

    private val binding by viewBinding(ActivityCallBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (CallManager.getPhoneState() == NoCall) {
            finish()
            return
        }

        updateTextColors(binding.callHolder)
        initButtons()
        audioManager.mode = AudioManager.MODE_IN_CALL
        addLockScreenFlags()
        CallManager.addListener(callCallback)
        updateCallContactInfo(CallManager.getPrimaryCall())
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        updateState()
    }

    override fun onResume() {
        super.onResume()
        updateState()
        updateNavigationBarColor(getProperBackgroundColor())

        if (config.isUsingSystemTheme) {
            updateStatusbarColor(getProperBackgroundColor())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.removeListener(callCallback)
        disableProximitySensor()

        if (screenOnWakeLock?.isHeld == true) {
            screenOnWakeLock!!.release()
        }
    }

    override fun onBackPressed() {
        if (binding.dialpadWrapper.isVisible()) {
            hideDialpad()
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
            binding.callDraggable.beGone()
            binding.callDraggableBackground.beGone()
            binding.callLeftArrow.beGone()
            binding.callRightArrow.beGone()

            binding.callDecline.setOnClickListener {
                endCall()
            }

            binding.callAccept.setOnClickListener {
                acceptCall()
            }
        } else {
            handleSwipe()
        }

        binding.callToggleMicrophone.setOnClickListener {
            toggleMicrophone()
        }

        binding.callToggleSpeaker.setOnClickListener {
            changeCallAudioRoute()
        }

        binding.callDialpad.setOnClickListener {
            toggleDialpadVisibility()
        }

        binding.dialpadClose.setOnClickListener {
            hideDialpad()
        }

        binding.callToggleHold.setOnClickListener {
            toggleHold()
        }

        binding.callAdd.setOnClickListener {
            Intent(applicationContext, DialpadActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(this)
            }
        }

        binding.callSwap.setOnClickListener {
            CallManager.swap()
        }

        binding.callMerge.setOnClickListener {
            CallManager.merge()
        }

        binding.callManage.setOnClickListener {
            startActivity(Intent(this, ConferenceActivity::class.java))
        }

        binding.callEnd.setOnClickListener {
            endCall()
        }

        binding.dialpadContainer.dialpad0Holder.setOnClickListener { dialpadPressed('0') }
        binding.dialpadContainer.dialpad1Holder.setOnClickListener { dialpadPressed('1') }
        binding.dialpadContainer.dialpad2Holder.setOnClickListener { dialpadPressed('2') }
        binding.dialpadContainer.dialpad3Holder.setOnClickListener { dialpadPressed('3') }
        binding.dialpadContainer.dialpad4Holder.setOnClickListener { dialpadPressed('4') }
        binding.dialpadContainer.dialpad5Holder.setOnClickListener { dialpadPressed('5') }
        binding.dialpadContainer.dialpad6Holder.setOnClickListener { dialpadPressed('6') }
        binding.dialpadContainer.dialpad7Holder.setOnClickListener { dialpadPressed('7') }
        binding.dialpadContainer.dialpad8Holder.setOnClickListener { dialpadPressed('8') }
        binding.dialpadContainer.dialpad9Holder.setOnClickListener { dialpadPressed('9') }

        arrayOf(
            binding.dialpadContainer.dialpad0Holder,
            binding.dialpadContainer.dialpad1Holder,
            binding.dialpadContainer.dialpad2Holder,
            binding.dialpadContainer.dialpad3Holder,
            binding.dialpadContainer.dialpad4Holder,
            binding.dialpadContainer.dialpad5Holder,
            binding.dialpadContainer.dialpad6Holder,
            binding.dialpadContainer.dialpad7Holder,
            binding.dialpadContainer.dialpad8Holder,
            binding.dialpadContainer.dialpad9Holder,
            binding.dialpadContainer.dialpadPlusHolder,
            binding.dialpadContainer.dialpadAsteriskHolder,
            binding.dialpadContainer.dialpadHashtagHolder
        ).forEach {
            it.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
            it.background?.alpha = LOWER_ALPHA_INT
        }

        binding.dialpadContainer.dialpad0Holder.setOnLongClickListener { dialpadPressed('+'); true }
        binding.dialpadContainer.dialpadAsteriskHolder.setOnClickListener { dialpadPressed('*') }
        binding.dialpadContainer.dialpadHashtagHolder.setOnClickListener { dialpadPressed('#') }

        binding.dialpadWrapper.setBackgroundColor(getProperBackgroundColor())
        arrayOf(binding.dialpadClose, binding.callSimImage).forEach {
            it.applyColorFilter(getProperTextColor())
        }

        val bgColor = getProperBackgroundColor()
        val inactiveColor = getInactiveButtonColor()
        arrayOf(
            binding.callToggleMicrophone,
            binding.callToggleSpeaker,
            binding.callDialpad,
            binding.callToggleHold,
            binding.callAdd,
            binding.callSwap,
            binding.callMerge,
            binding.callManage
        ).forEach {
            it.applyColorFilter(bgColor.getContrastColor())
            it.background.applyColorFilter(inactiveColor)
        }

        arrayOf(
            binding.callToggleMicrophone,
            binding.callToggleSpeaker,
            binding.callDialpad,
            binding.callToggleHold,
            binding.callAdd,
            binding.callSwap,
            binding.callMerge,
            binding.callManage
        ).forEach { imageView ->
            imageView.setOnLongClickListener {
                if (!imageView.contentDescription.isNullOrEmpty()) {
                    toast(imageView.contentDescription.toString())
                }
                true
            }
        }

        binding.callSimId.setTextColor(getProperTextColor().getContrastColor())
        binding.dialpadInput.disableKeyboard()

        binding.dialpadWrapper.onGlobalLayout {
            dialpadHeight = binding.dialpadWrapper.height.toFloat()
        }
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

        val isRtl = isRTLLayout
        binding.callAccept.onGlobalLayout {
            minDragX = if (isRtl) {
                binding.callAccept.left.toFloat()
            } else {
                binding.callDecline.left.toFloat()
            }

            maxDragX = if (isRtl) {
                binding.callDecline.left.toFloat()
            } else {
                binding.callAccept.left.toFloat()
            }

            initialDraggableX = binding.callDraggable.left.toFloat()
            initialLeftArrowX = binding.callLeftArrow.x
            initialRightArrowX = binding.callRightArrow.x
            initialLeftArrowScaleX = binding.callLeftArrow.scaleX
            initialLeftArrowScaleY = binding.callLeftArrow.scaleY
            initialRightArrowScaleX = binding.callRightArrow.scaleX
            initialRightArrowScaleY = binding.callRightArrow.scaleY
            leftArrowTranslation = if (isRtl) {
                binding.callAccept.x
            } else {
                -binding.callDecline.x
            }

            rightArrowTranslation = if (isRtl) {
                -binding.callAccept.x
            } else {
                binding.callDecline.x
            }

            if (isRtl) {
                binding.callLeftArrow.setImageResource(R.drawable.ic_chevron_right_vector)
                binding.callRightArrow.setImageResource(R.drawable.ic_chevron_left_vector)
            }

            binding.callLeftArrow.applyColorFilter(getColor(R.color.md_red_400))
            binding.callRightArrow.applyColorFilter(getColor(R.color.md_green_400))

            startArrowAnimation(binding.callLeftArrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
            startArrowAnimation(binding.callRightArrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
        }

        binding.callDraggable.drawable.mutate().setTint(getProperTextColor())
        binding.callDraggableBackground.drawable.mutate().setTint(getProperTextColor())

        var lock = false
        binding.callDraggable.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    binding.callDraggableBackground.animate().alpha(0f)
                    stopAnimation = true
                    binding.callLeftArrow.animate().alpha(0f)
                    binding.callRightArrow.animate().alpha(0f)
                    lock = false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
                    binding.callDraggable.animate().x(initialDraggableX).withEndAction {
                        binding.callDraggableBackground.animate().alpha(0.2f)
                    }
                    binding.callDraggable.setImageDrawable(getDrawable(R.drawable.ic_phone_down_vector))
                    binding.callDraggable.drawable.mutate().setTint(getProperTextColor())
                    binding.callLeftArrow.animate().alpha(1f)
                    binding.callRightArrow.animate().alpha(1f)
                    stopAnimation = false
                    startArrowAnimation(
                        binding.callLeftArrow,
                        initialLeftArrowX,
                        initialLeftArrowScaleX,
                        initialLeftArrowScaleY,
                        leftArrowTranslation
                    )
                    startArrowAnimation(
                        binding.callRightArrow,
                        initialRightArrowX,
                        initialRightArrowScaleX,
                        initialRightArrowScaleY,
                        rightArrowTranslation
                    )
                }

                MotionEvent.ACTION_MOVE -> {
                    binding.callDraggable.x = min(maxDragX, max(minDragX, event.rawX - dragDownX))
                    when {
                        binding.callDraggable.x >= maxDragX - 50f -> {
                            if (!lock) {
                                lock = true
                                binding.callDraggable.performHapticFeedback()
                                if (isRtl) {
                                    endCall()
                                } else {
                                    acceptCall()
                                }
                            }
                        }

                        binding.callDraggable.x <= minDragX + 50f -> {
                            if (!lock) {
                                lock = true
                                binding.callDraggable.performHapticFeedback()
                                if (isRtl) {
                                    acceptCall()
                                } else {
                                    endCall()
                                }
                            }
                        }

                        binding.callDraggable.x > initialDraggableX -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_down_red_vector
                            } else {
                                R.drawable.ic_phone_green_vector
                            }
                            binding.callDraggable.setImageDrawable(getDrawable(drawableRes))
                        }

                        binding.callDraggable.x <= initialDraggableX -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_green_vector
                            } else {
                                R.drawable.ic_phone_down_red_vector
                            }
                            binding.callDraggable.setImageDrawable(getDrawable(drawableRes))
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
        binding.dialpadInput.addCharacter(char)
    }

    private fun changeCallAudioRoute() {
        val supportAudioRoutes = CallManager.getSupportedAudioRoutes()
        if (supportAudioRoutes.contains(AudioRoute.BLUETOOTH)) {
            createOrUpdateAudioRouteChooser(supportAudioRoutes)
        } else {
            val isSpeakerOn = !isSpeakerOn
            val newRoute = if (isSpeakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            CallManager.setAudioRoute(newRoute)
        }
    }

    private fun createOrUpdateAudioRouteChooser(routes: Array<AudioRoute>, create: Boolean = true) {
        val callAudioRoute = CallManager.getCallAudioRoute()
        val items = routes
            .sortedByDescending { it.route }
            .map {
                SimpleListItem(id = it.route, textRes = it.stringRes, imageRes = it.iconRes, selected = it == callAudioRoute)
            }
            .toTypedArray()

        if (audioRouteChooserDialog?.isVisible == true) {
            audioRouteChooserDialog?.updateChooserItems(items)
        } else if (create) {
            audioRouteChooserDialog = DynamicBottomSheetChooserDialog.createChooser(
                fragmentManager = supportFragmentManager,
                title = R.string.choose_audio_route,
                items = items
            ) {
                audioRouteChooserDialog = null
                CallManager.setAudioRoute(it.id)
            }
        }
    }

    private fun updateCallAudioState(route: AudioRoute?) {
        if (route != null) {
            isMicrophoneOff = audioManager.isMicrophoneMute
            updateMicrophoneButton()

            isSpeakerOn = route == AudioRoute.SPEAKER
            val supportedAudioRoutes = CallManager.getSupportedAudioRoutes()
            binding.callToggleSpeaker.apply {
                val bluetoothConnected = supportedAudioRoutes.contains(AudioRoute.BLUETOOTH)
                contentDescription = if (bluetoothConnected) {
                    getString(R.string.choose_audio_route)
                } else {
                    getString(if (isSpeakerOn) R.string.turn_speaker_off else R.string.turn_speaker_on)
                }
                // show speaker icon when a headset is connected, a headset icon maybe confusing to some
                if (route == AudioRoute.WIRED_HEADSET) {
                    setImageResource(R.drawable.ic_volume_down_vector)
                } else {
                    setImageResource(route.iconRes)
                }
            }
            toggleButtonColor(binding.callToggleSpeaker, enabled = route != AudioRoute.EARPIECE && route != AudioRoute.WIRED_HEADSET)
            createOrUpdateAudioRouteChooser(supportedAudioRoutes, create = false)

            if (isSpeakerOn) {
                disableProximitySensor()
            } else {
                enableProximitySensor()
            }
        }
    }

    private fun toggleMicrophone() {
        isMicrophoneOff = !isMicrophoneOff
        audioManager.isMicrophoneMute = isMicrophoneOff
        CallManager.inCallService?.setMuted(isMicrophoneOff)
        updateMicrophoneButton()
    }

    private fun updateMicrophoneButton() {
        toggleButtonColor(binding.callToggleMicrophone, isMicrophoneOff)
        binding.callToggleMicrophone.contentDescription =
            getString(if (isMicrophoneOff) R.string.turn_microphone_on else R.string.turn_microphone_off)
    }

    private fun toggleDialpadVisibility() {
        if (binding.dialpadWrapper.isVisible()) hideDialpad() else showDialpad()
    }

    private fun findVisibleViewsUnderDialpad(): Sequence<Pair<View, Float>> {
        return binding.callHolder.children.filter { it.isVisible() }.map { view -> Pair(view, view.alpha) }
    }

    private fun showDialpad() {
        binding.dialpadWrapper.apply {
            translationY = dialpadHeight
            alpha = 0f
            animate()
                .withStartAction { beVisible() }
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setDuration(200L)
                .alpha(1f)
                .translationY(0f)
                .start()
        }

        viewsUnderDialpad.clear()
        viewsUnderDialpad.addAll(findVisibleViewsUnderDialpad())
        viewsUnderDialpad.forEach { (view, _) ->
            view.run {
                animate().scaleX(0f).alpha(0f).withEndAction { beGone() }.duration = 250L
                animate().scaleY(0f).alpha(0f).withEndAction { beGone() }.duration = 250L
            }
        }
    }

    private fun hideDialpad() {
        binding.dialpadWrapper.animate()
            .withEndAction { binding.dialpadWrapper.beGone() }
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setDuration(200L)
            .alpha(0f)
            .translationY(dialpadHeight)
            .start()

        viewsUnderDialpad.forEach { (view, alpha) ->
            view.run {
                animate().withStartAction { beVisible() }.setInterpolator(OvershootInterpolator()).scaleX(1f).alpha(alpha).duration = 250L
                animate().withStartAction { beVisible() }.setInterpolator(OvershootInterpolator()).scaleY(1f).alpha(alpha).duration = 250L
            }
        }
    }

    private fun toggleHold() {
        val isOnHold = CallManager.toggleHold()
        toggleButtonColor(binding.callToggleHold, isOnHold)
        binding.callToggleHold.contentDescription = getString(if (isOnHold) R.string.resume_call else R.string.hold_call)
        binding.holdStatusLabel.beVisibleIf(isOnHold)
    }

    private fun updateOtherPersonsInfo(avatar: Bitmap?) {
        if (callContact == null) {
            return
        }

        binding.callerNameLabel.text = if (callContact!!.name.isNotEmpty()) callContact!!.name else getString(R.string.unknown_caller)
        if (callContact!!.number.isNotEmpty() && callContact!!.number != callContact!!.name) {
            binding.callerNumber.text = callContact!!.number

            if (callContact!!.numberLabel.isNotEmpty()) {
                binding.callerNumber.text = "${callContact!!.number} - ${callContact!!.numberLabel}"
            }
        } else {
            binding.callerNumber.beGone()
        }

        if (avatar != null) {
            binding.callerAvatar.setImageBitmap(avatar)
        } else {
            binding.callerAvatar.setImageDrawable(null)
        }
    }

    private fun getContactNameOrNumber(contact: CallContact): String {
        return contact.name.ifEmpty {
            contact.number.ifEmpty {
                getString(R.string.unknown_caller)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkCalledSIMCard() {
        try {
            val accounts = telecomManager.callCapablePhoneAccounts
            if (accounts.size > 1) {
                accounts.forEachIndexed { index, account ->
                    if (account == CallManager.getPrimaryCall()?.details?.accountHandle) {

                        binding.callSimId.text = "${index + 1}"
                        binding.callSimId.beVisible()
                        binding.callSimImage.beVisible()

                        val acceptDrawableId = when (index) {
                            0 -> R.drawable.ic_phone_one_vector
                            1 -> R.drawable.ic_phone_two_vector
                            else -> R.drawable.ic_phone_vector
                        }

                        val rippleBg = resources.getDrawable(R.drawable.ic_call_accept, theme) as RippleDrawable
                        val layerDrawable = rippleBg.findDrawableByLayerId(R.id.accept_call_background_holder) as LayerDrawable
                        layerDrawable.setDrawableByLayerId(R.id.accept_call_icon, getDrawable(acceptDrawableId))
                        binding.callAccept.setImageDrawable(rippleBg)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun updateCallState(call: Call) {
        val state = call.getStateCompat()
        when (state) {
            Call.STATE_RINGING -> callRinging()
            Call.STATE_ACTIVE -> callStarted()
            Call.STATE_DISCONNECTED -> endCall()
            Call.STATE_CONNECTING, Call.STATE_DIALING -> initOutgoingCallUI()
            Call.STATE_SELECT_PHONE_ACCOUNT -> showPhoneAccountPicker()
        }

        val statusTextId = when (state) {
            Call.STATE_RINGING -> R.string.is_calling
            Call.STATE_CONNECTING, Call.STATE_DIALING -> R.string.dialing
            else -> 0
        }

        if (statusTextId != 0) {
            binding.callStatusLabel.text = getString(statusTextId)
        }

        binding.callManage.beVisibleIf(call.hasCapability(Call.Details.CAPABILITY_MANAGE_CONFERENCE))
        setActionButtonEnabled(binding.callSwap, state == Call.STATE_ACTIVE)
        setActionButtonEnabled(binding.callMerge, state == Call.STATE_ACTIVE)
    }

    private fun updateState() {
        val phoneState = CallManager.getPhoneState()
        if (phoneState is SingleCall) {
            updateCallState(phoneState.call)
            updateCallOnHoldState(null)
            val state = phoneState.call.getStateCompat()
            val isSingleCallActionsEnabled = (state == Call.STATE_ACTIVE || state == Call.STATE_DISCONNECTED
                || state == Call.STATE_DISCONNECTING || state == Call.STATE_HOLDING)
            setActionButtonEnabled(binding.callToggleHold, isSingleCallActionsEnabled)
            setActionButtonEnabled(binding.callAdd, isSingleCallActionsEnabled)
        } else if (phoneState is TwoCalls) {
            updateCallState(phoneState.active)
            updateCallOnHoldState(phoneState.onHold)
        }

        updateCallAudioState(CallManager.getCallAudioRoute())
    }

    private fun updateCallOnHoldState(call: Call?) {
        val hasCallOnHold = call != null
        if (hasCallOnHold) {
            getCallContact(applicationContext, call) { contact ->
                runOnUiThread {
                    binding.onHoldCallerName.text = getContactNameOrNumber(contact)
                }
            }
        }
        binding.onHoldStatusHolder.beVisibleIf(hasCallOnHold)
        binding.controlsSingleCall.beVisibleIf(!hasCallOnHold)
        binding.controlsTwoCalls.beVisibleIf(hasCallOnHold)
    }

    private fun updateCallContactInfo(call: Call?) {
        getCallContact(applicationContext, call) { contact ->
            if (call != CallManager.getPrimaryCall()) {
                return@getCallContact
            }
            callContact = contact
            val avatar = if (!call.isConference()) callContactAvatarHelper.getCallContactAvatar(contact) else null
            runOnUiThread {
                updateOtherPersonsInfo(avatar)
                checkCalledSIMCard()
            }
        }
    }

    private fun acceptCall() {
        CallManager.accept()
    }

    private fun initOutgoingCallUI() {
        enableProximitySensor()
        binding.incomingCallHolder.beGone()
        binding.ongoingCallHolder.beVisible()
    }

    private fun callRinging() {
        binding.incomingCallHolder.beVisible()
    }

    private fun callStarted() {
        enableProximitySensor()
        binding.incomingCallHolder.beGone()
        binding.ongoingCallHolder.beVisible()
        callDurationHandler.removeCallbacks(updateCallDurationTask)
        callDurationHandler.post(updateCallDurationTask)
    }

    private fun showPhoneAccountPicker() {
        if (callContact != null) {
            getHandleToUse(intent, callContact!!.number) { handle ->
                CallManager.getPrimaryCall()?.phoneAccountSelected(handle, false)
            }
        }
    }

    private fun endCall() {
        CallManager.reject()
        disableProximitySensor()
        audioRouteChooserDialog?.dismissAllowingStateLoss()

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
                binding.callStatusLabel.text = "${callDuration.getFormattedDuration()} (${getString(R.string.call_ended)})"
                Handler().postDelayed({
                    finishAndRemoveTask()
                }, 3000)
            }
        } else {
            binding.callStatusLabel.text = getString(R.string.call_ended)
            finish()
        }
    }

    private val callCallback = object : CallManagerListener {
        override fun onStateChanged() {
            updateState()
        }

        override fun onAudioStateChanged(audioState: AudioRoute) {
            updateCallAudioState(audioState)
        }

        override fun onPrimaryCallChanged(call: Call) {
            callDurationHandler.removeCallbacks(updateCallDurationTask)
            updateCallContactInfo(call)
            updateState()
        }
    }

    private val updateCallDurationTask = object : Runnable {
        override fun run() {
            callDuration = CallManager.getPrimaryCall().getCallDuration()
            if (!isCallEnded) {
                binding.callStatusLabel.text = callDuration.getFormattedDuration()
                callDurationHandler.postDelayed(this, 1000)
            }
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

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            screenOnWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "com.simplemobiletools.dialer.pro:full_wake_lock")
            screenOnWakeLock!!.acquire(5 * 1000L)
        } catch (e: Exception) {
        }
    }

    private fun enableProximitySensor() {
        if (!config.disableProximitySensor && (proximityWakeLock == null || proximityWakeLock?.isHeld == false)) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "com.simplemobiletools.dialer.pro:wake_lock")
            proximityWakeLock!!.acquire(60 * MINUTE_SECONDS * 1000L)
        }
    }

    private fun disableProximitySensor() {
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock!!.release()
        }
    }

    private fun setActionButtonEnabled(button: ImageView, enabled: Boolean) {
        button.apply {
            isEnabled = enabled
            alpha = if (enabled) 1.0f else LOWER_ALPHA
        }
    }

    private fun getActiveButtonColor() = getProperPrimaryColor()

    private fun getInactiveButtonColor() = getProperTextColor().adjustAlpha(0.10f)

    private fun toggleButtonColor(view: ImageView, enabled: Boolean) {
        if (enabled) {
            val color = getActiveButtonColor()
            view.background.applyColorFilter(color)
            view.applyColorFilter(color.getContrastColor())
        } else {
            view.background.applyColorFilter(getInactiveButtonColor())
            view.applyColorFilter(getProperBackgroundColor().getContrastColor())
        }
    }
}
