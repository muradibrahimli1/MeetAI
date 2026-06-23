package com.sabahhub.meetai.data

import android.content.Context
import java.io.File

/**
 * Maps a recording id to its local audio file path. Audio isn't uploaded to the
 * cloud (only transcript/summary), so this lets playback survive app restarts
 * for recordings made on this device.
 */
class AudioStore(context: Context) {

    private val prefs = context.getSharedPreferences("audio_index", Context.MODE_PRIVATE)

    fun put(id: String, path: String) {
        prefs.edit().putString(id, path).apply()
    }

    /** Returns the stored path only if the file still exists on disk. */
    fun get(id: String): String? =
        prefs.getString(id, null)?.takeIf { File(it).exists() }

    fun remove(id: String) {
        prefs.getString(id, null)?.let { runCatching { File(it).delete() } }
        prefs.edit().remove(id).apply()
    }
}
