package com.sabahhub.meetai.data.model

/** Processing lifecycle of a single recording. */
enum class RecordingStatus {
    RECORDING,      // mic is live
    UPLOADING,      // sending audio to AssemblyAI
    TRANSCRIBING,   // AssemblyAI is working
    SUMMARIZING,    // OpenAI is generating the summary
    DONE,
    FAILED,
}

/** One labeled chunk of speech: "Speaker A said ...". */
data class Utterance(
    val speaker: String,
    val text: String,
    val startMs: Long = 0,
    val endMs: Long = 0,
)

/**
 * A recording and everything derived from it. This is the single object the UI
 * renders and the sync layer persists to Firestore.
 */
data class Recording(
    val id: String,
    val createdAt: Long,
    val title: String,
    val durationMs: Long = 0,
    val status: RecordingStatus = RecordingStatus.RECORDING,
    val language: String? = null,           // detected language code, e.g. "en", "az"
    val transcript: String = "",            // full, speaker-labeled transcript
    val utterances: List<Utterance> = emptyList(),
    val summary: String = "",
    val errorMessage: String? = null,
    /** Local path to the audio file; not synced to the cloud. */
    val localAudioPath: String? = null,
)
