package com.sabahhub.meetai.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Thin wrapper around [MediaRecorder] that records compressed AAC audio into an
 * `.m4a` file. AssemblyAI accepts m4a directly, so no transcoding is needed.
 *
 * One instance records one file. Call [start] then [stop]; reuse is not supported.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Recorder already started" }

        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = r
        outputFile = file
        return file
    }

    /** Current normalized mic amplitude in 0f..1f, for a live level meter. */
    fun amplitude(): Float {
        val r = recorder ?: return 0f
        return (r.maxAmplitude.coerceIn(0, 32767)) / 32767f
    }

    /** Stops recording and returns the finished file, or null if nothing was recorded. */
    fun stop(): File? {
        val r = recorder ?: return null
        return try {
            r.stop()
            outputFile
        } catch (e: RuntimeException) {
            // stop() throws if stop() is called immediately after start() with no frames.
            outputFile?.delete()
            null
        } finally {
            r.release()
            recorder = null
        }
    }
}
