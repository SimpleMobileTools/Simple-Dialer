package com.simplemobiletools.dialer.activities

import android.os.Bundle
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.adapters.ConferenceCallsAdapter
import com.simplemobiletools.dialer.helpers.CallManager
import kotlinx.android.synthetic.main.activity_conference.*

class ConferenceActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conference)
        updateMaterialActivityViews(conference_coordinator, conference_list, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(conference_list, conference_toolbar)
        conference_list.adapter = ConferenceCallsAdapter(this, conference_list, ArrayList(CallManager.getConferenceCalls())) {}
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(conference_toolbar, NavigationIcon.Arrow)
    }
}
