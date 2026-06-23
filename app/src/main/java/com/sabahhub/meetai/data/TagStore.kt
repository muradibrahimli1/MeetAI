package com.sabahhub.meetai.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Per-recording tags, stored locally (device-scoped). Tags aren't synced to the
 * cloud — only transcript/summary metadata is.
 */
class TagStore(context: Context, private val json: Json) {

    private val prefs = context.getSharedPreferences("recording_tags", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(String.serializer())

    fun get(id: String): List<String> =
        prefs.getString(id, null)?.let {
            runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun set(id: String, tags: List<String>) {
        val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) prefs.edit().remove(id).apply()
        else prefs.edit().putString(id, json.encodeToString(serializer, clean)).apply()
    }

    fun remove(id: String) {
        prefs.edit().remove(id).apply()
    }
}
