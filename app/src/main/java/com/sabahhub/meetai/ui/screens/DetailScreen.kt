package com.sabahhub.meetai.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import com.sabahhub.meetai.data.model.Recording
import com.sabahhub.meetai.ui.MeetAiViewModel
import com.sabahhub.meetai.ui.components.AudioPlayerBar
import com.sabahhub.meetai.ui.components.GlassCard
import com.sabahhub.meetai.ui.components.MarkdownText
import com.sabahhub.meetai.ui.formatDate
import com.sabahhub.meetai.ui.formatDuration
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    recordingId: String,
    viewModel: MeetAiViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val rec = recordings.firstOrNull { it.id == recordingId }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text(rec?.title ?: "Recording") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (rec != null) {
                        IconButton(onClick = { shareRecording(context, rec) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (rec == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Recording not found.")
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val meta = buildString {
                    append(formatDate(rec.createdAt))
                    if (rec.durationMs > 0) append(" · ${formatDuration(rec.durationMs)}")
                    rec.language?.let { append(" · ${it.uppercase()}") }
                }
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Audio player — only when the recording's file is on this device.
            val audioPath = rec.localAudioPath
            val audioExists = remember(audioPath) { audioPath != null && File(audioPath).exists() }
            if (audioPath != null && audioExists) {
                AudioPlayerBar(
                    filePath = audioPath,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.secondary,
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Summary") },
                    selectedContentColor = MaterialTheme.colorScheme.secondary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Transcript") },
                    selectedContentColor = MaterialTheme.colorScheme.secondary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                when (tab) {
                    0 -> SummaryTab(rec)
                    else -> TranscriptTab(rec)
                }
            }
        }
    }
}

@Composable
private fun SummaryTab(rec: Recording) {
    if (rec.summary.isBlank()) {
        Text(rec.errorMessage ?: "No summary available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        MarkdownText(rec.summary)
    }
}

@Composable
private fun TranscriptTab(rec: Recording) {
    if (rec.utterances.isNotEmpty()) {
        rec.utterances.forEach { u ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), cornerRadius = 16.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        u.speaker,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(u.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    } else if (rec.transcript.isNotBlank()) {
        Text(
            rec.transcript,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    } else {
        Text("No transcript available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun shareRecording(context: android.content.Context, rec: Recording) {
    val body = buildString {
        appendLine(rec.title)
        appendLine()
        appendLine("== Summary ==")
        appendLine(rec.summary)
        appendLine()
        appendLine("== Transcript ==")
        appendLine(rec.transcript)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, rec.title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share recording"))
}
