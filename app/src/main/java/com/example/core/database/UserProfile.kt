package com.example.core.database

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Creator",
    val email: String = "",
    val photoUrl: String = "",
    val preferredTheme: String = "obsidian",
    val preferredExportQuality: Int = 95,
    val brandName: String = "Monolith Studio",
    val brandPrimaryHex: String = "#090A0C",
    val brandAccentHex: String = "#C4C8CE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val safeName = displayName.trim().ifBlank { "Creator" }.take(100)
        return buildMap {
            put("userId", uid)
            put("displayName", safeName)
            if (email.isNotBlank()) put("email", email.take(150))
            if (photoUrl.isNotBlank()) put("photoUrl", photoUrl.take(500))
            put("preferredTheme", if (preferredTheme == "slate") "slate" else "obsidian")
            put("preferredExportQuality", preferredExportQuality.coerceIn(70, 100))
            if (brandName.isNotBlank()) put("brandName", brandName.take(80))
            if (brandPrimaryHex.isNotBlank()) put("brandPrimaryHex", brandPrimaryHex.take(16))
            if (brandAccentHex.isNotBlank()) put("brandAccentHex", brandAccentHex.take(16))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
    }

    fun toUpdateMap(): Map<String, Any> {
        val safeName = displayName.trim().ifBlank { "Creator" }.take(100)
        return buildMap {
            put("displayName", safeName)
            put("email", email.take(150))
            put("photoUrl", photoUrl.take(500))
            put("preferredTheme", if (preferredTheme == "slate") "slate" else "obsidian")
            put("preferredExportQuality", preferredExportQuality.coerceIn(70, 100))
            put("brandName", brandName.take(80))
            put("brandPrimaryHex", brandPrimaryHex.take(16))
            put("brandAccentHex", brandAccentHex.take(16))
            put("updatedAt", FieldValue.serverTimestamp())
        }
    }
}
