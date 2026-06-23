package com.robot.g20demo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.robot.g20demo.ui.RobotControllerScreen
import com.robot.g20demo.ui.theme.G20RobotDemoTheme

/**
 * Main entry point for the application.
 * Manages the top-level UI and delegates logic to [MainViewModel].
 */
class MainActivity : ComponentActivity() {

    // ViewModel to handle RC logic and state
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure the screen stays on during robot operation
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Setup ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Initialize Skydroid SDK and start polling
        viewModel.initialize(this)

        // Edge-to-edge support for modern Android look
        enableEdgeToEdge()
        
        // Hide system bars for true full screen immersive mode
        hideSystemUI()

        setContent {
            G20RobotDemoTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    // We ignore innerPadding to ensure we fill the whole screen
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        // Main Controller Screen
                        RobotControllerScreen(
                            state = viewModel.g20State,
                            batteryState = viewModel.batteryState,
                            networkStatus = viewModel.networkStatus,
                            onSleepToggle = { viewModel.toggleSleepMode() }
                        )
                    }
                }
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }
}
