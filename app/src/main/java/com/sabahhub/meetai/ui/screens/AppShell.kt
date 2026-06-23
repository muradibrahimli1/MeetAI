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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabahhub.meetai.ui.MeetAiViewModel
import com.sabahhub.meetai.ui.components.glass
import com.sabahhub.meetai.ui.theme.Mint
import com.sabahhub.meetai.ui.theme.Teal

private enum class Tab { Recorder, Library, Settings }

@Composable
fun AppShell(
    viewModel: MeetAiViewModel,
    onOpenRecording: (String) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.record.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(Tab.Recorder) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Tab content.
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.Recorder -> RecorderScreen(
                    state = state,
                    onDiscard = viewModel::discardRecording,
                    onSave = viewModel::saveRecording,
                )
                Tab.Library -> LibraryScreen(
                    recordings = recordings,
                    onOpen = onOpenRecording,
                    onDelete = viewModel::deleteRecording,
                )
                Tab.Settings -> SettingsScreen(
                    user = user,
                    authAvailable = viewModel.authAvailable,
                    onSignIn = { viewModel.signIn(context) },
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
    selected: Tab,
    recording: Boolean,
    paused: Boolean,
    onSelectLibrary: () -> Unit,
    onSelectSettings: () -> Unit,
    onCenter: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Glass bar.
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .glass(RoundedCornerShape(32.dp), fillAlpha = 0.12f)
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
                .offset(y = (-18).dp)
                .size(68.dp)
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
