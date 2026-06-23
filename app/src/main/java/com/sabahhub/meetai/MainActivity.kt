package com.sabahhub.meetai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabahhub.meetai.data.ThemeMode
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import com.sabahhub.meetai.ui.MeetAiViewModel
import com.sabahhub.meetai.ui.screens.AppShell
import com.sabahhub.meetai.ui.screens.ChatScreen
import com.sabahhub.meetai.ui.screens.DetailScreen
import com.sabahhub.meetai.ui.screens.LockScreen
import com.sabahhub.meetai.ui.theme.AppBackground
import com.sabahhub.meetai.ui.theme.MeetAiTheme

class MainActivity : FragmentActivity() {

    private val viewModel: MeetAiViewModel by viewModels {
        MeetAiViewModel.factory(application as MeetAiApp)
    }

    // True only for a fresh (cold) launch, so we don't re-record on rotation
    // or when returning from the background.
    private var autoStartPending = false
    // Set when launched from the Quick Settings tile — always starts recording.
    private var forceStartPending = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.RECORD_AUDIO] == true) maybeAutoStart()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        forceStartPending = intent?.getBooleanExtra(EXTRA_START_RECORDING, false) == true
        autoStartPending = savedInstanceState == null && !forceStartPending
        ensurePermissionsThenMaybeAutoStart()

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val appLock by viewModel.appLock.collectAsStateWithLifecycle()
            var unlocked by rememberSaveable { mutableStateOf(false) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, appLock) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP && appLock) unlocked = false
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            val locked = appLock && !unlocked
            LaunchedEffect(locked) {
                if (locked) authenticate { ok -> if (ok) unlocked = true }
            }

            MeetAiTheme(darkTheme = darkTheme) {
                val hazeState = remember { HazeState() }
                AppBackground(hazeState = hazeState, darkTheme = darkTheme) {
                    if (locked) {
                        LockScreen(onUnlock = { authenticate { ok -> if (ok) unlocked = true } })
                        return@AppBackground
                    }
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "shell") {
                        composable("shell") {
                            AppShell(
                                viewModel = viewModel,
                                hazeState = hazeState,
                                onOpenRecording = { id -> navController.navigate("detail/$id") },
                            )
                        }
                        composable("detail/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id").orEmpty()
                            DetailScreen(
                                recordingId = id,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onAsk = { recId -> navController.navigate("chat/$recId") },
                            )
                        }
                        composable("chat/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id").orEmpty()
                            ChatScreen(
                                recordingId = id,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun ensurePermissionsThenMaybeAutoStart() {
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (micGranted) {
            maybeAutoStart()
            // Still request notifications so the recording notification can show.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        } else {
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            permissionLauncher.launch(perms)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_RECORDING, false)) {
            forceStartPending = true
            ensurePermissionsThenMaybeAutoStart()
        }
    }

    /** Fires the auto-start at most once per fresh launch. */
    private fun maybeAutoStart() {
        if (forceStartPending) {
            forceStartPending = false
            viewModel.startRecording()
            return
        }
        if (!autoStartPending) return
        autoStartPending = false
        viewModel.maybeAutoStartOnLaunch()
    }

    /** Prompts for biometric/credential auth. Falls back to unlocked if none is set up. */
    private fun authenticate(onResult: (Boolean) -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (BiometricManager.from(this).canAuthenticate(authenticators)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            onResult(true) // No biometric enrolled — don't lock the user out.
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MeetAI")
            .setSubtitle("Authenticate to continue")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        const val EXTRA_START_RECORDING = "start_recording"
    }
}
