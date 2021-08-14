package com.simplemobiletools.dialer.helpers

// shared prefs
const val SPEED_DIAL = "speed_dial"
const val REMEMBER_SIM_PREFIX = "remember_sim_"
const val GROUP_SUBSEQUENT_CALLS = "group_subsequent_calls"
const val OPEN_DIAL_PAD_AT_LAUNCH = "open_dial_pad_at_launch"

const val CONTACTS_TAB_MASK = 1
const val FAVORITES_TAB_MASK = 2
const val RECENTS_TAB_MASK = 4

val tabsList = arrayListOf(CONTACTS_TAB_MASK, FAVORITES_TAB_MASK, RECENTS_TAB_MASK)

private const val PATH = "com.simplemobiletools.dialer.action."
const val ACCEPT_CALL = PATH + "accept_call"
const val DECLINE_CALL = PATH + "decline_call"
