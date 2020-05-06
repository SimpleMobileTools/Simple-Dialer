package com.simplemobiletools.dialer.models

import com.simplemobiletools.commons.extensions.normalizeString
import com.simplemobiletools.commons.helpers.SORT_BY_FIRST_NAME
import com.simplemobiletools.commons.helpers.SORT_BY_MIDDLE_NAME
import com.simplemobiletools.commons.helpers.SORT_DESCENDING

data class Contact(var id: Int, var prefix: String, var firstName: String, var middleName: String, var surname: String, var suffix: String,
                   var photoUri: String, var phoneNumbers: ArrayList<PhoneNumber>, var source: String, var starred: Int, var contactId: Int, var thumbnailUri: String,
                   var organization: Organization) : Comparable<Contact> {
    companion object {
        var sorting = 0
        var startWithSurname = false
    }

    override fun compareTo(other: Contact): Int {
        var firstString: String
        var secondString: String

        when {
            sorting and SORT_BY_FIRST_NAME != 0 -> {
                firstString = firstName.normalizeString()
                secondString = other.firstName.normalizeString()
            }
            sorting and SORT_BY_MIDDLE_NAME != 0 -> {
                firstString = middleName.normalizeString()
                secondString = other.middleName.normalizeString()
            }
            else -> {
                firstString = surname.normalizeString()
                secondString = other.surname.normalizeString()
            }
        }

        if (firstString.isEmpty() && firstName.isEmpty() && middleName.isEmpty() && surname.isEmpty()) {
            val fullCompany = getFullCompany()
            if (fullCompany.isNotEmpty()) {
                firstString = fullCompany.normalizeString()
            }
        }

        if (secondString.isEmpty() && other.firstName.isEmpty() && other.middleName.isEmpty() && other.surname.isEmpty()) {
            val otherFullCompany = other.getFullCompany()
            if (otherFullCompany.isNotEmpty()) {
                secondString = otherFullCompany.normalizeString()
            }
        }

        var result = if (firstString.firstOrNull()?.isLetter() == true && secondString.firstOrNull()?.isLetter() == false) {
            -1
        } else if (firstString.firstOrNull()?.isLetter() == false && secondString.firstOrNull()?.isLetter() == true) {
            1
        } else {
            if (firstString.isEmpty() && secondString.isNotEmpty()) {
                1
            } else if (firstString.isNotEmpty() && secondString.isEmpty()) {
                -1
            } else {
                if (firstString.toLowerCase() == secondString.toLowerCase()) {
                    getNameToDisplay().compareTo(other.getNameToDisplay(), true)
                } else {
                    firstString.compareTo(secondString, true)
                }
            }
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }

    fun getNameToDisplay(): String {
        var firstPart = if (startWithSurname) surname else firstName
        if (middleName.isNotEmpty()) {
            firstPart += " $middleName"
        }

        val lastPart = if (startWithSurname) firstName else surname
        val suffixComma = if (suffix.isEmpty()) "" else ", $suffix"
        val fullName = "$prefix $firstPart $lastPart$suffixComma".trim()
        return if (fullName.isEmpty() && organization.isNotEmpty()) {
            getFullCompany()
        } else {
            fullName
        }
    }

    fun getFullCompany(): String {
        var fullOrganization = if (organization.company.isEmpty()) "" else "${organization.company}, "
        fullOrganization += organization.jobPosition
        return fullOrganization.trim().trimEnd(',')
    }
}
