package com.sabahhub.meetai.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sabahhub.meetai.MeetAiApp
import com.sabahhub.meetai.audio.AudioRecorder
import com.sabahhub.meetai.audio.RecordingService
import com.sabahhub.meetai.data.model.Recording
import com.sabahhub.meetai.data.model.RecordingStatus
import com.sabahhub.meetai.data.remote.AssemblyAiClient
import com.sabahhub.meetai.data.remote.OpenAiClient
import com.sabahhub.meetai.data.remote.supabase.SupabaseAuth
import com.sabahhub.meetai.data.remote.supabase.SupabaseRepository
import com.sabahhub.meetai.data.remote.supabase.SupabaseSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
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
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0,
    val amplitude: Float = 0f,
    /** Recent normalized amplitudes (oldest first) for the live waveform. */
    val amplitudes: List<Float> = emptyList(),
    /** The recording currently being transcribed/summarized, if any. */
    val processing: Recording? = null,
    val error: String? = null,
) {
    val isActive: Boolean get() = isRecording
}

class MeetAiViewModel(
    private val recorder: AudioRecorder,
    private val assemblyAi: AssemblyAiClient,
    private val openAi: OpenAiClient,
    private val auth: SupabaseAuth,
    private val repo: SupabaseRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _record = MutableStateFlow(RecordUiState())
    val record: StateFlow<RecordUiState> = _record.asStateFlow()

    val session: StateFlow<SupabaseSession?> = auth.session

    /** Whether Supabase (sign-in + cloud sync) is configured in this build. */
    val authAvailable: Boolean = auth.available

    /**
     * In-memory history for the current session, used when signed out. When
     * signed in, [_cloud] (fetched from Supabase) is shown instead.
     */
    private val _local = MutableStateFlow<List<Recording>>(emptyList())
    private val _cloud = MutableStateFlow<List<Recording>>(emptyList())

    val recordings: StateFlow<List<Recording>> =
        combine(auth.session, _local, _cloud) { session, local, cloud ->
            if (session != null && repo.available) cloud else local
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Whenever the signed-in user changes, refresh the cloud list (or clear it).
        auth.session
            .onEach { session ->
                if (session != null && repo.available) refreshCloud() else _cloud.value = emptyList()
            }
            .launchIn(viewModelScope)
    }

    private fun upsertLocal(recording: Recording) {
        _local.value = (listOf(recording) + _local.value.filterNot { it.id == recording.id })
            .sortedByDescending { it.createdAt }
    }

    private suspend fun refreshCloud() {
        runCatching { repo.list() }
            .onSuccess { _cloud.value = it }
            .onFailure { _record.value = _record.value.copy(error = "Couldn't load cloud recordings: ${it.message}") }
    }

    // Elapsed time is accumulated so it freezes while paused.
    private var elapsedAccumMs = 0L
    private var lastResumeAt = 0L
    private val amplitudeHistory = ArrayDeque<Float>()

    // --- auth ---------------------------------------------------------------

    fun signIn(email: String, password: String) = viewModelScope.launch {
        runCatching { auth.signIn(email, password) }
            .onFailure { _record.value = _record.value.copy(error = it.message) }
    }

    fun signUp(email: String, password: String) = viewModelScope.launch {
        runCatching { auth.signUp(email, password) }
            .onFailure { _record.value = _record.value.copy(error = it.message) }
    }

    fun signOut() {
        auth.signOut()
        _cloud.value = emptyList()
    }

    // --- recording ----------------------------------------------------------

    fun startRecording() {
        if (_record.value.isRecording) return
        runCatching {
            recorder.start()
            RecordingService.start(appContext)
        }.onFailure {
            _record.value = _record.value.copy(error = "Couldn't start recording: ${it.message}")
            return
        }
        elapsedAccumMs = 0L
        lastResumeAt = System.currentTimeMillis()
        amplitudeHistory.clear()
        _record.value = RecordUiState(isRecording = true, isPaused = false)
        tickWhileRecording()
    }

    fun togglePause() {
        val s = _record.value
        if (!s.isRecording) return
        if (s.isPaused) {
            recorder.resume()
            lastResumeAt = System.currentTimeMillis()
            _record.value = s.copy(isPaused = false)
        } else {
            recorder.pause()
            elapsedAccumMs += System.currentTimeMillis() - lastResumeAt
            _record.value = s.copy(isPaused = true)
        }
    }

    private fun currentElapsed(): Long {
        val s = _record.value
        return if (s.isPaused) elapsedAccumMs
        else elapsedAccumMs + (System.currentTimeMillis() - lastResumeAt)
    }

    private fun tickWhileRecording() = viewModelScope.launch {
        while (isActive && _record.value.isRecording) {
            if (!_record.value.isPaused) {
                val amp = recorder.amplitude()
                amplitudeHistory.addLast(amp)
                while (amplitudeHistory.size > MAX_WAVE_SAMPLES) amplitudeHistory.removeFirst()
                _record.value = _record.value.copy(
                    elapsedMs = currentElapsed(),
                    amplitude = amp,
                    amplitudes = amplitudeHistory.toList(),
                )
            }
            delay(80)
        }
    }

    /** Discards the in-progress recording without transcribing. */
    fun discardRecording() {
        recorder.discard()
        RecordingService.stop(appContext)
        _record.value = _record.value.copy(
            isRecording = false, isPaused = false, amplitude = 0f, amplitudes = emptyList(), elapsedMs = 0,
        )
    }

    /** Stops the recording and runs the transcription + summary pipeline. */
    fun saveRecording() {
        if (!_record.value.isRecording) return
        if (_record.value.isPaused) recorder.resume() // stop() requires an active recorder
        val file = recorder.stop()
        RecordingService.stop(appContext)
        _record.value = _record.value.copy(
            isRecording = false, isPaused = false, amplitude = 0f, amplitudes = emptyList(), elapsedMs = 0,
        )

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
            recording = recording.copy(
                status = RecordingStatus.DONE,
                summary = summary.summary,
                // Use the AI-generated title; fall back to the timestamp title.
                title = summary.title.ifBlank { recording.title },
            )

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
        if (auth.accessToken == null) return         // not signed in — nothing to sync to
        runCatching {
            repo.upsert(recording)                   // push to Supabase
            refreshCloud()                           // reflect it in the cloud list
        }
    }

    fun deleteRecording(id: String) = viewModelScope.launch {
        _local.value = _local.value.filterNot { it.id == id }
        _cloud.value = _cloud.value.filterNot { it.id == id }
        if (auth.accessToken == null) return@launch
        runCatching { repo.delete(id) }
    }

    fun clearError() { _record.value = _record.value.copy(error = null) }

    private fun defaultTitle(epochMs: Long): String =
        "Recording " + SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMs))

    // --- factory ------------------------------------------------------------

    companion object {
        private const val MAX_WAVE_SAMPLES = 80

        fun factory(app: MeetAiApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MeetAiViewModel(
                        recorder = app.audioRecorder,
                        assemblyAi = app.assemblyAi,
                        openAi = app.openAi,
                        auth = app.supabaseAuth,
                        repo = app.supabaseRepo,
                        appContext = app.applicationContext,
                    ) as T
            }
    }
}
