package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.auth.AuthScreen
import com.example.core.auth.AuthState
import com.example.core.designsystem.CreativeStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appNameMatchesCreativeStudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Creative Studio", appName)
    }

    @Test
    fun authScreenDisplaysGoogleSignInAndTriggersCallback() {
        var clicked = false
        composeTestRule.setContent {
            CreativeStudioTheme {
                AuthScreen(
                    authState = AuthState.Unauthenticated,
                    onGoogleSignIn = { clicked = true },
                    onDismissError = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Creative Studio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performClick()
        assertTrue(clicked)
    }

    @Test
    fun authScreenShowsLoadingIndicatorAndDisablesButtonDuringSignIn() {
        composeTestRule.setContent {
            CreativeStudioTheme {
                AuthScreen(
                    authState = AuthState.Authenticating,
                    onGoogleSignIn = {},
                    onDismissError = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsNotEnabled()
    }

    @Test
    fun authScreenDisplaysErrorBannerAndAllowsDismissal() {
        var dismissed = false
        composeTestRule.setContent {
            CreativeStudioTheme {
                AuthScreen(
                    authState = AuthState.Error("Profile synchronization failed"),
                    onGoogleSignIn = {},
                    onDismissError = { dismissed = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Profile synchronization failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dismiss").performClick()
        assertTrue(dismissed)
    }
}
