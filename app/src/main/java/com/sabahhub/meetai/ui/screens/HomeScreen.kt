package com.sabahhub.meetai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabahhub.meetai.data.model.Recording
import com.sabahhub.meetai.data.model.RecordingStatus
import com.sabahhub.meetai.ui.MeetAiViewModel
import com.sabahhub.meetai.ui.formatDate
import com.sabahhub.meetai.ui.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MeetAiViewModel,
    onOpenRecording: (String) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.record.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("MeetAI") },
                actions = {
                    if (user != null) {
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (viewModel.authAvailable && user == null) {
                SignInBanner(onSignIn = { viewModel.signIn(context) })
                Spacer(Modifier.height(8.dp))
            }

            RecordControl(
                isRecording = state.isRecording,
                elapsedMs = state.elapsedMs,
                amplitude = state.amplitude,
                enabled = state.processing == null,
                onToggle = { viewModel.toggleRecording() },
            )

            state.processing?.let {
                Spacer(Modifier.height(12.dp))
                ProcessingCard(it)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            if (recordings.isEmpty()) {
                Text(
                    "No recordings yet. Tap the mic to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recordings, key = { it.id }) { rec ->
                        RecordingRow(
                            rec = rec,
                            onClick = { onOpenRecording(rec.id) },
                            onDelete = { viewModel.deleteRecording(rec.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignInBanner(onSignIn: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Sign in to sync", fontWeight = FontWeight.SemiBold)
            Text(
                "Your recordings, transcripts and summaries sync across devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSignIn) { Text("Sign in with Google") }
        }
    }
}

@Composable
private fun RecordControl(
    isRecording: Boolean,
    elapsedMs: Long,
    amplitude: Float,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val scale = 1f + (if (isRecording) amplitude * 0.4f else 0f)
        Box(
            modifier = Modifier
                .size((96 * scale).dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                .clickable(enabled = enabled) { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop" else "Record",
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isRecording) formatDuration(elapsedMs) else "Tap to record",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
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
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RecordingRow(
    rec: Recording,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(rec.title, fontWeight = FontWeight.Medium)
                val meta = buildString {
                    append(formatDate(rec.createdAt))
                    if (rec.durationMs > 0) append(" · ${formatDuration(rec.durationMs)}")
                    rec.language?.let { append(" · ${it.uppercase()}") }
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rec.status == RecordingStatus.FAILED) {
                    Text(
                        "Failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
