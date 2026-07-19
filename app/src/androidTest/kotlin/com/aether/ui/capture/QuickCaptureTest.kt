package com.aether.ui.capture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tests the capture sheet in isolation, without Hilt or a database.
 *
 * The sheet takes a callback rather than a ViewModel, so what is verified here
 * is the interaction contract: what the user types is what gets captured.
 */
class QuickCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capturingTextInvokesCallbackWithTypedContent() {
        val captured = mutableListOf<String>()

        composeTestRule.setContent {
            QuickCaptureSheet(onDismiss = {}, onCapture = { captured += it })
        }

        composeTestRule.onNodeWithTag(CaptureTestTags.TEXT_FIELD)
            .performTextInput("Renew the passport")
        composeTestRule.onNodeWithTag(CaptureTestTags.SAVE_BUTTON).performClick()

        assertEquals(listOf("Renew the passport"), captured)
    }

    @Test
    fun saveIsDisabledUntilSomethingIsTyped() {
        composeTestRule.setContent {
            QuickCaptureSheet(onDismiss = {}, onCapture = {})
        }

        // Prevents the empty-capture mis-tap at the UI layer, so the user is
        // never shown an error they cannot act on.
        composeTestRule.onNodeWithTag(CaptureTestTags.SAVE_BUTTON).assertIsNotEnabled()

        composeTestRule.onNodeWithTag(CaptureTestTags.TEXT_FIELD).performTextInput("Anything")

        composeTestRule.onNodeWithTag(CaptureTestTags.SAVE_BUTTON).assertIsEnabled()
    }

    @Test
    fun capturingDismissesTheSheet() {
        var dismissed = false

        composeTestRule.setContent {
            QuickCaptureSheet(onDismiss = { dismissed = true }, onCapture = {})
        }

        composeTestRule.onNodeWithTag(CaptureTestTags.TEXT_FIELD).performTextInput("Done")
        composeTestRule.onNodeWithTag(CaptureTestTags.SAVE_BUTTON).performClick()

        assertEquals(true, dismissed)
    }

    @Test
    fun promptIsShownSoTheFieldIsNeverAmbiguous() {
        composeTestRule.setContent {
            QuickCaptureSheet(onDismiss = {}, onCapture = {})
        }

        composeTestRule.onNodeWithText("What's on your mind?").assertIsDisplayed()
    }
}
