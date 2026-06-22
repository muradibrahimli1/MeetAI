package com.sabahhub.meetai.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sabahhub.meetai.MeetAiApp
import com.sabahhub.meetai.audio.AudioRecorder
import com.sabahhub.meetai.audio.RecordingService
import com.sabahhub.meetai.auth.AuthManager
import com.sabahhub.meetai.data.model.Recording
import com.sabahhub.meetai.data.model.RecordingStatus
import com.sabahhub.meetai.data.remote.AssemblyAiClient
import com.sabahhub.meetai.data.remote.OpenAiClient
import com.sabahhub.meetai.data.sync.FirestoreSync
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class RecordUiState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0,
    val amplitude: Float = 0f,
    /** The recording currently being transcribed/summarized, if any. */
    val processing: Recording? = null,
    val error: String? = null,
)

class MeetAiViewModel(
    private val recorder: AudioRecorder,
    private val assemblyAi: AssemblyAiClient,
    private val openAi: OpenAiClient,
    private val auth: AuthManager,
    private val sync: FirestoreSync,
    private val appContext: Context,
) : ViewModel() {

    private val _record = MutableStateFlow(RecordUiState())
    val record: StateFlow<RecordUiState> = _record.asStateFlow()

    val user: StateFlow<FirebaseUser?> = auth.user

    /** Whether Firebase (sign-in + cloud sync) is configured in this build. */
    val authAvailable: Boolean = auth.available

    /**
     * In-memory history for the current session. Used when Firebase isn't
     * configured or the user isn't signed in, so recordings are still visible
     * (they just don't survive an app restart until cloud sync is enabled).
     */
    private val _local = MutableStateFlow<List<Recording>>(emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recordings: StateFlow<List<Recording>> = auth.user
        .flatMapLatest { u ->
            if (u != null && sync.available) sync.observeRecordings(u.uid).catch { emit(emptyList()) }
            else _local
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun upsertLocal(recording: Recording) {
        _local.value = (listOf(recording) + _local.value.filterNot { it.id == recording.id })
            .sortedByDescending { it.createdAt }
    }

    private var recordingStartedAt = 0L

    // --- auth ---------------------------------------------------------------

    fun signIn(context: Context) = viewModelScope.launch {
        runCatching { auth.signInWithGoogle(context) }
            .onFailure { _record.value = _record.value.copy(error = it.message) }
    }

    fun signOut() = auth.signOut()

    // --- recording ----------------------------------------------------------

    fun toggleRecording() {
        if (_record.value.isRecording) stopAndProcess() else startRecording()
    }

    private fun startRecording() {
        runCatching {
            recorder.start()
            RecordingService.start(appContext)
        }.onFailure {
            _record.value = _record.value.copy(error = "Couldn't start recording: ${it.message}")
            return
        }
        recordingStartedAt = System.currentTimeMillis()
        _record.value = RecordUiState(isRecording = true)
        tickWhileRecording()
    }

    private fun tickWhileRecording() = viewModelScope.launch {
        while (isActive && _record.value.isRecording) {
            _record.value = _record.value.copy(
                elapsedMs = System.currentTimeMillis() - recordingStartedAt,
                amplitude = recorder.amplitude(),
            )
            delay(100)
        }
    }

    private fun stopAndProcess() {
        val file = recorder.stop()
        RecordingService.stop(appContext)
        _record.value = _record.value.copy(isRecording = false, amplitude = 0f)

        if (file == null) {
            _record.value = _record.value.copy(error = "Recording was too short.")
            return
        }
        process(file)
    }

    private fun process(file: File) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        var recording = Recording(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            title = defaultTitle(now),
            status = RecordingStatus.UPLOADING,
            localAudioPath = file.absolutePath,
        )
        _record.value = _record.value.copy(processing = recording, error = null)

        try {
            val result = assemblyAi.transcribe(file) {
                recording = recording.copy(status = RecordingStatus.TRANSCRIBING)
                _record.value = _record.value.copy(processing = recording)
            }
            recording = recording.copy(
                status = RecordingStatus.SUMMARIZING,
                transcript = result.text,
                utterances = result.utterances,
                language = result.language,
                durationMs = result.durationMs,
            )
            _record.value = _record.value.copy(processing = recording)

            val summary = openAi.summarize(result.text)
            recording = recording.copy(status = RecordingStatus.DONE, summary = summary)

            persist(recording)
            _record.value = _record.value.copy(processing = null)
        } catch (e: Exception) {
            val failed = recording.copy(status = RecordingStatus.FAILED, errorMessage = e.message)
            persist(failed)
            _record.value = _record.value.copy(
                processing = null,
                error = "Processing failed: ${e.message}",
            )
        }
    }

    private suspend fun persist(recording: Recording) {
        upsertLocal(recording)                       // always keep session history
        val uid = auth.uid ?: return                 // not signed in — nothing to sync to
        runCatching { sync.save(uid, recording) }    // also push to the cloud
    }

    fun deleteRecording(id: String) = viewModelScope.launch {
        _local.value = _local.value.filterNot { it.id == id }
        val uid = auth.uid ?: return@launch
        runCatching { sync.delete(uid, id) }
    }

    fun clearError() { _record.value = _record.value.copy(error = null) }

    private fun defaultTitle(epochMs: Long): String =
        "Recording " + SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMs))

    // --- factory ------------------------------------------------------------

    companion object {
        fun factory(app: MeetAiApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MeetAiViewModel(
                        recorder = app.audioRecorder,
                        assemblyAi = app.assemblyAi,
                        openAi = app.openAi,
                        auth = app.authManager,
                        sync = app.firestoreSync,
                        appContext = app.applicationContext,
                    ) as T
            }
    }
}
