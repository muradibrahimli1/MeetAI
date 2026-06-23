package com.sabahhub.meetai.data

import android.content.Context

/** Lightweight user preferences. */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    /** Start recording automatically when the app is launched. */
    var autoStartOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    private companion object {
        const val KEY_AUTO_START = "auto_start_on_launch"
    }
}
