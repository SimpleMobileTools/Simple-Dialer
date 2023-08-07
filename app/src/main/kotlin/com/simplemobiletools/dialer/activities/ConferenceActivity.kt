package com.simplemobiletools.dialer.activities

import android.os.Bundle
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.dialer.adapters.ConferenceCallsAdapter
import com.simplemobiletools.dialer.databinding.ActivityConferenceBinding
import com.simplemobiletools.dialer.helpers.CallManager

class ConferenceActivity : SimpleActivity() {

    private lateinit var binding : ActivityConferenceBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivityConferenceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateMaterialActivityViews(binding.conferenceCoordinator, binding.conferenceList, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.conferenceList, binding.conferenceToolbar)
        binding.conferenceList.adapter = ConferenceCallsAdapter(this, binding.conferenceList, ArrayList(CallManager.getConferenceCalls())) {}
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.conferenceToolbar, NavigationIcon.Arrow)
    }
}
