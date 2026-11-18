package com.example

import com.example.core.common.UiState
import com.example.core.database.AiHistoryEntry
import com.example.core.database.CreativeProject
import com.example.core.database.LayerType
import com.example.core.database.PhotoAdjustments
import com.example.core.database.ProjectType
import com.example.core.database.ShapeKind
import com.example.core.database.StudioLayer
import com.example.core.database.TimelineClip
import com.example.core.database.TransitionKind
import com.example.feature.projects.ProjectsRepository
import com.example.feature.projects.ProjectsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class StudioLogicAndSerializationTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun creativeProjectCreateMapOmitsDocumentIdAndMatchesRulesSchema() {
        val project = CreativeProject(
            id = "ignored_doc_id",
            userId = "uid_123",
            name = "Editorial Poster",
            projectType = ProjectType.DESIGN,
            canvasWidth = 1080,
            canvasHeight = 1350,
            backgroundColorHex = "#090A0C",
            adjustments = PhotoAdjustments(brightness = 12f, contrast = 1.2f, filterPreset = "Noir"),
            layers = listOf(
                StudioLayer(
                    layerId = "l1",
                    name = "Headline",
                    type = LayerType.TEXT,
                    text = "DREAM BIGGER",
                    shapeKind = ShapeKind.ROUNDED_RECT
                )
            ),
            clips = listOf(
                TimelineClip(
                    clipId = "c1",
                    trackType = "VIDEO",
                    sourceDurationMs = 12_000L,
                    trimStartMs = 1_000L,
                    trimEndMs = 9_000L,
                    speed = 2f,
                    transitionAfter = TransitionKind.CROSSFADE
                )
            )
        )

        val map = project.toCreateMap("uid_123")
        assertFalse("Firestore document ID must not be serialized inside payload", map.containsKey("id"))
        assertEquals("uid_123", map["userId"])
        assertEquals("DESIGN", map["projectType"])
        assertEquals(1080, map["canvasWidth"])
        assertEquals(1350, map["canvasHeight"])
        assertTrue(map.containsKey("createdAt"))
        assertTrue(map.containsKey("updatedAt"))

        // Round-trip submodels
        @Suppress("UNCHECKED_CAST")
        val parsedAdj = PhotoAdjustments.fromMap(map["adjustments"] as Map<String, Any?>)
        assertEquals("Noir", parsedAdj.filterPreset)
        assertEquals(1.2f, parsedAdj.contrast, 0.01f)

        @Suppress("UNCHECKED_CAST")
        val rawLayers = map["layers"] as List<Map<String, Any?>>
        val parsedLayer = StudioLayer.fromMap(rawLayers.first())
        assertEquals("DREAM BIGGER", parsedLayer.text)

        @Suppress("UNCHECKED_CAST")
        val rawClips = map["clips"] as List<Map<String, Any?>>
        val parsedClip = TimelineClip.fromMap(rawClips.first())
        assertEquals(TransitionKind.CROSSFADE, parsedClip.transitionAfter)
        assertEquals(4_000L, parsedClip.effectiveDurationMs)
    }

    @Test
    fun projectsViewModelCrudIsScopedToCurrentUser() = runTest(testDispatcher) {
        val repo = InMemoryProjectsRepo()
        val aliceVm = ProjectsViewModel(repo, "alice_uid")
        val collectJob = launch { aliceVm.projectsState.collect {} }

        advanceUntilIdle()
        var createdId = ""
        aliceVm.createProject(
            CreativeProject(name = "Alice Poster", projectType = ProjectType.DESIGN)
        ) { id, _ -> createdId = id }
        advanceUntilIdle()

        assertTrue(createdId.isNotBlank())
        val stateAfterCreate = aliceVm.projectsState.value as UiState.Success
        assertEquals(1, stateAfterCreate.data.size)
        assertEquals("Alice Poster", stateAfterCreate.data.first().name)

        // Rename
        aliceVm.renameProject(createdId, "Alice V2")
        advanceUntilIdle()
        assertEquals("Alice V2", (aliceVm.projectsState.value as UiState.Success).data.first().name)

        // Bob cannot see Alice's project
        val bobVm = ProjectsViewModel(repo, "bob_uid")
        val bobJob = launch { bobVm.projectsState.collect {} }
        advanceUntilIdle()
        assertEquals(0, (bobVm.projectsState.value as UiState.Success).data.size)

        // Delete
        aliceVm.deleteProject(createdId)
        advanceUntilIdle()
        assertEquals(0, (aliceVm.projectsState.value as UiState.Success).data.size)

        collectJob.cancel()
        bobJob.cancel()
    }

    private class InMemoryProjectsRepo : ProjectsRepository {
        private val store = MutableStateFlow<List<CreativeProject>>(emptyList())
        private var counter = 0

        override fun observeProjects(userId: String): Flow<List<CreativeProject>> =
            store.map { list -> list.filter { it.userId == userId } }

        override fun observeProject(userId: String, projectId: String): Flow<CreativeProject?> =
            store.map { list -> list.firstOrNull { it.userId == userId && it.id == projectId } }

        override suspend fun getProject(userId: String, projectId: String): Result<CreativeProject?> =
            Result.success(store.value.firstOrNull { it.userId == userId && it.id == projectId })

        override suspend fun createProject(userId: String, project: CreativeProject): Result<String> {
            val id = "proj_${++counter}"
            store.value = store.value + project.copy(id = id, userId = userId)
            return Result.success(id)
        }

        override suspend fun updateProject(userId: String, project: CreativeProject): Result<Unit> {
            store.value = store.value.map {
                if (it.userId == userId && it.id == project.id) project else it
            }
            return Result.success(Unit)
        }

        override suspend fun renameProject(userId: String, projectId: String, newName: String): Result<Unit> {
            store.value = store.value.map {
                if (it.userId == userId && it.id == projectId) it.copy(name = newName) else it
            }
            return Result.success(Unit)
        }

        override suspend fun duplicateProject(userId: String, source: CreativeProject): Result<String> =
            createProject(userId, source.copy(name = "${source.name} (Copy)"))

        override suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
            store.value = store.value.filterNot { it.userId == userId && it.id == projectId }
            return Result.success(Unit)
        }

        override fun observeAiHistory(userId: String): Flow<List<AiHistoryEntry>> = flowOf(emptyList())

        override suspend fun addAiHistory(userId: String, entry: AiHistoryEntry): Result<Unit> = Result.success(Unit)
    }
}
