package com.example.feature.projects

import android.content.Context
import com.example.R
import com.example.core.database.AiHistoryEntry
import com.example.core.database.CreativeProject
import com.example.core.database.OperationType
import com.example.core.database.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface ProjectsRepository {
    fun observeProjects(userId: String): Flow<List<CreativeProject>>
    fun observeProject(userId: String, projectId: String): Flow<CreativeProject?>
    suspend fun getProject(userId: String, projectId: String): Result<CreativeProject?>
    suspend fun createProject(userId: String, project: CreativeProject): Result<String>
    suspend fun updateProject(userId: String, project: CreativeProject): Result<Unit>
    suspend fun renameProject(userId: String, projectId: String, newName: String): Result<Unit>
    suspend fun duplicateProject(userId: String, source: CreativeProject): Result<String>
    suspend fun deleteProject(userId: String, projectId: String): Result<Unit>
    fun observeAiHistory(userId: String): Flow<List<AiHistoryEntry>>
    suspend fun addAiHistory(userId: String, entry: AiHistoryEntry): Result<Unit>
}

class FirestoreProjectsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : ProjectsRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun verifySessionOwner(userId: String): String {
        val currentUid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing projects.")
        require(userId.isNotBlank() && currentUid == userId) {
            "Signed-in user mismatch; cross-account operation refused."
        }
        return currentUid
    }

    override fun observeProjects(userId: String): Flow<List<CreativeProject>> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { CreativeProject.fromSnapshot(it) }
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
        )
    }

    override fun observeProject(userId: String, projectId: String): Flow<CreativeProject?> = flow {
        if (userId.isBlank() || projectId.isBlank() || auth.currentUser?.uid != userId) {
            emit(null)
            return@flow
        }
        emitAll(
            db.collection("users").document(userId).collection("projects").document(projectId)
                .snapshots()
                .map { CreativeProject.fromSnapshot(it) }
        )
    }

    override suspend fun getProject(userId: String, projectId: String): Result<CreativeProject?> {
        val path = "users/$userId/projects/$projectId"
        return try {
            verifySessionOwner(userId)
            val snap = db.collection("users").document(userId)
                .collection("projects").document(projectId)
                .get().await()
            Result.success(CreativeProject.fromSnapshot(snap))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    override suspend fun createProject(userId: String, project: CreativeProject): Result<String> {
        var path = "users/$userId/projects"
        return try {
            val uid = verifySessionOwner(userId)
            val docRef = db.collection("users").document(uid).collection("projects").document()
            path = docRef.path
            docRef.set(project.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateProject(userId: String, project: CreativeProject): Result<Unit> {
        val path = "users/$userId/projects/${project.id}"
        return try {
            val uid = verifySessionOwner(userId)
            require(project.id.isNotBlank()) { "Project ID must not be blank for update." }
            val docRef = db.collection("users").document(uid)
                .collection("projects").document(project.id)
            docRef.update(project.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun renameProject(userId: String, projectId: String, newName: String): Result<Unit> {
        val path = "users/$userId/projects/$projectId"
        return try {
            val uid = verifySessionOwner(userId)
            require(projectId.isNotBlank()) { "Project ID must not be blank." }
            val clean = newName.trim().ifBlank { "Untitled Project" }.take(120)
            db.collection("users").document(uid)
                .collection("projects").document(projectId)
                .update(
                    mapOf(
                        "name" to clean,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun duplicateProject(userId: String, source: CreativeProject): Result<String> {
        val copyName = "${source.name.take(110)} (Copy)"
        val copy = source.copy(id = "", name = copyName, createdAt = null, updatedAt = null)
        return createProject(userId, copy)
    }

    override suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
        val path = "users/$userId/projects/$projectId"
        return try {
            val uid = verifySessionOwner(userId)
            require(projectId.isNotBlank()) { "Project ID must not be blank." }
            db.collection("users").document(uid)
                .collection("projects").document(projectId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override fun observeAiHistory(userId: String): Flow<List<AiHistoryEntry>> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("users").document(userId).collection("ai_history")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiHistoryEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
        )
    }

    override suspend fun addAiHistory(userId: String, entry: AiHistoryEntry): Result<Unit> {
        var path = "users/$userId/ai_history"
        return try {
            val uid = verifySessionOwner(userId)
            val docRef = db.collection("users").document(uid)
                .collection("ai_history").document()
            path = docRef.path
            docRef.set(entry.toCreateMap(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
