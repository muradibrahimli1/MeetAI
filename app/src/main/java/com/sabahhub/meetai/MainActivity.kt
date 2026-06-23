package com.sabahhub.meetai

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sabahhub.meetai.ui.MeetAiViewModel
import com.sabahhub.meetai.ui.screens.AppShell
import com.sabahhub.meetai.ui.screens.DetailScreen
import com.sabahhub.meetai.ui.theme.AppBackground
import com.sabahhub.meetai.ui.theme.MeetAiTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MeetAiViewModel by viewModels {
        MeetAiViewModel.factory(application as MeetAiApp)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* result handled by re-check */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()

        setContent {
            MeetAiTheme {
                AppBackground {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "shell") {
                        composable("shell") {
                            AppShell(
                                viewModel = viewModel,
                                onOpenRecording = { id -> navController.navigate("detail/$id") },
                            )
                        }
                        composable("detail/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id").orEmpty()
                            DetailScreen(
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

    private fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }
}
