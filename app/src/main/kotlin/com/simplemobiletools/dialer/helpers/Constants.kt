package com.simplemobiletools.dialer.helpers

const val CONTACTS_TAB_MASK = 1
const val FAVORITES_TAB_MASK = 2
const val RECENTS_TAB_MASK = 4
const val ALL_TABS_MASK = CONTACTS_TAB_MASK or FAVORITES_TAB_MASK or RECENTS_TAB_MASK

val tabsList = arrayListOf(CONTACTS_TAB_MASK, RECENTS_TAB_MASK)
