package com.aether

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the app actually launches on a real device.
 *
 * This is a small test doing a lot of work: it verifies Hilt's generated
 * component builds at runtime (a broken graph fails here, not at compile
 * time), that MainActivity composes without crashing, and — the first time it
 * runs — that the USB device pipeline itself works end to end.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndShowsItsName() {
        composeTestRule.onNodeWithText("Aether").assertExists()
        composeTestRule.onNodeWithText("Your second brain.").assertExists()
    }
}
