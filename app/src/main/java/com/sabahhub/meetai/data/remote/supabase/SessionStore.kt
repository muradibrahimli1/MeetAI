package com.sabahhub.meetai.data.remote.supabase

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Persists the Supabase auth session in private SharedPreferences so the user
 * stays logged in across app restarts.
 */
class SessionStore(context: Context, private val json: Json) {

    private val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)

    fun load(): SupabaseSession? =
        prefs.getString(KEY, null)?.let {
            runCatching { json.decodeFromString(SupabaseSession.serializer(), it) }.getOrNull()
        }

    fun save(session: SupabaseSession) {
        prefs.edit().putString(KEY, json.encodeToString(SupabaseSession.serializer(), session)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "session"
    }
}
