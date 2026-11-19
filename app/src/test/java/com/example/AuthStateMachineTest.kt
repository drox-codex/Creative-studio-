package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.auth.AuthGateway
import com.example.core.auth.AuthState
import com.example.core.auth.AuthViewModel
import com.example.core.auth.AuthenticatedIdentity
import com.example.core.auth.GUEST_USER_ID
import com.example.core.auth.GuestWorkspaceStore
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
import org.junit.Assert.assertFalse
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

    private class FakeGuestWorkspaceStore : GuestWorkspaceStore {
        var active = false
        var clearedCount = 0
        var storedProfile = UserProfile(
            id = GUEST_USER_ID,
            userId = GUEST_USER_ID,
            displayName = "Guest Creator",
            brandName = "Local Studio"
        )

        override fun isGuestActive(): Boolean = active
        override fun setGuestActive(active: Boolean) {
            this.active = active
        }
        override fun loadGuestProfile(): UserProfile = storedProfile
        override fun saveGuestProfile(profile: UserProfile) {
            storedProfile = profile
        }
        override fun clearGuestWorkspace() {
            clearedCount++
            active = false
            storedProfile = UserProfile(
                id = GUEST_USER_ID,
                userId = GUEST_USER_ID,
                displayName = "Guest Creator",
                brandName = "Local Studio"
            )
        }
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
        var signOutCalls = 0
        var deleteAccountCalls = 0

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
            signOutCalls++
            currentUserFlow.value = null
        }

        override suspend fun deleteAccount(uid: String): Result<Unit> {
            deleteAccountCalls++
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
        assertFalse(authed1.isGuest)
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

    @Test
    fun guestModeLifecycleRestoreProfileSaveAndGoogleUpgrade() = runTest(testDispatcher) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val gateway = FakeAuthGateway()
        val guestStore = FakeGuestWorkspaceStore()
        val vm = AuthViewModel(gateway, runStartupCheck = true, guestStore = guestStore)

        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, vm.authState.value)

        // 1. Continue without account -> enters Guest Mode without touching Firebase
        vm.continueAsGuest()
        advanceUntilIdle()

        val guestState = vm.authState.value as AuthState.Authenticated
        assertEquals(GUEST_USER_ID, guestState.uid)
        assertTrue(guestState.isGuest)
        assertTrue(guestStore.isGuestActive())

        // 2. Save Brand Kit locally in Guest Mode -> saved to GuestWorkspaceStore, NOT Firebase
        var brandSaveSucceeded = false
        vm.updateBrandAndProfile(
            displayName = "Nadia Studio",
            brandName = "Monolith Lab",
            brandPrimaryHex = "#111317",
            brandAccentHex = "#E1E4E8",
            preferredExportQuality = 90
        ) { result -> brandSaveSucceeded = result.isSuccess }
        advanceUntilIdle()

        assertTrue(brandSaveSucceeded)
        assertEquals("Nadia Studio", guestStore.storedProfile.displayName)
        assertEquals("Monolith Lab", (vm.authState.value as AuthState.Authenticated).profile.brandName)

        // 3. Reopen app with active guest session -> restores Guest Mode automatically
        val vmReopen = AuthViewModel(gateway, runStartupCheck = true, guestStore = guestStore)
        advanceUntilIdle()
        val restoredGuest = vmReopen.authState.value as AuthState.Authenticated
        assertTrue(restoredGuest.isGuest)
        assertEquals("Nadia Studio", restoredGuest.profile.displayName)

        // 4. Cancelled Google Sign-In upgrade from Guest Mode -> stays cleanly in Guest Mode
        gateway.nextInteractiveResult = InteractiveSignInResult.Cancelled
        vmReopen.signInWithGoogle(ctx)
        advanceUntilIdle()
        val afterCancel = vmReopen.authState.value as AuthState.Authenticated
        assertTrue(afterCancel.isGuest)
        assertTrue(guestStore.isGuestActive())

        // 5. Successful Google Sign-In upgrade from Guest Mode -> transitions to Google account
        val googleUser = AuthenticatedIdentity("uid_nadia", "Nadia Vance", "nadia@studio.dev", "")
        gateway.nextInteractiveResult = InteractiveSignInResult.Success(googleUser)
        vmReopen.signInWithGoogle(ctx)
        advanceUntilIdle()
        val upgraded = vmReopen.authState.value as AuthState.Authenticated
        assertFalse(upgraded.isGuest)
        assertFalse(guestStore.isGuestActive())
        assertEquals("uid_nadia", upgraded.uid)
    }

    @Test
    fun guestSignOutAndClearWorkspaceDoNotCallFirebaseGateway() = runTest(testDispatcher) {
        val gateway = FakeAuthGateway()
        val guestStore = FakeGuestWorkspaceStore()
        val vm = AuthViewModel(gateway, runStartupCheck = true, guestStore = guestStore)
        advanceUntilIdle()

        vm.continueAsGuest()
        advanceUntilIdle()
        assertTrue((vm.authState.value as AuthState.Authenticated).isGuest)

        // Exit Guest
        vm.signOut()
        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, vm.authState.value)
        assertFalse(guestStore.isGuestActive())
        assertEquals(0, gateway.signOutCalls)

        // Enter again and Clear Data (deleteAccount in guest mode)
        vm.continueAsGuest()
        advanceUntilIdle()
        var deleteErr: String? = null
        vm.deleteAccount { deleteErr = it }
        advanceUntilIdle()
        assertEquals(null, deleteErr)
        assertEquals(AuthState.Unauthenticated, vm.authState.value)
        assertEquals(1, guestStore.clearedCount)
        assertEquals(0, gateway.deleteAccountCalls)
    }
}
