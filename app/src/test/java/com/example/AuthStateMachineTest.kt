package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.auth.AuthGateway
import com.example.core.auth.AuthState
import com.example.core.auth.AuthViewModel
import com.example.core.auth.AuthenticatedIdentity
import com.example.core.auth.InteractiveSignInResult
import com.example.core.database.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthStateMachineTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthGateway(
        initialUser: AuthenticatedIdentity? = null
    ) : AuthGateway {
        val currentUserFlow = MutableStateFlow(initialUser)
        val profiles = mutableMapOf<String, MutableStateFlow<UserProfile?>>()
        var nextInteractiveResult: InteractiveSignInResult = InteractiveSignInResult.Cancelled
        var nextSilentResult: AuthenticatedIdentity? = initialUser
        var failProfileLoad = false
        var profileInitializedBeforeAuthReturn = false

        override fun currentIdentity(): AuthenticatedIdentity? = currentUserFlow.value

        override fun observeAuthIdentity(): Flow<AuthenticatedIdentity?> = currentUserFlow

        override suspend fun attemptSilentSignIn(): AuthenticatedIdentity? {
            if (nextSilentResult != null) currentUserFlow.value = nextSilentResult
            return nextSilentResult
        }

        override suspend fun signInWithGoogle(activityContext: Context): InteractiveSignInResult {
            if (nextInteractiveResult is InteractiveSignInResult.Success) {
                currentUserFlow.value = (nextInteractiveResult as InteractiveSignInResult.Success).identity
            }
            return nextInteractiveResult
        }

        override suspend fun ensureUserProfile(identity: AuthenticatedIdentity): Result<UserProfile> {
            if (failProfileLoad) {
                return Result.failure(IllegalStateException("Simulated Firestore outage"))
            }
            val flow = profiles.getOrPut(identity.uid) {
                MutableStateFlow(
                    UserProfile(
                        id = identity.uid,
                        userId = identity.uid,
                        displayName = identity.displayName,
                        email = identity.email
                    )
                )
            }
            profileInitializedBeforeAuthReturn = true
            return Result.success(flow.value!!)
        }

        override fun observeUserProfile(uid: String): Flow<UserProfile?> =
            profiles.getOrPut(uid) { MutableStateFlow(null) }

        override suspend fun updateUserProfile(uid: String, profile: UserProfile): Result<Unit> {
            profiles.getOrPut(uid) { MutableStateFlow(null) }.value = profile
            return Result.success(Unit)
        }

        override suspend fun signOut() {
            currentUserFlow.value = null
        }

        override suspend fun deleteAccount(uid: String): Result<Unit> {
            profiles.remove(uid)
            currentUserFlow.value = null
            return Result.success(Unit)
        }
    }

    @Test
    fun fullEndToEndGoogleAuthSignOutAndAccountSwitchIsolation() = runTest(testDispatcher) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val gateway = FakeAuthGateway()
        val vm = AuthViewModel(gateway, runStartupCheck = true)

        // 1. Cold start -> unauthenticated
        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, vm.authState.value)

        // 2. Interactive Google Sign-In Account 1 -> profile loaded BEFORE Authenticated
        val user1 = AuthenticatedIdentity("uid_alice", "Alice Vance", "alice@studio.dev", "")
        gateway.nextInteractiveResult = InteractiveSignInResult.Success(user1)
        vm.signInWithGoogle(ctx)
        advanceUntilIdle()

        val authed1 = vm.authState.value
        assertTrue(authed1 is AuthState.Authenticated)
        assertTrue(gateway.profileInitializedBeforeAuthReturn)
        assertEquals("uid_alice", (authed1 as AuthState.Authenticated).uid)
        assertEquals("Alice Vance", authed1.profile.displayName)

        // 3. Reopen simulation with persisted Firebase session -> restores Authenticated directly
        val vmReopen = AuthViewModel(gateway, runStartupCheck = true)
        advanceUntilIdle()
        val restored = vmReopen.authState.value
        assertTrue(restored is AuthState.Authenticated)
        assertEquals("uid_alice", (restored as AuthState.Authenticated).uid)

        // 4. Sign out -> returns cleanly to Unauthenticated
        vm.signOut()
        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, vm.authState.value)

        // 5. Sign in Account 2 -> isolated UID & profile (no stale Account 1 profile)
        val user2 = AuthenticatedIdentity("uid_bob", "Bob Mercer", "bob@studio.dev", "")
        gateway.nextInteractiveResult = InteractiveSignInResult.Success(user2)
        vm.signInWithGoogle(ctx)
        advanceUntilIdle()

        val authed2 = vm.authState.value as AuthState.Authenticated
        assertEquals("uid_bob", authed2.uid)
        assertEquals("Bob Mercer", authed2.profile.displayName)
    }

    @Test
    fun cancelledSignInResetsToUnauthenticatedWithoutError() = runTest(testDispatcher) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val gateway = FakeAuthGateway()
        gateway.nextInteractiveResult = InteractiveSignInResult.Cancelled
        val vm = AuthViewModel(gateway, runStartupCheck = true)
        advanceUntilIdle()

        vm.signInWithGoogle(ctx)
        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, vm.authState.value)
    }

    @Test
    fun profileFailureBlocksAuthenticatedStateTransition() = runTest(testDispatcher) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val gateway = FakeAuthGateway()
        val user1 = AuthenticatedIdentity("uid_alice", "Alice Vance", "alice@studio.dev", "")
        gateway.nextInteractiveResult = InteractiveSignInResult.Success(user1)
        gateway.failProfileLoad = true
        val vm = AuthViewModel(gateway, runStartupCheck = true)
        advanceUntilIdle()

        vm.signInWithGoogle(ctx)
        advanceUntilIdle()

        val state = vm.authState.value
        assertTrue("Expected Error state when profile creation fails, got $state", state is AuthState.Error)
    }
}
