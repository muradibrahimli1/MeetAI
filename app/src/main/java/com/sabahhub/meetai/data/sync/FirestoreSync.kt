package com.sabahhub.meetai.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sabahhub.meetai.data.model.Recording
import com.sabahhub.meetai.data.model.RecordingStatus
import com.sabahhub.meetai.data.model.Utterance
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Cloud sync for recordings, scoped per Firebase user at
 * `users/{uid}/recordings/{id}`. Firestore's offline persistence (on by
 * default) means writes are cached locally and replayed when back online, so
 * this layer also serves as the on-device store.
 *
 * Audio files are NOT uploaded — only the transcript/summary metadata.
 */
class FirestoreSync(
    // Null when Firebase isn't configured. Sync becomes a no-op in that case.
    private val db: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull(),
) {
    val available: Boolean get() = db != null

    private fun collection(uid: String) =
        db!!.collection("users").document(uid).collection("recordings")

    fun observeRecordings(uid: String): Flow<List<Recording>> = callbackFlow {
        if (db == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val registration = collection(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toRecording() }.orEmpty()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    suspend fun save(uid: String, recording: Recording) {
        if (db == null) return
        collection(uid).document(recording.id).set(recording.toMap()).await()
    }

    suspend fun delete(uid: String, id: String) {
        if (db == null) return
        collection(uid).document(id).delete().await()
    }

    // --- mapping ------------------------------------------------------------

    private fun Recording.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "createdAt" to createdAt,
        "title" to title,
        "durationMs" to durationMs,
        "status" to status.name,
        "language" to language,
        "transcript" to transcript,
        "summary" to summary,
        "errorMessage" to errorMessage,
        "utterances" to utterances.map {
            mapOf("speaker" to it.speaker, "text" to it.text, "startMs" to it.startMs, "endMs" to it.endMs)
        },
    )

    @Suppress("UNCHECKED_CAST")
    private fun com.google.firebase.firestore.DocumentSnapshot.toRecording(): Recording? {
        val id = getString("id") ?: return null
        val utterances = (get("utterances") as? List<Map<String, Any?>>).orEmpty().map {
            Utterance(
                speaker = it["speaker"] as? String ?: "",
                text = it["text"] as? String ?: "",
                startMs = (it["startMs"] as? Number)?.toLong() ?: 0,
                endMs = (it["endMs"] as? Number)?.toLong() ?: 0,
            )
        }
        return Recording(
            id = id,
            createdAt = getLong("createdAt") ?: 0,
            title = getString("title") ?: "Recording",
            durationMs = getLong("durationMs") ?: 0,
            status = runCatching { RecordingStatus.valueOf(getString("status") ?: "") }
                .getOrDefault(RecordingStatus.DONE),
            language = getString("language"),
            transcript = getString("transcript") ?: "",
            utterances = utterances,
            summary = getString("summary") ?: "",
            errorMessage = getString("errorMessage"),
        )
    }
}
