package com.sabahhub.meetai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sabahhub.meetai.MeetAiApp
import com.sabahhub.meetai.audio.RecordUiState
import com.sabahhub.meetai.audio.RecordingController
import com.sabahhub.meetai.data.AppPrefs
import com.sabahhub.meetai.data.ThemeMode
import com.sabahhub.meetai.data.model.Recording
import com.sabahhub.meetai.data.remote.supabase.SupabaseAuth
import com.sabahhub.meetai.data.remote.supabase.SupabaseSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin UI-facing wrapper. The recording session, processing pipeline and
 * history all live in the app-scoped [RecordingController] (so they survive the
 * Activity); this just exposes its state and forwards user actions, plus the
 * UI-only auto-start preference.
 */
class MeetAiViewModel(
    private val controller: RecordingController,
    private val auth: SupabaseAuth,
    private val appPrefs: AppPrefs,
) : ViewModel() {

    val record: StateFlow<RecordUiState> = controller.record
    val recordings: StateFlow<List<Recording>> = controller.recordings
    val session: StateFlow<SupabaseSession?> = auth.session
    val authAvailable: Boolean = auth.available

    private val _autoStart = MutableStateFlow(appPrefs.autoStartOnLaunch)
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    fun setAutoStart(enabled: Boolean) {
        appPrefs.autoStartOnLaunch = enabled
        _autoStart.value = enabled
    }

    private val _themeMode = MutableStateFlow(appPrefs.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        appPrefs.themeMode = mode
        _themeMode.value = mode
    }

    private val _appLock = MutableStateFlow(appPrefs.appLockEnabled)
    val appLock: StateFlow<Boolean> = _appLock.asStateFlow()

    fun setAppLock(enabled: Boolean) {
        appPrefs.appLockEnabled = enabled
        _appLock.value = enabled
    }

    /** Called once per fresh launch (after mic permission is available). */
    fun maybeAutoStartOnLaunch() {
        if (!_autoStart.value) return
        val s = controller.record.value
        if (s.isRecording || s.processing != null) return
        controller.startRecording()
    }

    // --- delegation ---------------------------------------------------------

    fun startRecording() = controller.startRecording()
    fun togglePause() = controller.togglePause()
    fun restartRecording() = controller.restartRecording()
    fun discardRecording() = controller.discardRecording()
    fun saveRecording() = controller.saveRecording()
    fun deleteRecording(id: String) { controller.deleteRecording(id) }
    fun renameRecording(id: String, title: String) { controller.renameRecording(id, title) }
    fun renameSpeaker(id: String, oldName: String, newName: String) { controller.renameSpeaker(id, oldName, newName) }
    fun setTags(id: String, tags: List<String>) { controller.setTags(id, tags) }
    fun clearError() = controller.clearError()
    suspend fun refresh() = controller.refresh()

    suspend fun regenerateSummary(id: String, style: com.sabahhub.meetai.data.remote.OpenAiClient.SummaryStyle) =
        controller.regenerateSummary(id, style)

    /** Ask a question about a transcript. [history] = (fromUser, text) turns. */
    suspend fun ask(transcript: String, history: List<Pair<Boolean, String>>): String =
        controller.ask(
            transcript,
            history.map { com.sabahhub.meetai.data.remote.dto.ChatMessage(if (it.first) "user" else "assistant", it.second) },
        )

    fun signIn(email: String, password: String) { controller.signIn(email, password) }
    fun signUp(email: String, password: String) { controller.signUp(email, password) }
    fun signOut() = controller.signOut()

    companion object {
        fun factory(app: MeetAiApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MeetAiViewModel(
                        controller = app.recordingController,
                        auth = app.supabaseAuth,
                        appPrefs = app.appPrefs,
                    ) as T
            }
    }
}
