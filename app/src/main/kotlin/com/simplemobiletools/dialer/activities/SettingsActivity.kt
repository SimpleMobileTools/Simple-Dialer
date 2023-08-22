package com.simplemobiletools.dialer.activities

import android.annotation.TargetApi
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import com.simplemobiletools.commons.activities.ManageBlockedNumbersActivity
import com.simplemobiletools.commons.dialogs.ChangeDateTimeFormatDialog
import com.simplemobiletools.commons.dialogs.FeatureLockedDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.databinding.ActivitySettingsBinding
import com.simplemobiletools.dialer.dialogs.ExportCallHistoryDialog
import com.simplemobiletools.dialer.dialogs.ManageVisibleTabsDialog
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.helpers.RecentsHelper
import com.simplemobiletools.dialer.models.RecentCall
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {

    private val settingsActivityBinding: ActivitySettingsBinding by viewBinding(ActivitySettingsBinding::inflate)
    private val callHistoryFileType = "application/json"

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            toast(R.string.importing)
            importCallHistory(uri)
        }
    }

    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument(callHistoryFileType)) { uri ->
        if (uri != null) {
            toast(R.string.exporting)
            RecentsHelper(this).getRecentCalls(false, Int.MAX_VALUE) { recents ->
                exportCallHistory(recents, uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(settingsActivityBinding.root)

        updateMaterialActivityViews(
            settingsActivityBinding.settingsCoordinator,
            settingsActivityBinding.settingsHolder,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(settingsActivityBinding.settingsNestedScrollview, settingsActivityBinding.settingsToolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(settingsActivityBinding.settingsToolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupUseEnglish()
        setupLanguage()
        setupManageBlockedNumbers()
        setupManageSpeedDial()
        setupChangeDateTimeFormat()
        setupFontSize()
        setupManageShownTabs()
        setupDefaultTab()
        setupDialPadOpen()
        setupGroupSubsequentCalls()
        setupStartNameWithSurname()
        setupDialpadVibrations()
        setupDialpadNumbers()
        setupDialpadBeeps()
        setupShowCallConfirmation()
        setupDisableProximitySensor()
        setupDisableSwipeToAnswer()
        setupAlwaysShowFullscreen()
        setupCallsExport()
        setupCallsImport()
        updateTextColors(settingsActivityBinding.settingsHolder)

        arrayOf(
            settingsActivityBinding.settingsColorCustomizationSectionLabel,
            settingsActivityBinding.settingsGeneralSettingsLabel,
            settingsActivityBinding.settingsStartupLabel,
            settingsActivityBinding.settingsCallsLabel,
            settingsActivityBinding.settingsMigrationSectionLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupPurchaseThankYou() {
        settingsActivityBinding.settingsPurchaseThankYouHolder.beGoneIf(isOrWasThankYouInstalled())
        settingsActivityBinding.settingsPurchaseThankYouHolder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settingsActivityBinding.settingsColorCustomizationLabel.text = getCustomizeColorsString()
        settingsActivityBinding.settingsColorCustomizationHolder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupUseEnglish() {
        settingsActivityBinding.settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsActivityBinding.settingsUseEnglish.isChecked = config.useEnglish
        settingsActivityBinding.settingsUseEnglishHolder.setOnClickListener {
            settingsActivityBinding.settingsUseEnglish.toggle()
            config.useEnglish = settingsActivityBinding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {

        settingsActivityBinding.settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsActivityBinding.settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        settingsActivityBinding.settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    // support for device-wise blocking came on Android 7, rely only on that
    @TargetApi(Build.VERSION_CODES.N)
    private fun setupManageBlockedNumbers() {
        settingsActivityBinding.settingsManageBlockedNumbersLabel.text = addLockedLabelIfNeeded(R.string.manage_blocked_numbers)
        settingsActivityBinding.settingsManageBlockedNumbersHolder.beVisibleIf(isNougatPlus())
        settingsActivityBinding.settingsManageBlockedNumbersHolder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                Intent(this, ManageBlockedNumbersActivity::class.java).apply {
                    startActivity(this)
                }
            } else {
                FeatureLockedDialog(this) { }
            }
        }
    }

    private fun setupManageSpeedDial() {
        settingsActivityBinding.settingsManageSpeedDialHolder.setOnClickListener {
            Intent(this, ManageSpeedDialActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupChangeDateTimeFormat() {
        settingsActivityBinding.settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupFontSize() {
        settingsActivityBinding.settingsFontSize.text = getFontSizeText()
        settingsActivityBinding.settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settingsActivityBinding.settingsFontSize.text = getFontSizeText()
            }
        }
    }

    private fun setupManageShownTabs() {
        settingsActivityBinding.settingsManageTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupDefaultTab() {
        settingsActivityBinding.settingsDefaultTab.text = getDefaultTabText()
        settingsActivityBinding.settingsDefaultTabHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(TAB_CONTACTS, getString(R.string.contacts_tab)),
                RadioItem(TAB_FAVORITES, getString(R.string.favorites_tab)),
                RadioItem(TAB_CALL_HISTORY, getString(R.string.call_history_tab)),
                RadioItem(TAB_LAST_USED, getString(R.string.last_used_tab))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.defaultTab) {
                config.defaultTab = it as Int
                settingsActivityBinding.settingsDefaultTab.text = getDefaultTabText()
            }
        }
    }

    private fun getDefaultTabText() = getString(
        when (baseConfig.defaultTab) {
            TAB_CONTACTS -> R.string.contacts_tab
            TAB_FAVORITES -> R.string.favorites_tab
            TAB_CALL_HISTORY -> R.string.call_history_tab
            else -> R.string.last_used_tab
        }
    )

    private fun setupDialPadOpen() {

        settingsActivityBinding.settingsOpenDialpadAtLaunch.isChecked = config.openDialPadAtLaunch
        settingsActivityBinding.settingsOpenDialpadAtLaunchHolder.setOnClickListener {
            settingsActivityBinding.settingsOpenDialpadAtLaunch.toggle()
            config.openDialPadAtLaunch = settingsActivityBinding.settingsOpenDialpadAtLaunch.isChecked
        }
    }

    private fun setupGroupSubsequentCalls() {
        settingsActivityBinding.settingsGroupSubsequentCalls.isChecked = config.groupSubsequentCalls
        settingsActivityBinding.settingsGroupSubsequentCallsHolder.setOnClickListener {
            settingsActivityBinding.settingsGroupSubsequentCalls.toggle()
            config.groupSubsequentCalls = settingsActivityBinding.settingsGroupSubsequentCalls.isChecked
        }
    }

    private fun setupStartNameWithSurname() {
        settingsActivityBinding.settingsStartNameWithSurname.isChecked = config.startNameWithSurname
        settingsActivityBinding.settingsStartNameWithSurnameHolder.setOnClickListener {
            settingsActivityBinding.settingsStartNameWithSurname.toggle()
            config.startNameWithSurname = settingsActivityBinding.settingsStartNameWithSurname.isChecked
        }
    }

    private fun setupDialpadVibrations() {
        settingsActivityBinding.settingsDialpadVibration.isChecked = config.dialpadVibration
        settingsActivityBinding.settingsDialpadVibrationHolder.setOnClickListener {
            settingsActivityBinding.settingsDialpadVibration.toggle()
            config.dialpadVibration = settingsActivityBinding.settingsDialpadVibration.isChecked
        }
    }

    private fun setupDialpadNumbers() {
        settingsActivityBinding.settingsHideDialpadNumbers.isChecked = config.hideDialpadNumbers
        settingsActivityBinding.settingsHideDialpadNumbersHolder.setOnClickListener {
            settingsActivityBinding.settingsHideDialpadNumbers.toggle()
            config.hideDialpadNumbers = settingsActivityBinding.settingsHideDialpadNumbers.isChecked
        }
    }

    private fun setupDialpadBeeps() {
        settingsActivityBinding.settingsDialpadBeeps.isChecked = config.dialpadBeeps
        settingsActivityBinding.settingsDialpadBeepsHolder.setOnClickListener {
            settingsActivityBinding.settingsDialpadBeeps.toggle()
            config.dialpadBeeps = settingsActivityBinding.settingsDialpadBeeps.isChecked
        }
    }

    private fun setupShowCallConfirmation() {
        settingsActivityBinding.settingsShowCallConfirmation.isChecked = config.showCallConfirmation
        settingsActivityBinding.settingsShowCallConfirmationHolder.setOnClickListener {
            settingsActivityBinding.settingsShowCallConfirmation.toggle()
            config.showCallConfirmation = settingsActivityBinding.settingsShowCallConfirmation.isChecked
        }
    }

    private fun setupDisableProximitySensor() {
        settingsActivityBinding.settingsDisableProximitySensor.isChecked = config.disableProximitySensor
        settingsActivityBinding.settingsDisableProximitySensorHolder.setOnClickListener {
            settingsActivityBinding.settingsDisableProximitySensor.toggle()
            config.disableProximitySensor = settingsActivityBinding.settingsDisableProximitySensor.isChecked
        }
    }

    private fun setupDisableSwipeToAnswer() {
        settingsActivityBinding.settingsDisableSwipeToAnswer.isChecked = config.disableSwipeToAnswer
        settingsActivityBinding.settingsDisableSwipeToAnswerHolder.setOnClickListener {
            settingsActivityBinding.settingsDisableSwipeToAnswer.toggle()
            config.disableSwipeToAnswer = settingsActivityBinding.settingsDisableSwipeToAnswer.isChecked
        }
    }

    private fun setupAlwaysShowFullscreen() {
        settingsActivityBinding.settingsAlwaysShowFullscreen.isChecked = config.alwaysShowFullscreen
        settingsActivityBinding.settingsAlwaysShowFullscreenHolder.setOnClickListener {
            settingsActivityBinding.settingsAlwaysShowFullscreen.toggle()
            config.alwaysShowFullscreen = settingsActivityBinding.settingsAlwaysShowFullscreen.isChecked
        }
    }

    private fun setupCallsExport() {
        settingsActivityBinding.settingsExportCallsHolder.setOnClickListener {
            ExportCallHistoryDialog(this) { filename ->
                saveDocument.launch(filename)
            }
        }
    }

    private fun setupCallsImport() {
        settingsActivityBinding.settingsImportCallsHolder.setOnClickListener {
            getContent.launch(callHistoryFileType)
        }
    }

    private fun importCallHistory(uri: Uri) {
        try {
            val jsonString = contentResolver.openInputStream(uri)!!.use { inputStream ->
                inputStream.bufferedReader().readText()
            }

            val objects = Json.decodeFromString<List<RecentCall>>(jsonString)

            if (objects.isEmpty()) {
                toast(R.string.no_entries_for_importing)
                return
            }

            RecentsHelper(this).restoreRecentCalls(this, objects) {
                toast(R.string.importing_successful)
            }
        } catch (_: SerializationException) {
            toast(R.string.invalid_file_format)
        } catch (_: IllegalArgumentException) {
            toast(R.string.invalid_file_format)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun exportCallHistory(recents: List<RecentCall>, uri: Uri) {
        if (recents.isEmpty()) {
            toast(R.string.no_entries_for_exporting)
        } else {
            try {
                val outputStream = contentResolver.openOutputStream(uri)!!

                val jsonString = Json.encodeToString(recents)
                outputStream.use {
                    it.write(jsonString.toByteArray())
                }
                toast(R.string.exporting_successful)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}
