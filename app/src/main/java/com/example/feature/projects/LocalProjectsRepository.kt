package com.example.feature.projects

import android.content.Context
import com.example.core.database.AiHistoryEntry
import com.example.core.database.CreativeProject
import com.example.core.database.PhotoAdjustments
import com.example.core.database.ProjectType
import com.example.core.database.StudioLayer
import com.example.core.database.TimelineClip
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Fully persistent on-device implementation of [ProjectsRepository] for Guest / Without-Account mode.
 * Stores projects and AI history locally as JSON inside app filesDir without touching Firestore.
 */
class LocalProjectsRepository(private val appContext: Context) : ProjectsRepository {

    private val mutex = Mutex()
    private val projectsFlow = MutableStateFlow<List<CreativeProject>>(emptyList())
    private val aiHistoryFlow = MutableStateFlow<List<AiHistoryEntry>>(emptyList())
    private var initializedUserId: String? = null

    private fun workspaceFile(userId: String): File {
        val dir = File(appContext.filesDir, "local_studio")
        if (!dir.exists()) dir.mkdirs()
        val safeId = userId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "guest" }
        return File(dir, "${safeId}_workspace.json")
    }

    private suspend fun ensureLoaded(userId: String) {
        if (initializedUserId == userId) return
        mutex.withLock {
            if (initializedUserId == userId) return@withLock
            withContext(Dispatchers.IO) {
                val file = workspaceFile(userId)
                if (!file.exists()) {
                    projectsFlow.value = emptyList()
                    aiHistoryFlow.value = emptyList()
                    initializedUserId = userId
                    return@withContext
                }
                try {
                    val root = JSONObject(file.readText())
                    val projectsArr = root.optJSONArray("projects") ?: JSONArray()
                    val loadedProjects = buildList {
                        for (i in 0 until projectsArr.length()) {
                            val item = projectsArr.optJSONObject(i) ?: continue
                            add(projectFromJson(item))
                        }
                    }.sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }

                    val historyArr = root.optJSONArray("aiHistory") ?: JSONArray()
                    val loadedHistory = buildList {
                        for (i in 0 until historyArr.length()) {
                            val item = historyArr.optJSONObject(i) ?: continue
                            add(historyFromJson(item))
                        }
                    }.sortedByDescending { it.createdAt?.seconds ?: 0L }

                    projectsFlow.value = loadedProjects
                    aiHistoryFlow.value = loadedHistory
                } catch (_: Exception) {
                    projectsFlow.value = emptyList()
                    aiHistoryFlow.value = emptyList()
                }
                initializedUserId = userId
            }
        }
    }

    private suspend fun persistLocked(userId: String) {
        withContext(Dispatchers.IO) {
            val root = JSONObject().apply {
                val projectsArr = JSONArray()
                projectsFlow.value.forEach { projectsArr.put(projectToJson(it)) }
                put("projects", projectsArr)

                val historyArr = JSONArray()
                aiHistoryFlow.value.forEach { historyArr.put(historyToJson(it)) }
                put("aiHistory", historyArr)
            }
            workspaceFile(userId).writeText(root.toString())
        }
    }

    override fun observeProjects(userId: String): Flow<List<CreativeProject>> =
        kotlinx.coroutines.flow.flow {
            ensureLoaded(userId)
            projectsFlow.collect { list ->
                emit(
                    list.filter { it.userId == userId }
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                )
            }
        }

    override fun observeProject(userId: String, projectId: String): Flow<CreativeProject?> =
        kotlinx.coroutines.flow.flow {
            ensureLoaded(userId)
            projectsFlow.collect { list ->
                emit(list.firstOrNull { it.userId == userId && it.id == projectId })
            }
        }

    override suspend fun getProject(userId: String, projectId: String): Result<CreativeProject?> {
        return try {
            ensureLoaded(userId)
            Result.success(projectsFlow.value.firstOrNull { it.userId == userId && it.id == projectId })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createProject(userId: String, project: CreativeProject): Result<String> {
        return try {
            ensureLoaded(userId)
            val now = Timestamp(System.currentTimeMillis() / 1000L, 0)
            val newId = "local_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val stored = project.copy(
                id = newId,
                userId = userId,
                name = project.name.trim().ifBlank { "Untitled Project" }.take(120),
                createdAt = now,
                updatedAt = now
            )
            mutex.withLock {
                projectsFlow.value = listOf(stored) + projectsFlow.value
                persistLocked(userId)
            }
            Result.success(newId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProject(userId: String, project: CreativeProject): Result<Unit> {
        return try {
            ensureLoaded(userId)
            require(project.id.isNotBlank()) { "Project ID must not be blank for update." }
            val now = Timestamp(System.currentTimeMillis() / 1000L, 0)
            mutex.withLock {
                val updatedList = projectsFlow.value.map { existing ->
                    if (existing.userId == userId && existing.id == project.id) {
                        project.copy(
                            userId = userId,
                            name = project.name.trim().ifBlank { "Untitled Project" }.take(120),
                            createdAt = existing.createdAt ?: now,
                            updatedAt = now
                        )
                    } else {
                        existing
                    }
                }
                projectsFlow.value = updatedList
                persistLocked(userId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameProject(userId: String, projectId: String, newName: String): Result<Unit> {
        return try {
            ensureLoaded(userId)
            val clean = newName.trim().ifBlank { "Untitled Project" }.take(120)
            val now = Timestamp(System.currentTimeMillis() / 1000L, 0)
            mutex.withLock {
                projectsFlow.value = projectsFlow.value.map { existing ->
                    if (existing.userId == userId && existing.id == projectId) {
                        existing.copy(name = clean, updatedAt = now)
                    } else {
                        existing
                    }
                }
                persistLocked(userId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun duplicateProject(userId: String, source: CreativeProject): Result<String> {
        val copyName = "${source.name.take(110)} (Copy)"
        val copy = source.copy(id = "", name = copyName, createdAt = null, updatedAt = null)
        return createProject(userId, copy)
    }

    override suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
        return try {
            ensureLoaded(userId)
            mutex.withLock {
                projectsFlow.value = projectsFlow.value.filterNot {
                    it.userId == userId && it.id == projectId
                }
                persistLocked(userId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeAiHistory(userId: String): Flow<List<AiHistoryEntry>> =
        kotlinx.coroutines.flow.flow {
            ensureLoaded(userId)
            aiHistoryFlow.collect { list ->
                emit(
                    list.filter { it.userId == userId }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                )
            }
        }

    override suspend fun addAiHistory(userId: String, entry: AiHistoryEntry): Result<Unit> {
        return try {
            ensureLoaded(userId)
            val now = Timestamp(System.currentTimeMillis() / 1000L, 0)
            val stored = entry.copy(
                id = "ai_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                userId = userId,
                createdAt = now
            )
            mutex.withLock {
                aiHistoryFlow.value = (listOf(stored) + aiHistoryFlow.value).take(100)
                persistLocked(userId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun projectToJson(p: CreativeProject): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("userId", p.userId)
        put("name", p.name)
        put("projectType", p.projectType.name)
        put("canvasWidth", p.canvasWidth)
        put("canvasHeight", p.canvasHeight)
        put("backgroundColorHex", p.backgroundColorHex)
        put("thumbnailHint", p.thumbnailHint)
        put("mediaUri", p.mediaUri)
        put("adjustments", mapToJsonObject(p.adjustments.toMap()))
        put("layers", JSONArray().apply { p.layers.forEach { put(mapToJsonObject(it.toMap())) } })
        put("clips", JSONArray().apply { p.clips.forEach { put(mapToJsonObject(it.toMap())) } })
        put("createdAtSec", p.createdAt?.seconds ?: 0L)
        put("updatedAtSec", p.updatedAt?.seconds ?: 0L)
    }

    private fun projectFromJson(json: JSONObject): CreativeProject {
        val adjMap = json.optJSONObject("adjustments")?.let { jsonObjectToMap(it) }
        val layersArr = json.optJSONArray("layers") ?: JSONArray()
        val layers = buildList {
            for (i in 0 until layersArr.length()) {
                val obj = layersArr.optJSONObject(i) ?: continue
                add(StudioLayer.fromMap(jsonObjectToMap(obj)))
            }
        }
        val clipsArr = json.optJSONArray("clips") ?: JSONArray()
        val clips = buildList {
            for (i in 0 until clipsArr.length()) {
                val obj = clipsArr.optJSONObject(i) ?: continue
                add(TimelineClip.fromMap(jsonObjectToMap(obj)))
            }
        }
        val createdSec = json.optLong("createdAtSec", 0L)
        val updatedSec = json.optLong("updatedAtSec", 0L)
        return CreativeProject(
            id = json.optString("id"),
            userId = json.optString("userId"),
            name = json.optString("name", "Untitled Project"),
            projectType = ProjectType.safeFrom(json.optString("projectType")),
            canvasWidth = json.optInt("canvasWidth", 1080),
            canvasHeight = json.optInt("canvasHeight", 1080),
            backgroundColorHex = json.optString("backgroundColorHex", "#090A0C"),
            thumbnailHint = json.optString("thumbnailHint"),
            mediaUri = json.optString("mediaUri"),
            adjustments = PhotoAdjustments.fromMap(adjMap),
            layers = layers,
            clips = clips,
            createdAt = createdSec.takeIf { it > 0L }?.let { Timestamp(it, 0) },
            updatedAt = updatedSec.takeIf { it > 0L }?.let { Timestamp(it, 0) }
        )
    }

    private fun historyToJson(h: AiHistoryEntry): JSONObject = JSONObject().apply {
        put("id", h.id)
        put("userId", h.userId)
        put("projectId", h.projectId)
        put("prompt", h.prompt)
        put("response", h.response)
        put("appliedAction", h.appliedAction)
        put("createdAtSec", h.createdAt?.seconds ?: 0L)
    }

    private fun historyFromJson(json: JSONObject): AiHistoryEntry {
        val createdSec = json.optLong("createdAtSec", 0L)
        return AiHistoryEntry(
            id = json.optString("id"),
            userId = json.optString("userId"),
            projectId = json.optString("projectId"),
            prompt = json.optString("prompt"),
            response = json.optString("response"),
            appliedAction = json.optString("appliedAction"),
            createdAt = createdSec.takeIf { it > 0L }?.let { Timestamp(it, 0) }
        )
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject = JSONObject().apply {
        map.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> = buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.get(k)
            put(k, if (v == JSONObject.NULL) null else v)
        }
    }
}
