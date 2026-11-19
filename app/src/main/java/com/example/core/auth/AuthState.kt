package com.example.core.auth

import com.example.core.database.UserProfile

sealed interface AuthState {
    data object Initializing : AuthState
    data object Unauthenticated : AuthState
    data object Authenticating : AuthState
    data class Authenticated(
        val uid: String,
        val profile: UserProfile,
        val isGuest: Boolean = false
    ) : AuthState
    data class Error(val message: String) : AuthState
}
