package com.example.feature.aiassistant

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.common.MediaStorageHelper
import com.example.core.database.AiHistoryEntry
import com.example.core.database.CreativeProject
import com.example.core.database.ProjectType
import com.example.feature.projects.ProjectsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class AiMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val actionApplied: String? = null,
    val isError: Boolean = false
)

class AiAssistantViewModel(
    private val appContext: Context,
    private val projectsRepository: ProjectsRepository,
    private val aiService: AiCreativeService,
    private val currentUserId: String,
    private val initialProjectId: String?,
    private val selectedLayerId: String?
) : ViewModel() {

    private val _activeProject = MutableStateFlow<CreativeProject?>(null)
    val activeProject: StateFlow<CreativeProject?> = _activeProject.asStateFlow()

    private val _messages = MutableStateFlow<List<AiMessageItem>>(emptyList())
    val messages: StateFlow<List<AiMessageItem>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val undoStack = ArrayDeque<CreativeProject>()
    private val redoStack = ArrayDeque<CreativeProject>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    init {
        if (!initialProjectId.isNullOrBlank()) {
            viewModelScope.launch {
                val res = projectsRepository.getProject(currentUserId, initialProjectId)
                _activeProject.value = res.getOrNull()
            }
        }
    }

    fun sendPrompt(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() || _isProcessing.value) return

        _messages.value = _messages.value + AiMessageItem(isUser = true, text = cleanPrompt)
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val project = _activeProject.value
                val bitmap: Bitmap? = if (project != null && project.mediaUri.isNotBlank() && project.projectType != ProjectType.VIDEO) {
                    MediaStorageHelper.decodeBitmapSafely(appContext, project.mediaUri, 1024)
                } else null

                val result = aiService.processPrompt(
                    prompt = cleanPrompt,
                    activeProject = project,
                    selectedLayerId = selectedLayerId,
                    activeBitmap = bitmap
                )

                result.fold(
                    onSuccess = { exec ->
                        var nextProject = exec.updatedProject
                        if (exec.generatedBitmap != null) {
                            val savedUri = MediaStorageHelper.saveBitmapToUserStorage(
                                context = appContext,
                                userId = currentUserId,
                                bitmap = exec.generatedBitmap,
                                prefix = "ai"
                            ).getOrNull()
                            if (savedUri != null) {
                                nextProject = if (nextProject != null) {
                                    nextProject.copy(mediaUri = savedUri)
                                } else {
                                    val newProj = CreativeProject(
                                        name = "AI Studio Artwork",
                                        projectType = ProjectType.PHOTO,
                                        canvasWidth = exec.generatedBitmap.width.coerceIn(64, 4096),
                                        canvasHeight = exec.generatedBitmap.height.coerceIn(64, 4096),
                                        mediaUri = savedUri
                                    )
                                    val createdId = projectsRepository.createProject(currentUserId, newProj).getOrNull()
                                    if (createdId != null) newProj.copy(id = createdId) else newProj
                                }
                            }
                        }

                        if (nextProject != null && nextProject != project && nextProject.id.isNotBlank()) {
                            if (project != null) {
                                undoStack.addLast(project)
                                redoStack.clear()
                                updateUndoRedoFlags()
                            }
                            _activeProject.value = nextProject
                            projectsRepository.updateProject(currentUserId, nextProject)
                        } else if (nextProject != null && nextProject.id.isNotBlank()) {
                            _activeProject.value = nextProject
                        }

                        projectsRepository.addAiHistory(
                            currentUserId,
                            AiHistoryEntry(
                                userId = currentUserId,
                                projectId = nextProject?.id.orEmpty(),
                                prompt = cleanPrompt,
                                response = exec.reply,
                                appliedAction = exec.actionType
                            )
                        )

                        _messages.value = _messages.value + AiMessageItem(
                            isUser = false,
                            text = exec.reply,
                            actionApplied = if (exec.executable) exec.actionType else null
                        )
                    },
                    onFailure = { error ->
                        _messages.value = _messages.value + AiMessageItem(
                            isUser = false,
                            text = error.localizedMessage ?: "AI operation failed.",
                            isError = true
                        )
                    }
                )
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun undoLastAiEdit() {
        val current = _activeProject.value ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        _activeProject.value = prev
        updateUndoRedoFlags()
        viewModelScope.launch {
            if (prev.id.isNotBlank()) {
                projectsRepository.updateProject(currentUserId, prev)
            }
        }
        _messages.value = _messages.value + AiMessageItem(
            isUser = false,
            text = "Reverted the previous AI edit.",
            actionApplied = "UNDO"
        )
    }

    fun redoLastAiEdit() {
        val current = _activeProject.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        _activeProject.value = next
        updateUndoRedoFlags()
        viewModelScope.launch {
            if (next.id.isNotBlank()) {
                projectsRepository.updateProject(currentUserId, next)
            }
        }
        _messages.value = _messages.value + AiMessageItem(
            isUser = false,
            text = "Reapplied the AI edit.",
            actionApplied = "REDO"
        )
    }

    private fun updateUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    companion object {
        fun provideFactory(
            appContext: Context,
            projectsRepository: ProjectsRepository,
            currentUserId: String,
            projectId: String?,
            selectedLayerId: String?
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AiAssistantViewModel(
                    appContext = appContext.applicationContext,
                    projectsRepository = projectsRepository,
                    aiService = AiCreativeService(),
                    currentUserId = currentUserId,
                    initialProjectId = projectId,
                    selectedLayerId = selectedLayerId
                )
            }
        }
    }
}
