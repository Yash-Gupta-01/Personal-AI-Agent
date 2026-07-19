package com.aether

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aether.ui.dashboard.DashboardTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the app launches on a real device with its real dependency graph.
 *
 * A small test doing a lot of work: it verifies Hilt's component builds at
 * runtime (a broken graph fails here, not at compile time), that Room is
 * constructible, and that the dashboard composes without crashing.
 *
 * It asserts on the capture button rather than on any particular memory,
 * because the database holds whatever the device happens to have captured.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesShowingTheDashboard() {
        composeTestRule.onNodeWithText("Aether").assertIsDisplayed()
    }

    @Test
    fun captureActionIsAvailableOnLaunch() {
        // Capture is the primary action; if it is unreachable the app has no
        // purpose, so this is the one interaction worth asserting on launch.
        composeTestRule.onNodeWithTag(DashboardTestTags.CAPTURE_FAB).assertIsDisplayed()
    }
}
