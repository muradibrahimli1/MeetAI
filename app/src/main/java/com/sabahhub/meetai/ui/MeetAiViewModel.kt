package com.sabahhub.meetai.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sabahhub.meetai.MeetAiApp
import com.sabahhub.meetai.audio.AudioRecorder
import com.sabahhub.meetai.audio.RecordingService
import com.sabahhub.meetai.data.AudioStore
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
    private val audioStore: AudioStore,
    private val appContext: Context,
) : ViewModel() {

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    // True when a recording was paused automatically (e.g. by a phone call), so
    // we know to auto-resume when audio focus returns.
    private var wasAutoPaused = false

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
            // Audio isn't synced, so attach any local file we have for playback.
            .onSuccess { list -> _cloud.value = list.map { it.copy(localAudioPath = audioStore.get(it.id)) } }
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
        requestAudioFocus()
        elapsedAccumMs = 0L
        lastResumeAt = System.currentTimeMillis()
        wasAutoPaused = false
        amplitudeHistory.clear()
        _record.value = RecordUiState(isRecording = true, isPaused = false)
        tickWhileRecording()
    }

    /** User-initiated pause/resume from the UI. */
    fun togglePause() {
        val s = _record.value
        if (!s.isRecording) return
        if (s.isPaused) doResume(auto = false) else doPause(auto = false)
    }

    private fun doPause(auto: Boolean) {
        val s = _record.value
        if (!s.isRecording || s.isPaused) return
        recorder.pause()
        elapsedAccumMs += System.currentTimeMillis() - lastResumeAt
        wasAutoPaused = auto
        _record.value = s.copy(isPaused = true)
    }

    private fun doResume(auto: Boolean) {
        val s = _record.value
        if (!s.isRecording || !s.isPaused) return
        recorder.resume()
        lastResumeAt = System.currentTimeMillis()
        wasAutoPaused = false
        _record.value = s.copy(isPaused = false)
    }

    // --- audio focus (auto-pause during phone calls / interruptions) --------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // A call (or another app) grabbed audio — pause so we don't
                // record silence (Android blocks mic capture during calls).
                doPause(auto = true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Call ended / focus returned — resume only if WE auto-paused.
                if (wasAutoPaused) doResume(auto = true)
            }
        }
    }

    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        wasAutoPaused = false
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
        abandonAudioFocus()
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
        abandonAudioFocus()
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
        // Remember the audio file so it can be replayed later (even after restart).
        audioStore.put(recording.id, file.absolutePath)
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
        audioStore.remove(id) // also deletes the local audio file
        if (auth.accessToken == null) return@launch
        runCatching { repo.delete(id) }
    }

    fun clearError() { _record.value = _record.value.copy(error = null) }

    override fun onCleared() {
        abandonAudioFocus()
        super.onCleared()
    }

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
                        audioStore = app.audioStore,
                        appContext = app.applicationContext,
                    ) as T
            }
    }
}
