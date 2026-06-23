package com.sabahhub.meetai

import android.app.Application
import com.sabahhub.meetai.audio.AudioRecorder
import com.sabahhub.meetai.data.AudioStore
import com.sabahhub.meetai.data.remote.AssemblyAiClient
import com.sabahhub.meetai.data.remote.OpenAiClient
import com.sabahhub.meetai.data.remote.supabase.SessionStore
import com.sabahhub.meetai.data.remote.supabase.SupabaseAuth
import com.sabahhub.meetai.data.remote.supabase.SupabaseRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application entry point and a tiny manual service locator. The app is small
 * enough that a DI framework (Hilt) would be more ceremony than value; these
 * singletons are created lazily and shared.
 */
class MeetAiApp : Application() {

    val json by lazy { Json { ignoreUnknownKeys = true } }

    val httpClient by lazy {
        OkHttpClient.Builder()
            // Transcription jobs poll for a while; uploads can be large.
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    val assemblyAi by lazy { AssemblyAiClient(httpClient, json) }
    val openAi by lazy { OpenAiClient(httpClient, json) }
    private val sessionStore by lazy { SessionStore(this, json) }
    val supabaseAuth by lazy { SupabaseAuth(httpClient, json, sessionStore) }
    val supabaseRepo by lazy { SupabaseRepository(httpClient, json, supabaseAuth) }
    val audioRecorder by lazy { AudioRecorder(this) }
    val audioStore by lazy { AudioStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MeetAiApp
            private set
    }
}
