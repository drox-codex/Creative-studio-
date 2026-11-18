package com.example.core.database

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class AiHistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val prompt: String = "",
    val response: String = "",
    val appliedAction: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = buildMap {
        put("userId", uid)
        if (projectId.isNotBlank()) put("projectId", projectId.take(128))
        put("prompt", prompt.trim().ifBlank { "Action" }.take(2000))
        put("response", response.trim().ifBlank { "Completed" }.take(4000))
        put("appliedAction", appliedAction.take(120))
        put("createdAt", FieldValue.serverTimestamp())
    }
}
