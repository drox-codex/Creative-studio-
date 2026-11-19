package com.example.core.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.core.common.MediaStorageHelper
import com.example.core.database.OperationType
import com.example.core.database.UserProfile
import com.example.core.database.handleFirestoreError
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AuthenticatedIdentity(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String
)

sealed interface InteractiveSignInResult {
    data class Success(val identity: AuthenticatedIdentity) : InteractiveSignInResult
    data object Cancelled : InteractiveSignInResult
    data class Failure(val message: String) : InteractiveSignInResult
}

/**
 * Abstraction for authentication & user profile initialization so AuthViewModel
 * can be deterministically tested across every auth state transition.
 */
interface AuthGateway {
    fun currentIdentity(): AuthenticatedIdentity?
    fun observeAuthIdentity(): Flow<AuthenticatedIdentity?>
    suspend fun attemptSilentSignIn(): AuthenticatedIdentity?
    suspend fun signInWithGoogle(activityContext: Context): InteractiveSignInResult
    suspend fun ensureUserProfile(identity: AuthenticatedIdentity): Result<UserProfile>
    fun observeUserProfile(uid: String): Flow<UserProfile?>
    suspend fun updateUserProfile(uid: String, profile: UserProfile): Result<Unit>
    suspend fun signOut()
    suspend fun deleteAccount(uid: String): Result<Unit>
}

