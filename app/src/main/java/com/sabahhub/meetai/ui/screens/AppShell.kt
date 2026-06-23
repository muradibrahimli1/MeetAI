package com.sabahhub.meetai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import com.sabahhub.meetai.ui.MeetAiViewModel
import com.sabahhub.meetai.ui.theme.Mint
import com.sabahhub.meetai.ui.theme.Navy
import com.sabahhub.meetai.ui.theme.Teal

private enum class Tab { Recorder, Library, Settings }

@Composable
fun AppShell(
    viewModel: MeetAiViewModel,
    hazeState: HazeState,
    onOpenRecording: (String) -> Unit,
) {
    val state by viewModel.record.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(Tab.Recorder) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Tab content. Padded below the status bar (we draw edge-to-edge).
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            when (tab) {
                Tab.Recorder -> RecorderScreen(
                    state = state,
                    hazeState = hazeState,
                    onDiscard = viewModel::discardRecording,
                    onSave = viewModel::saveRecording,
                )
                Tab.Library -> LibraryScreen(
                    recordings = recordings,
                    hazeState = hazeState,
                    onOpen = onOpenRecording,
                    onDelete = viewModel::deleteRecording,
                )
                Tab.Settings -> SettingsScreen(
                    session = session,
                    authAvailable = viewModel.authAvailable,
                    onSignIn = viewModel::signIn,
                    onSignUp = viewModel::signUp,
                    onSignOut = viewModel::signOut,
                )
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.navigationBars),
        )

        BottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
            selected = tab,
            recording = state.isRecording,
            paused = state.isPaused,
            onSelectLibrary = { tab = Tab.Library },
            onSelectSettings = { tab = Tab.Settings },
            onCenter = {
                when {
                    tab != Tab.Recorder -> tab = Tab.Recorder
                    state.isRecording -> viewModel.togglePause()
                    else -> viewModel.startRecording()
                }
            },
        )
    }
}

@Composable
private fun BottomBar(
    modifier: Modifier,
    hazeState: HazeState,
    selected: Tab,
    recording: Boolean,
    paused: Boolean,
    onSelectLibrary: () -> Unit,
    onSelectSettings: () -> Unit,
    onCenter: () -> Unit,
) {
    val barShape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Glass bar — Haze blurs the content scrolling behind it; the translucent
        // white overlay + border add the frosted tint and edge highlight.
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(barShape)
                .hazeChild(
                    state = hazeState,
                    shape = barShape,
                    style = HazeStyle(
                        backgroundColor = Navy,
                        tint = HazeTint(Color.White.copy(alpha = 0.10f)),
                        blurRadius = 24.dp,
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.07f))
                    ),
                    shape = barShape,
                )
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavIcon(
                icon = Icons.Default.FolderOpen,
                active = selected == Tab.Library,
                onClick = onSelectLibrary,
            )
            Box(Modifier.weight(1f))
            NavIcon(
                icon = Icons.Default.Settings,
                active = selected == Tab.Settings,
                onClick = onSelectSettings,
            )
        }

        // Center mic / pause FAB, elevated above the bar.
        val centerIcon = when {
            selected == Tab.Recorder && recording && !paused -> Icons.Default.Pause
            selected == Tab.Recorder && recording && paused -> Icons.Default.PlayArrow
            else -> Icons.Default.Mic
        }
        Box(
            Modifier
                .size(64.dp)
                .background(
                    brush = Brush.linearGradient(listOf(Mint, Teal)),
                    shape = CircleShape,
                )
                .clickable(onClick = onCenter),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                centerIcon,
                contentDescription = "Record",
                tint = Color(0xFF06143A),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun RowScope.NavIcon(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
}
