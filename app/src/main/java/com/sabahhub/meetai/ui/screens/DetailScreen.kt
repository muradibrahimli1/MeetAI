package com.sabahhub.meetai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    onAsk: (String) -> Unit,
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
                        IconButton(onClick = { onAsk(rec.id) }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Ask")
                        }
                        IconButton(onClick = {
                            val text = if (tab == 0) rec.summary else transcriptText(rec)
                            copyToClipboard(context, text)
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
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

            TagsSection(
                tags = rec.tags,
                onTagsChange = { viewModel.setTags(rec.id, it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

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
                    else -> TranscriptTab(
                        rec = rec,
                        onRenameSpeaker = { old, new -> viewModel.renameSpeaker(rec.id, old, new) },
                    )
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
private fun TranscriptTab(rec: Recording, onRenameSpeaker: (String, String) -> Unit) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    renameTarget?.let { target ->
        SpeakerRenameDialog(
            current = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newName -> renameTarget = null; onRenameSpeaker(target, newName) },
        )
    }

    if (rec.utterances.isNotEmpty()) {
        rec.utterances.forEach { u ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), cornerRadius = 16.dp) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            u.speaker,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable { renameTarget = u.speaker },
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename speaker",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp).clickable { renameTarget = u.speaker },
                        )
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by remember { mutableStateOf(false) }
    if (showAdd) {
        SpeakerRenameDialog(
            current = "",
            title = "Add tag",
            label = "Tag",
            onDismiss = { showAdd = false },
            onConfirm = { tag -> showAdd = false; onTagsChange((tags + tag.trim()).distinct()) },
        )
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            InputChip(
                selected = false,
                onClick = {},
                label = { Text(tag) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove $tag",
                        modifier = Modifier.size(16.dp).clickable { onTagsChange(tags - tag) },
                    )
                },
            )
        }
        AssistChip(
            onClick = { showAdd = true },
            label = { Text("Add tag") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

@Composable
private fun SpeakerRenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "Rename speaker",
    label: String = "Name",
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(label) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun transcriptText(rec: Recording): String =
    if (rec.utterances.isNotEmpty()) rec.utterances.joinToString("\n\n") { "${it.speaker}: ${it.text}" }
    else rec.transcript

private fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MeetAI", text))
    android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
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
