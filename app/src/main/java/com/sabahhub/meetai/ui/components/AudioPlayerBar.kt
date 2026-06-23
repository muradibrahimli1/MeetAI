package com.sabahhub.meetai.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sabahhub.meetai.ui.formatDuration
import kotlinx.coroutines.delay

/**
 * Inline audio player for a recorded `.m4a` file: play/pause, a scrubbable
 * progress bar, and elapsed/total times. Owns its [MediaPlayer] and releases it
 * when it leaves composition.
 */
@Composable
fun AudioPlayerBar(filePath: String, modifier: Modifier = Modifier) {
    val player = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    // While the user drags the thumb, show the dragged value instead of playback.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    DisposableEffect(filePath) {
        runCatching {
            player.reset()
            player.setDataSource(filePath)
            player.setOnPreparedListener {
                prepared = true
                durationMs = it.duration.coerceAtLeast(0)
            }
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = 0
                runCatching { it.seekTo(0) }
            }
            player.prepareAsync()
        }
        onDispose { runCatching { player.release() } }
    }

    // Poll playback position while playing.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!scrubbing) positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs)
            delay(200)
        }
    }

    val shownPos = if (scrubbing) scrubValue.toInt() else positionMs

    GlassCard(modifier, cornerRadius = 20.dp) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play / pause button.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
                    .clickable(enabled = prepared) {
                        if (isPlaying) {
                            player.pause(); isPlaying = false
                        } else {
                            runCatching { player.start() }; isPlaying = true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.size(8.dp))

            Column(Modifier.weight(1f)) {
                Slider(
                    value = shownPos.toFloat(),
                    valueRange = 0f..(durationMs.takeIf { it > 0 } ?: 1).toFloat(),
                    enabled = prepared && durationMs > 0,
                    onValueChange = { scrubbing = true; scrubValue = it },
                    onValueChangeFinished = {
                        runCatching { player.seekTo(scrubValue.toInt()) }
                        positionMs = scrubValue.toInt()
                        scrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDuration(shownPos.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatDuration(durationMs.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
