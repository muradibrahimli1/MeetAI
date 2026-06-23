package com.sabahhub.meetai.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sabahhub.meetai.MainActivity
import com.sabahhub.meetai.MeetAiApp
import com.sabahhub.meetai.R
import com.sabahhub.meetai.data.model.RecordingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service that keeps recording/processing alive in the background and
 * exposes notification actions (Pause/Resume, Save, Discard) so a recording can
 * be finished without reopening the app.
 *
 * It mirrors [RecordingController]'s state into the notification and stops itself
 * once nothing is recording or processing.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller get() = (application as MeetAiApp).recordingController

    private enum class Phase { RECORDING, PAUSED, PROCESSING, IDLE }
    private data class NotifModel(val phase: Phase, val text: String)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Keep the notification in sync with recording state, and self-stop when idle.
        controller.record
            .map { phaseOf(it) }
            .distinctUntilChanged()
            .onEach { model ->
                if (model.phase == Phase.IDLE) {
                    stopForegroundCompat()
                    stopSelf()
                } else {
                    startForegroundFor(model)
                }
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_RESUME -> controller.togglePause()
            ACTION_SAVE -> controller.saveRecording()
            ACTION_DISCARD -> controller.discardRecording()
            else -> {
                // Initial start — show the foreground notification immediately
                // (required within ~5s of startForegroundService).
                val s = controller.record.value
                val model = if (s.isRecording || s.processing != null) phaseOf(s)
                else NotifModel(Phase.RECORDING, "Recording…")
                startForegroundFor(model)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- notification -------------------------------------------------------

    private fun phaseOf(s: RecordUiState): NotifModel = when {
        s.isRecording && s.isPaused -> NotifModel(Phase.PAUSED, "Paused")
        s.isRecording -> NotifModel(Phase.RECORDING, "Recording…")
        s.processing != null -> NotifModel(Phase.PROCESSING, processingText(s.processing.status))
        else -> NotifModel(Phase.IDLE, "")
    }

    private fun startForegroundFor(model: NotifModel) {
        val notification = buildNotification(model)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = when (model.phase) {
                Phase.RECORDING, Phase.PAUSED -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                Phase.PROCESSING -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                Phase.IDLE -> 0
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(model: NotifModel): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(model.text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        when (model.phase) {
            Phase.RECORDING -> {
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", action(ACTION_PAUSE_RESUME))
                builder.addAction(android.R.drawable.ic_menu_save, "Save", action(ACTION_SAVE))
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Discard", action(ACTION_DISCARD))
            }
            Phase.PAUSED -> {
                builder.addAction(android.R.drawable.ic_media_play, "Resume", action(ACTION_PAUSE_RESUME))
                builder.addAction(android.R.drawable.ic_menu_save, "Save", action(ACTION_SAVE))
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Discard", action(ACTION_DISCARD))
            }
            Phase.PROCESSING -> builder.setProgress(0, 0, true)
            Phase.IDLE -> Unit
        }
        return builder.build()
    }

    private fun action(name: String): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).setAction(name)
        return PendingIntent.getService(this, name.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun processingText(status: RecordingStatus): String = when (status) {
        RecordingStatus.UPLOADING -> "Uploading audio…"
        RecordingStatus.TRANSCRIBING -> "Transcribing & detecting speakers…"
        RecordingStatus.SUMMARIZING -> "Summarizing…"
        else -> "Processing…"
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.recording_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        const val ACTION_PAUSE_RESUME = "com.sabahhub.meetai.PAUSE_RESUME"
        const val ACTION_SAVE = "com.sabahhub.meetai.SAVE"
        const val ACTION_DISCARD = "com.sabahhub.meetai.DISCARD"

        fun start(context: Context) {
            val i = Intent(context, RecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
