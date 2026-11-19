package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.auth.AuthScreen
import com.example.core.auth.AuthState
import com.example.core.auth.GUEST_USER_ID
import com.example.core.database.AiHistoryEntry
import com.example.core.database.CreativeProject
import com.example.core.database.LayerType
import com.example.core.database.PhotoAdjustments
import com.example.core.database.ProjectType
import com.example.core.database.StudioLayer
import com.example.core.database.TimelineClip
import com.example.core.database.TransitionKind
import com.example.core.designsystem.CreativeStudioTheme
import com.example.feature.projects.LocalProjectsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun authScreenDisplaysGoogleSignInAndGuestOptionAndTriggersCallbacks() {
        var googleClicked = false
        var guestClicked = false
        composeTestRule.setContent {
            CreativeStudioTheme {
                AuthScreen(
                    authState = AuthState.Unauthenticated,
                    onGoogleSignIn = { googleClicked = true },
                    onContinueWithoutAccount = { guestClicked = true },
                    onDismissError = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Creative Studio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("guest_sign_in_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performClick()
        composeTestRule.onNodeWithTag("guest_sign_in_button").performClick()
        assertTrue(googleClicked)
        assertTrue(guestClicked)
    }

    @Test
    fun authScreenShowsLoadingIndicatorAndDisablesButtonsDuringSignIn() {
        composeTestRule.setContent {
            CreativeStudioTheme {
                AuthScreen(
                    authState = AuthState.Authenticating,
                    onGoogleSignIn = {},
                    onContinueWithoutAccount = {},
                    onDismissError = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("guest_sign_in_button").assertIsNotEnabled()
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

    @Test
    fun localProjectsRepositoryPersistsCrudLayersClipsAndAiHistoryAcrossInstances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo1 = LocalProjectsRepository(context)

        val initialProject = CreativeProject(
            name = "Guest Lookbook",
            projectType = ProjectType.DESIGN,
            canvasWidth = 1080,
            canvasHeight = 1350,
            backgroundColorHex = "#111317",
            adjustments = PhotoAdjustments(brightness = 15f, contrast = 1.25f, filterPreset = "Noir"),
            layers = listOf(
                StudioLayer(
                    layerId = "layer_1",
                    name = "Title",
                    type = LayerType.TEXT,
                    text = "MONOLITH",
                    colorHex = "#F5F5F5"
                )
            ),
            clips = listOf(
                TimelineClip(
                    clipId = "clip_1",
                    trackType = "VIDEO",
                    title = "Intro",
                    sourceDurationMs = 8000L,
                    trimEndMs = 6000L,
                    speed = 1.5f,
                    transitionAfter = TransitionKind.CROSSFADE
                )
            )
        )

        val createdId = repo1.createProject(GUEST_USER_ID, initialProject).getOrThrow()
        assertTrue(createdId.startsWith("local_"))

        // Update & rename
        val loaded = repo1.getProject(GUEST_USER_ID, createdId).getOrThrow()
        assertNotNull(loaded)
        repo1.updateProject(GUEST_USER_ID, loaded!!.copy(backgroundColorHex = "#090A0C")).getOrThrow()
        repo1.renameProject(GUEST_USER_ID, createdId, "Guest Lookbook V2").getOrThrow()

        // Duplicate
        val copyId = repo1.duplicateProject(GUEST_USER_ID, repo1.getProject(GUEST_USER_ID, createdId).getOrThrow()!!).getOrThrow()

        // AI History
        repo1.addAiHistory(
            GUEST_USER_ID,
            AiHistoryEntry(
                projectId = createdId,
                prompt = "Apply Noir mood",
                response = "Applied Noir preset",
                appliedAction = "PHOTO_ADJUST"
            )
        ).getOrThrow()

        // Reload via fresh repository instance (simulating app restart)
        val repo2 = LocalProjectsRepository(context)
        val persistedProjects = repo2.observeProjects(GUEST_USER_ID).first()
        assertEquals(2, persistedProjects.size)
        val mainProj = repo2.getProject(GUEST_USER_ID, createdId).getOrThrow()
        assertNotNull(mainProj)
        assertEquals("Guest Lookbook V2", mainProj!!.name)
        assertEquals("#090A0C", mainProj.backgroundColorHex)
        assertEquals("MONOLITH", mainProj.layers.first().text)
        assertEquals(TransitionKind.CROSSFADE, mainProj.clips.first().transitionAfter)

        val history = repo2.observeAiHistory(GUEST_USER_ID).first()
        assertEquals(1, history.size)
        assertEquals("Apply Noir mood", history.first().prompt)

        // Delete copy & verify
        repo2.deleteProject(GUEST_USER_ID, copyId).getOrThrow()
        assertNull(repo2.getProject(GUEST_USER_ID, copyId).getOrThrow())
        assertEquals(1, repo2.observeProjects(GUEST_USER_ID).first().size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityStartupRobolectricTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityLaunchesAndCanEnterGuestStudioWithoutCrash() {
        activityRule.waitForIdle()
        activityRule.onNodeWithText("Creative Studio").assertIsDisplayed()
        activityRule.onNodeWithText("Welcome Back").assertIsDisplayed()
        activityRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        activityRule.onNodeWithTag("guest_sign_in_button").assertIsDisplayed()

        // Enter Guest Mode directly on MainActivity
        activityRule.onNodeWithTag("guest_sign_in_button").performClick()
        activityRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                activityRule.onNodeWithTag("home_greeting_text").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        activityRule.onNodeWithTag("home_greeting_text").assertIsDisplayed()
        activityRule.onNodeWithText("Hello, Guest").assertIsDisplayed()
        activityRule.onNodeWithTag("nav_home_tab").assertIsDisplayed()
        activityRule.onNodeWithTag("nav_projects_tab").assertIsDisplayed()
        activityRule.onNodeWithTag("nav_profile_tab").assertIsDisplayed()
    }
}
