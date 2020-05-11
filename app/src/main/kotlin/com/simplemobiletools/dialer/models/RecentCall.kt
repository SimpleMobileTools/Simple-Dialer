package com.simplemobiletools.dialer.models

data class RecentCall(var id: Int, var phoneNumber: String, var name: String, var photoUri: String, var startTS: Int, var duration: Int, var type: Int,
                      var neighbourIDs: ArrayList<Int>)
