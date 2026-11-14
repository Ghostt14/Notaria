/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Build
import android.os.Bundle
import me.zhanghai.android.files.R
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.ui.PreferenceFragmentCompat
import me.zhanghai.android.files.util.valueCompat

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var localePreference: LocalePreference

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        localePreference = preferenceScreen.findPreference(getString(R.string.pref_key_locale))!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            localePreference.setApplicationLocalesPre33 = { locales ->
                val activity = requireActivity()
                if (activity is SettingsActivity) {
                    activity.setApplicationLocalesPre33(locales)
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                }
            }
        }
        setUpThemePreferences()
        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_about_version))
            ?.summary = runCatching {
                requireContext().packageManager
                    .getPackageInfo(requireContext().packageName, 0).versionName
            }.getOrNull()
    }

    // Theme: System / Light / Dark / Pure black, and the main color.
    private fun setUpThemePreferences() {
        val themeMode = findPreference<androidx.preference.ListPreference>(
            getString(R.string.pref_key_theme_mode)
        )!!
        themeMode.value = when {
            Settings.NIGHT_MODE.valueCompat == NightMode.OFF -> "light"
            Settings.NIGHT_MODE.valueCompat == NightMode.ON &&
                Settings.BLACK_NIGHT_MODE.valueCompat -> "black"
            Settings.NIGHT_MODE.valueCompat == NightMode.ON -> "dark"
            else -> "system"
        }
        themeMode.setOnPreferenceChangeListener { _, newValue ->
            when (newValue as String) {
                "light" -> {
                    Settings.BLACK_NIGHT_MODE.putValue(false)
                    Settings.NIGHT_MODE.putValue(NightMode.OFF)
                }
                "dark" -> {
                    Settings.BLACK_NIGHT_MODE.putValue(false)
                    Settings.NIGHT_MODE.putValue(NightMode.ON)
                }
                "black" -> {
                    Settings.BLACK_NIGHT_MODE.putValue(true)
                    Settings.NIGHT_MODE.putValue(NightMode.ON)
                }
                else -> {
                    Settings.BLACK_NIGHT_MODE.putValue(false)
                    Settings.NIGHT_MODE.putValue(NightMode.FOLLOW_SYSTEM)
                }
            }
            true
        }
        val themeColor = findPreference<androidx.preference.ListPreference>(
            getString(R.string.pref_key_theme_color_seed)
        )!!
        themeColor.value = Settings.THEME_SEED.valueCompat.toString()
        themeColor.setOnPreferenceChangeListener { _, newValue ->
            Settings.THEME_SEED.putValue((newValue as String).toInt())
            // Re-theme the screen with the new palette.
            requireActivity().recreate()
            true
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val viewLifecycleOwner = viewLifecycleOwner
        // The following may end up passing the same lambda instance to the observer because it has
        // no capture, and result in an IllegalArgumentException "Cannot add the same observer with
        // different lifecycles" if activity is finished and instantly started again. To work around
        // this, always use an instance method reference.
        // https://stackoverflow.com/a/27524543
        //Settings.THEME_COLOR.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.NIGHT_MODE.observe(viewLifecycleOwner) { NightModeHelper.sync() }
        //Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        Settings.THEME_COLOR.observe(viewLifecycleOwner, this::onThemeColorChanged)
        Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner, this::onMaterialDesign3Changed)
        Settings.NIGHT_MODE.observe(viewLifecycleOwner, this::onNightModeChanged)
        Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner, this::onBlackNightModeChanged)
    }

    private fun onThemeColorChanged(themeColor: ThemeColor) {
        CustomThemeHelper.sync()
    }

    private fun onMaterialDesign3Changed(isMaterialDesign3: Boolean) {
        CustomThemeHelper.sync()
    }

    private fun onNightModeChanged(nightMode: NightMode) {
        NightModeHelper.sync()
    }

    private fun onBlackNightModeChanged(blackNightMode: Boolean) {
        CustomThemeHelper.sync()
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Refresh locale preference summary because we aren't notified for an external change
            // between system default and the locale that's the current system default.
            localePreference.notifyChanged()
        }
    }
}
