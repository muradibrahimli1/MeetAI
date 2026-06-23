package com.sabahhub.meetai.data

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Lightweight user preferences. */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    /** Start recording automatically when the app is launched. */
    var autoStartOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    private companion object {
        const val KEY_AUTO_START = "auto_start_on_launch"
        const val KEY_THEME = "theme_mode"
    }
}
