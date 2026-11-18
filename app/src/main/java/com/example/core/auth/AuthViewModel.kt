package com.example.core.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.database.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Coordinates the strict authentication state machine:
 * Initializing -> Unauthenticated / Authenticating -> Authenticated -> Unauthenticated / Error.
 * Never transitions to Authenticated until FirebaseAuth user ID AND Firestore UserProfile are confirmed.
 */
class AuthViewModel(
    private val gateway: AuthGateway,
    private val runStartupCheck: Boolean = true
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var profileJob: Job? = null
    private var activeSignInInProgress = false

    init {
        observeFirebaseSession()
        if (runStartupCheck) {
            restoreOrSilentSignIn()
        }
    }

    private fun observeFirebaseSession() {
        viewModelScope.launch {
            gateway.observeAuthIdentity().collect { identity ->
                if (identity == null) {
                    profileJob?.cancel()
                    profileJob = null
                    if (!activeSignInInProgress && _authState.value !is AuthState.Initializing && _authState.value !is AuthState.Error) {
                        _authState.value = AuthState.Unauthenticated
                    }
                } else if (!activeSignInInProgress) {
                    initializeAuthenticatedSession(identity)
                }
            }
        }
    }

    fun restoreOrSilentSignIn() {
        viewModelScope.launch {
            _authState.value = AuthState.Initializing
            val existing = gateway.currentIdentity()
            if (existing != null) {
                initializeAuthenticatedSession(existing)
                return@launch
            }
            val silent = gateway.attemptSilentSignIn()
            if (silent != null) {
                initializeAuthenticatedSession(silent)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (activeSignInInProgress) return
        viewModelScope.launch {
            activeSignInInProgress = true
            _authState.value = AuthState.Authenticating
            try {
                when (val result = gateway.signInWithGoogle(activityContext)) {
                    is InteractiveSignInResult.Success -> {
                        initializeAuthenticatedSession(result.identity)
                    }
                    is InteractiveSignInResult.Cancelled -> {
                        _authState.value = AuthState.Unauthenticated
                    }
                    is InteractiveSignInResult.Failure -> {
                        _authState.value = AuthState.Error(result.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.localizedMessage ?: "Authentication failed.")
            } finally {
                activeSignInInProgress = false
            }
        }
    }

    private suspend fun initializeAuthenticatedSession(identity: AuthenticatedIdentity) {
        if (identity.uid.isBlank()) {
            _authState.value = AuthState.Error("Authenticated user ID is invalid.")
            return
        }
        val profileResult = gateway.ensureUserProfile(identity)
        profileResult.fold(
            onSuccess = { profile ->
                _authState.value = AuthState.Authenticated(uid = identity.uid, profile = profile)
                startObservingProfile(identity.uid)
            },
            onFailure = { error ->
                _authState.value = AuthState.Error(
                    error.localizedMessage ?: "Failed to load user profile."
                )
            }
        )
    }

    private fun startObservingProfile(uid: String) {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            gateway.observeUserProfile(uid)
                .catch { /* Keep current Authenticated profile on transient listener hiccup */ }
                .collect { updated ->
                    val current = _authState.value
                    if (updated != null && current is AuthState.Authenticated && current.uid == uid) {
                        _authState.value = current.copy(profile = updated)
                    }
                }
        }
    }

    fun updateBrandAndProfile(
        displayName: String,
        brandName: String,
        brandPrimaryHex: String,
        brandAccentHex: String,
        preferredExportQuality: Int,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        val current = _authState.value as? AuthState.Authenticated ?: return
        viewModelScope.launch {
            val updated = current.profile.copy(
                displayName = displayName.trim().ifBlank { current.profile.displayName },
                brandName = brandName.trim().ifBlank { current.profile.brandName },
                brandPrimaryHex = brandPrimaryHex,
                brandAccentHex = brandAccentHex,
                preferredExportQuality = preferredExportQuality
            )
            val result = gateway.updateUserProfile(current.uid, updated)
            if (result.isSuccess) {
                _authState.value = current.copy(profile = updated)
            }
            onComplete(result)
        }
    }

    fun dismissError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun signOut() {
        viewModelScope.launch {
            profileJob?.cancel()
            profileJob = null
            gateway.signOut()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun deleteAccount(onError: (String) -> Unit) {
        val current = _authState.value as? AuthState.Authenticated ?: return
        viewModelScope.launch {
            profileJob?.cancel()
            profileJob = null
            val res = gateway.deleteAccount(current.uid)
            res.fold(
                onSuccess = { _authState.value = AuthState.Unauthenticated },
                onFailure = { err ->
                    onError(err.localizedMessage ?: "Unable to delete account. Please sign in again and retry.")
                }
            )
        }
    }

    companion object {
        fun provideFactory(appContext: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuthViewModel(FirebaseAuthGateway(appContext.applicationContext))
            }
        }
    }
}
