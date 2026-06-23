package com.sabahhub.meetai.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.sabahhub.meetai.data.AudioStore
import com.sabahhub.meetai.data.TagStore
import com.sabahhub.meetai.data.model.Recording
import com.sabahhub.meetai.data.model.RecordingStatus
import com.sabahhub.meetai.data.remote.AssemblyAiClient
import com.sabahhub.meetai.data.remote.OpenAiClient
import com.sabahhub.meetai.data.remote.supabase.SupabaseAuth
import com.sabahhub.meetai.data.remote.supabase.SupabaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

/**
 * App-scoped owner of the recording session and the transcribe → summarize →
 * sync pipeline. Lives on an application-level [CoroutineScope] (not the
 * ViewModel's), so a recording can be saved and fully processed even when the
 * UI is gone — e.g. from the notification's "Save" action.
 */
class RecordingController(
    private val recorder: AudioRecorder,
    private val assemblyAi: AssemblyAiClient,
    private val openAi: OpenAiClient,
    private val auth: SupabaseAuth,
    private val repo: SupabaseRepository,
    private val audioStore: AudioStore,
    private val tagStore: TagStore,
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var wasAutoPaused = false

    private val _record = MutableStateFlow(RecordUiState())
    val record: StateFlow<RecordUiState> = _record.asStateFlow()

    private val _local = MutableStateFlow<List<Recording>>(emptyList())
    private val _cloud = MutableStateFlow<List<Recording>>(emptyList())

    val recordings: StateFlow<List<Recording>> =
        combine(auth.session, _local, _cloud) { session, local, cloud ->
            if (session != null && repo.available) cloud else local
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var elapsedAccumMs = 0L
    private var lastResumeAt = 0L
    private val amplitudeHistory = ArrayDeque<Float>()

    init {
        auth.session
            .onEach { session ->
                if (session != null && repo.available) refreshCloud() else _cloud.value = emptyList()
            }
            .launchIn(scope)
    }

    // --- auth ---------------------------------------------------------------

    fun signIn(email: String, password: String) = scope.launch {
        runCatching { auth.signIn(email, password) }.onFailure { setError(it.message) }
    }

    fun signUp(email: String, password: String) = scope.launch {
        runCatching { auth.signUp(email, password) }.onFailure { setError(it.message) }
    }

    fun signOut() {
        auth.signOut()
        _cloud.value = emptyList()
    }

    // --- recording controls -------------------------------------------------

    fun startRecording() {
        if (_record.value.isRecording) return
        runCatching { recorder.start() }
            .onFailure { setError("Couldn't start recording: ${it.message}"); return }

        elapsedAccumMs = 0L
        lastResumeAt = System.currentTimeMillis()
        wasAutoPaused = false
        amplitudeHistory.clear()
        _record.value = RecordUiState(isRecording = true, isPaused = false)
        RecordingService.start(appContext) // state is set first so the service sees "recording"
        requestAudioFocus()
        tickWhileRecording()
    }

    fun togglePause() {
        val s = _record.value
        if (!s.isRecording) return
        if (s.isPaused) doResume(auto = false) else doPause(auto = false)
    }

    /**
     * Discards the current take and starts a fresh one. The mic/audio-focus and
     * foreground service stay active throughout (state never returns to idle),
     * so there's no flicker — it just resets to 00:00.
     */
    fun restartRecording() {
        if (!_record.value.isRecording) {
            startRecording()
            return
        }
        recorder.discard()
        runCatching { recorder.start() }.onFailure {
            // Couldn't restart — fall back to a clean idle state.
            abandonAudioFocus()
            _record.value = RecordUiState(error = "Couldn't restart recording: ${it.message}")
            return
        }
        elapsedAccumMs = 0L
        lastResumeAt = System.currentTimeMillis()
        wasAutoPaused = false
        amplitudeHistory.clear()
        _record.value = RecordUiState(isRecording = true, isPaused = false)
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

    /** Discards the in-progress recording without transcribing. */
    fun discardRecording() {
        recorder.discard()
        abandonAudioFocus()
        _record.value = _record.value.copy(
            isRecording = false, isPaused = false, amplitude = 0f, amplitudes = emptyList(), elapsedMs = 0,
        )
    }

    /** Stops recording and runs the transcription + summary pipeline. */
    fun saveRecording() {
        if (!_record.value.isRecording) return
        if (_record.value.isPaused) recorder.resume() // stop() requires an active recorder
        val file = recorder.stop()
        abandonAudioFocus()
        _record.value = _record.value.copy(
            isRecording = false, isPaused = false, amplitude = 0f, amplitudes = emptyList(), elapsedMs = 0,
        )
        if (file == null) {
            setError("Recording was too short.")
            return
        }
        process(file)
    }

    fun renameRecording(id: String, newTitle: String) = scope.launch {
        val title = newTitle.trim()
        if (title.isEmpty()) return@launch
        _local.value = _local.value.map { if (it.id == id) it.copy(title = title) else it }
        _cloud.value = _cloud.value.map { if (it.id == id) it.copy(title = title) else it }
        if (auth.accessToken == null) return@launch
        runCatching { repo.updateTitle(id, title) }
    }

    /** Renames a speaker across a recording's utterances and transcript, then syncs. */
    fun renameSpeaker(recordingId: String, oldName: String, newName: String) = scope.launch {
        val name = newName.trim()
        if (name.isEmpty() || name == oldName) return@launch

        fun remap(list: List<Recording>) = list.map { r ->
            if (r.id != recordingId) return@map r
            val utterances = r.utterances.map { if (it.speaker == oldName) it.copy(speaker = name) else it }
            val transcript = if (utterances.isNotEmpty())
                utterances.joinToString("\n\n") { "${it.speaker}: ${it.text}" } else r.transcript
            r.copy(utterances = utterances, transcript = transcript)
        }
        _local.value = remap(_local.value)
        _cloud.value = remap(_cloud.value)

        val updated = (_cloud.value + _local.value).firstOrNull { it.id == recordingId } ?: return@launch
        if (auth.accessToken == null) return@launch
        runCatching { repo.upsert(updated) }
    }

    fun deleteRecording(id: String) = scope.launch {
        _local.value = _local.value.filterNot { it.id == id }
        _cloud.value = _cloud.value.filterNot { it.id == id }
        audioStore.remove(id)
        tagStore.remove(id)
        if (auth.accessToken == null) return@launch
        runCatching { repo.delete(id) }
    }

    fun clearError() { _record.value = _record.value.copy(error = null) }

    /** Re-fetch the cloud list (used by pull-to-refresh). No-op when signed out. */
    suspend fun refresh() {
        if (auth.accessToken != null && repo.available) refreshCloud()
    }

    /** Q&A over a transcript. [history] alternates user/assistant turns. */
    suspend fun ask(transcript: String, history: List<com.sabahhub.meetai.data.remote.dto.ChatMessage>): String =
        openAi.ask(transcript, history)

    private fun setError(message: String?) { _record.value = _record.value.copy(error = message) }

    // --- audio focus (auto-pause during phone calls / interruptions) --------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> doPause(auto = true)
            AudioManager.AUDIOFOCUS_GAIN -> if (wasAutoPaused) doResume(auto = true)
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

    // --- pipeline -----------------------------------------------------------

    private fun tickWhileRecording() = scope.launch {
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

    private fun currentElapsed(): Long {
        val s = _record.value
        return if (s.isPaused) elapsedAccumMs
        else elapsedAccumMs + (System.currentTimeMillis() - lastResumeAt)
    }

    private fun process(file: File) = scope.launch {
        val now = System.currentTimeMillis()
        var recording = Recording(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            title = defaultTitle(now),
            status = RecordingStatus.UPLOADING,
            localAudioPath = file.absolutePath,
        )
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
                title = summary.title.ifBlank { recording.title },
            )
            persist(recording)
            _record.value = _record.value.copy(processing = null)
        } catch (e: Exception) {
            val failed = recording.copy(status = RecordingStatus.FAILED, errorMessage = e.message)
            persist(failed)
            _record.value = _record.value.copy(processing = null, error = "Processing failed: ${e.message}")
        }
    }

    private suspend fun persist(recording: Recording) {
        upsertLocal(recording)
        if (auth.accessToken == null) return
        runCatching {
            repo.upsert(recording)
            refreshCloud()
        }
    }

    private fun upsertLocal(recording: Recording) {
        val tagged = recording.copy(tags = tagStore.get(recording.id))
        _local.value = (listOf(tagged) + _local.value.filterNot { it.id == recording.id })
            .sortedByDescending { it.createdAt }
    }

    private suspend fun refreshCloud() {
        runCatching { repo.list() }
            .onSuccess { list ->
                _cloud.value = list.map {
                    it.copy(localAudioPath = audioStore.get(it.id), tags = tagStore.get(it.id))
                }
            }
            .onFailure { setError("Couldn't load cloud recordings: ${it.message}") }
    }

    fun setTags(id: String, tags: List<String>) = scope.launch {
        tagStore.set(id, tags)
        val clean = tagStore.get(id)
        _local.value = _local.value.map { if (it.id == id) it.copy(tags = clean) else it }
        _cloud.value = _cloud.value.map { if (it.id == id) it.copy(tags = clean) else it }
    }

    private fun defaultTitle(epochMs: Long): String =
        "Recording " + SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMs))

    private companion object {
        const val MAX_WAVE_SAMPLES = 80
    }
}
