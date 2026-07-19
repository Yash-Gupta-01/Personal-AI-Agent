package com.aether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aether.ui.dashboard.DashboardRoute
import com.aether.ui.theme.AetherTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity host for Aether's Compose UI.
 *
 * The home screen is the dashboard — what Aether remembers — not a chat
 * screen. Insets are handled by the dashboard's own Scaffold, which
 * targetSdk 36 requires now that edge-to-edge is enforced.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AetherTheme {
                DashboardRoute()
            }
        }
    }
}
