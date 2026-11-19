package com.example.core.auth

import android.content.Context
import com.example.core.common.MediaStorageHelper
import com.example.core.database.UserProfile
import org.json.JSONObject
import java.io.File

const val GUEST_USER_ID = "local_guest_studio"

interface GuestWorkspaceStore {
    fun isGuestActive(): Boolean
    fun setGuestActive(active: Boolean)
    fun loadGuestProfile(): UserProfile
    fun saveGuestProfile(profile: UserProfile)
    fun clearGuestWorkspace()
}

class FileGuestWorkspaceStore(private val appContext: Context) : GuestWorkspaceStore {

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun localDir(): File {
        val dir = File(appContext.filesDir, "local_studio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun profileFile(): File = File(localDir(), "guest_profile.json")

    override fun isGuestActive(): Boolean = prefs.getBoolean(KEY_GUEST_ACTIVE, false)

    override fun setGuestActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_GUEST_ACTIVE, active).apply()
    }

    override fun loadGuestProfile(): UserProfile {
        val file = profileFile()
        if (!file.exists()) return defaultGuestProfile()
        return try {
            val json = JSONObject(file.readText())
            UserProfile(
                id = GUEST_USER_ID,
                userId = GUEST_USER_ID,
                displayName = json.optString("displayName").ifBlank { "Guest Creator" },
                email = "",
                photoUrl = "",
                preferredTheme = json.optString("preferredTheme").ifBlank { "obsidian" },
                preferredExportQuality = json.optInt("preferredExportQuality", 95).coerceIn(70, 100),
                brandName = json.optString("brandName").ifBlank { "Local Studio" },
                brandPrimaryHex = json.optString("brandPrimaryHex").ifBlank { "#090A0C" },
                brandAccentHex = json.optString("brandAccentHex").ifBlank { "#C4C8CE" }
            )
        } catch (_: Exception) {
            defaultGuestProfile()
        }
    }

    override fun saveGuestProfile(profile: UserProfile) {
        try {
            val json = JSONObject().apply {
                put("displayName", profile.displayName.trim().ifBlank { "Guest Creator" }.take(100))
                put("preferredTheme", if (profile.preferredTheme == "slate") "slate" else "obsidian")
                put("preferredExportQuality", profile.preferredExportQuality.coerceIn(70, 100))
                put("brandName", profile.brandName.trim().ifBlank { "Local Studio" }.take(80))
                put("brandPrimaryHex", profile.brandPrimaryHex.ifBlank { "#090A0C" }.take(16))
                put("brandAccentHex", profile.brandAccentHex.ifBlank { "#C4C8CE" }.take(16))
            }
            profileFile().writeText(json.toString())
        } catch (_: Exception) {
        }
    }

    override fun clearGuestWorkspace() {
        setGuestActive(false)
        try {
            val dir = File(appContext.filesDir, "local_studio")
            if (dir.exists()) dir.deleteRecursively()
        } catch (_: Exception) {
        }
        MediaStorageHelper.clearUserMedia(appContext, GUEST_USER_ID)
    }

    private fun defaultGuestProfile(): UserProfile = UserProfile(
        id = GUEST_USER_ID,
        userId = GUEST_USER_ID,
        displayName = "Guest Creator",
        email = "",
        photoUrl = "",
        brandName = "Local Studio"
    )

    private companion object {
        const val PREFS_NAME = "creative_studio_guest_prefs"
        const val KEY_GUEST_ACTIVE = "guest_session_active"
    }
}
