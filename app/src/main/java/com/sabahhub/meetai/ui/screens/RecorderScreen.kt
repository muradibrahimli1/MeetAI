package com.sabahhub.meetai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabahhub.meetai.ui.MeetAiViewModel
import com.sabahhub.meetai.ui.RecordUiState
import com.sabahhub.meetai.ui.components.GlassCard
import com.sabahhub.meetai.ui.components.Waveform
import com.sabahhub.meetai.ui.components.glass
import com.sabahhub.meetai.ui.formatDuration
import com.sabahhub.meetai.data.model.Recording
import com.sabahhub.meetai.data.model.RecordingStatus
import dev.chrisbanes.haze.HazeState

@Composable
fun RecorderScreen(
    state: RecordUiState,
    hazeState: HazeState,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Voice Recorder",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.weight(1f))

        GlassCard(Modifier.fillMaxWidth(), hazeState = hazeState) {
            Waveform(
                amplitudes = state.amplitudes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(20.dp),
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = formatDuration(state.elapsedMs),
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = when {
                state.isPaused -> "Paused"
                state.isRecording -> "Recording"
                state.processing != null -> "Processing…"
                else -> "Tap the mic to start"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        AnimatedVisibility(visible = state.isActive) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionButton(
                    label = "Discard",
                    icon = Icons.Default.Close,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDiscard,
                )
                ActionButton(
                    label = "Save",
                    icon = Icons.Default.Check,
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onSave,
                )
            }
        }

        state.processing?.let {
            Spacer(Modifier.height(8.dp))
            ProcessingCard(it)
        }

        Spacer(Modifier.weight(1f))
        // Leaves room for the bottom bar / FAB drawn by the shell.
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .glass(CircleShape, fillAlpha = 0.12f)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProcessingCard(rec: Recording) {
    val label = when (rec.status) {
        RecordingStatus.UPLOADING -> "Uploading audio…"
        RecordingStatus.TRANSCRIBING -> "Transcribing & detecting speakers…"
        RecordingStatus.SUMMARIZING -> "Summarizing…"
        else -> "Processing…"
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