class FirebaseAuthGateway(
    private val appContext: Context,
    private val authOverride: FirebaseAuth? = null,
    private val dbOverride: FirebaseFirestore? = null
) : AuthGateway {

    private val credentialManager by lazy { CredentialManager.create(appContext) }

    private fun ensureFirebaseReady(): Boolean = try {
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            true
        } else {
            FirebaseApp.initializeApp(appContext) != null
        }
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseApp unavailable: ${e.message}")
        false
    }

    private fun resolveAuth(): FirebaseAuth? = authOverride ?: try {
        if (ensureFirebaseReady()) Firebase.auth else null
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseAuth unavailable: ${e.message}")
        null
    }

    private fun resolveDb(): FirebaseFirestore? = dbOverride ?: try {
        if (ensureFirebaseReady()) {
            FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
        } else {
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseFirestore unavailable: ${e.message}")
        null
    }

    override fun currentIdentity(): AuthenticatedIdentity? = resolveAuth()?.currentUser?.toIdentity()

    override fun observeAuthIdentity(): Flow<AuthenticatedIdentity?> = callbackFlow {
        val auth = resolveAuth()
        if (auth == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toIdentity())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun attemptSilentSignIn(): AuthenticatedIdentity? {
        val auth = resolveAuth() ?: return null
        auth.currentUser?.toIdentity()?.let { return it }
        val clientId = resolveWebClientId() ?: return null
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val result = kotlinx.coroutines.withTimeoutOrNull(2_500L) {
                credentialManager.getCredential(appContext, request)
            } ?: return null
            val cred = result.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                authResult.user?.toIdentity()
            } else {
                null
            }
        } catch (_: NoCredentialException) {
            null
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent auto-sign-in skipped: ${e.message}")
            null
        }
    }

    override suspend fun signInWithGoogle(activityContext: Context): InteractiveSignInResult {
        val auth = resolveAuth()
        val db = resolveDb()
        if (auth == null || db == null) {
            return InteractiveSignInResult.Failure(
                "Firebase is not configured in this APK build (missing google-services.json). Add GOOGLE_SERVICES_JSON_BASE64 in GitHub Secrets and rebuild."
            )
        }
        val clientId = resolveWebClientId()
            ?: return InteractiveSignInResult.Failure(
                "Google Sign-In configuration missing: default_web_client_id not found"
            )
        val activity = activityContext as? Activity
            ?: return InteractiveSignInResult.Failure("Unable to launch Google Sign-In without an Activity.")

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                val verifiedUser = authResult.user ?: auth.currentUser
                val identity = verifiedUser?.toIdentity()
                if (identity == null || identity.uid.isBlank()) {
                    InteractiveSignInResult.Failure("Firebase authentication returned no valid user ID.")
                } else {
                    InteractiveSignInResult.Success(identity)
                }
            } else {
                InteractiveSignInResult.Failure("Unexpected Google credential response.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            InteractiveSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w("Auth", "No Google account available on device: ${e.message}", e)
            InteractiveSignInResult.Failure("No Google account found on this device. Please add a Google account in device settings.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            InteractiveSignInResult.Failure(e.localizedMessage ?: "Google Sign-In failed. Check network connection.")
        }
    }

    override suspend fun ensureUserProfile(identity: AuthenticatedIdentity): Result<UserProfile> {
        val path = "users/${identity.uid}"
        val db = resolveDb()
            ?: return Result.failure(IllegalStateException("Cloud database is not configured in this build."))
        return try {
            require(identity.uid.isNotBlank()) { "Authenticated UID cannot be blank." }
            val docRef = db.collection("users").document(identity.uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val existing = snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(
                    userId = identity.uid,
                    displayName = identity.displayName,
                    email = identity.email,
                    photoUrl = identity.photoUrl
                )
                Result.success(existing)
            } else {
                val newProfile = UserProfile(
                    userId = identity.uid,
                    displayName = identity.displayName.ifBlank { "Creator" },
                    email = identity.email,
                    photoUrl = identity.photoUrl
                )
                docRef.set(newProfile.toCreateMap(identity.uid)).await()
                val createdSnap = docRef.get().await()
                val loaded = createdSnap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: newProfile
                Result.success(loaded)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override fun observeUserProfile(uid: String): Flow<UserProfile?> {
        val db = resolveDb() ?: return flowOf(null)
        return db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
    }

    override suspend fun updateUserProfile(uid: String, profile: UserProfile): Result<Unit> {
        val path = "users/$uid"
        val auth = resolveAuth()
            ?: return Result.failure(IllegalStateException("Firebase Auth is not configured in this build."))
        val db = resolveDb()
            ?: return Result.failure(IllegalStateException("Cloud database is not configured in this build."))
        return try {
            val currentUid = auth.currentUser?.uid
                ?: error("User must be signed in before updating profile.")
            check(currentUid == uid) { "Signed-in user changed; refusing cross-user profile update." }
            db.collection("users").document(uid).update(profile.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                resolveAuth()?.signOut()
            }
        }
    }

    override suspend fun deleteAccount(uid: String): Result<Unit> {
        val path = "users/$uid"
        val auth = resolveAuth()
            ?: return Result.failure(IllegalStateException("Firebase Auth is not configured in this build."))
        val db = resolveDb()
            ?: return Result.failure(IllegalStateException("Cloud database is not configured in this build."))
        return try {
            val user = auth.currentUser ?: error("No authenticated user.")
            check(user.uid == uid) { "User mismatch during account deletion." }

            // Clean up user subcollections first while still authenticated
            val projectsSnap = db.collection("users").document(uid).collection("projects").get().await()
            for (doc in projectsSnap.documents) {
                doc.reference.delete().await()
            }
            val historySnap = db.collection("users").document(uid).collection("ai_history").get().await()
            for (doc in historySnap.documents) {
                doc.reference.delete().await()
            }
            db.collection("users").document(uid).delete().await()
            MediaStorageHelper.clearUserMedia(appContext, uid)

            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (_: Exception) {
                }
                user.delete().await()
                auth.signOut()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    @android.annotation.SuppressLint("DiscouragedApi")
    private fun resolveWebClientId(): String? = try {
        val resId = appContext.resources.getIdentifier(
            "default_web_client_id",
            "string",
            appContext.packageName
        )
        if (resId != 0) appContext.getString(resId).takeIf { it.isNotBlank() } else null
    } catch (_: Exception) {
        null
    }

    private fun FirebaseUser.toIdentity(): AuthenticatedIdentity = AuthenticatedIdentity(
        uid = uid,
        displayName = displayName.orEmpty().ifBlank { email?.substringBefore('@') ?: "Creator" },
        email = email.orEmpty(),
        photoUrl = photoUrl?.toString().orEmpty()
    )

    private companion object {
        const val TAG = "FirebaseAuthGateway"
    }
}
