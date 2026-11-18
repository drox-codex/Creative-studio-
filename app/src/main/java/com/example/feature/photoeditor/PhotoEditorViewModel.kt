package com.example.feature.photoeditor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.common.MediaStorageHelper
import com.example.core.common.UiState
import com.example.core.database.CreativeProject
import com.example.core.database.LayerType
import com.example.core.database.MaskKind
import com.example.core.database.PhotoAdjustments
import com.example.core.database.ShapeKind
import com.example.core.database.StudioLayer
import com.example.feature.aiassistant.AiCreativeService
import com.example.feature.projects.ProjectsRepository
import com.google.firebase.ai.FirebaseAI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class PhotoToolTab(val label: String) {
    CROP("Crop"),
    FILTER("Filter"),
    ADJUST("Adjust"),
    LAYERS("Layers"),
    DRAW("Draw"),
    AI("AI Tools")
}

class PhotoEditorViewModel(
    private val appContext: Context,
    private val repository: ProjectsRepository,
    private val aiService: AiCreativeService,
    private val currentUserId: String,
    private val projectId: String
) : ViewModel() {

    private val _projectState = MutableStateFlow<UiState<CreativeProject>>(UiState.Loading)
    val projectState: StateFlow<UiState<CreativeProject>> = _projectState.asStateFlow()

    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId: StateFlow<String?> = _selectedLayerId.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _lastExportedFile = MutableStateFlow<File?>(null)
    val lastExportedFile: StateFlow<File?> = _lastExportedFile.asStateFlow()

    private val undoStack = ArrayDeque<CreativeProject>()
    private val redoStack = ArrayDeque<CreativeProject>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow<Boolean>(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var autosaveJob: Job? = null

    init {
        loadProject()
    }

    fun loadProject() {
        viewModelScope.launch {
            _projectState.value = UiState.Loading
            val res = repository.getProject(currentUserId, projectId)
            res.fold(
                onSuccess = { proj ->
                    if (proj == null) {
                        _projectState.value = UiState.Error("Project not found.")
                    } else {
                        _projectState.value = UiState.Success(proj)
                        if (proj.mediaUri.isNotBlank()) {
                            _sourceBitmap.value = MediaStorageHelper.decodeBitmapSafely(appContext, proj.mediaUri, 1600)
                        }
                    }
                },
                onFailure = { err ->
                    _projectState.value = UiState.Error(err.localizedMessage ?: "Failed to load photo project.")
                }
            )
        }
    }

    fun importPrimaryPhoto(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val stored = MediaStorageHelper.importMediaToUserStorage(appContext, currentUserId, uri, "jpg")
            stored.fold(
                onSuccess = { localUri ->
                    val bmp = MediaStorageHelper.decodeBitmapSafely(appContext, localUri, 1600)
                    _sourceBitmap.value = bmp
                    mutateProject { proj ->
                        proj.copy(
                            mediaUri = localUri,
                            canvasWidth = bmp?.width ?: proj.canvasWidth,
                            canvasHeight = bmp?.height ?: proj.canvasHeight
                        )
                    }
                    _statusMessage.value = "Photo imported"
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to import photo"
                }
            )
            _isBusy.value = false
        }
    }

    fun addImageOverlayLayer(uri: Uri) {
        viewModelScope.launch {
            val stored = MediaStorageHelper.importMediaToUserStorage(appContext, currentUserId, uri, "png")
            stored.onSuccess { localUri ->
                val newLayer = StudioLayer(
                    layerId = UUID.randomUUID().toString().take(8),
                    name = "Overlay Image",
                    type = LayerType.IMAGE,
                    x = 0.5f,
                    y = 0.5f,
                    widthFraction = 0.45f,
                    heightFraction = 0.45f,
                    imageUri = localUri,
                    maskKind = MaskKind.NONE
                )
                mutateProject { it.copy(layers = it.layers + newLayer) }
                _selectedLayerId.value = newLayer.layerId
            }
        }
    }

    fun updateAdjustments(transform: (PhotoAdjustments) -> PhotoAdjustments) {
        mutateProject { it.copy(adjustments = transform(it.adjustments)) }
    }

    fun rotate90() {
        updateAdjustments { it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f) }
    }

    fun flipHorizontal() {
        updateAdjustments { it.copy(flipH = !it.flipH) }
    }

    fun flipVertical() {
        updateAdjustments { it.copy(flipV = !it.flipV) }
    }

    fun setCropPreset(aspect: String) {
        val bounds = when (aspect) {
            "1:1" -> floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f)
            "4:5" -> floatArrayOf(0.1f, 0.05f, 0.9f, 0.95f)
            "16:9" -> floatArrayOf(0f, 0.22f, 1f, 0.78f)
            "9:16" -> floatArrayOf(0.22f, 0f, 0.78f, 1f)
            else -> floatArrayOf(0f, 0f, 1f, 1f)
        }
        updateAdjustments {
            it.copy(
                cropAspect = aspect,
                cropLeft = bounds[0],
                cropTop = bounds[1],
                cropRight = bounds[2],
                cropBottom = bounds[3]
            )
        }
    }

    fun addTextLayer(text: String = "Studio Caption") {
        val layer = StudioLayer(
            layerId = UUID.randomUUID().toString().take(8),
            name = text.take(18),
            type = LayerType.TEXT,
            x = 0.5f,
            y = 0.5f,
            widthFraction = 0.7f,
            heightFraction = 0.14f,
            text = text,
            colorHex = "#F5F5F5",
            fontSizeSp = 24f
        )
        mutateProject { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.layerId
    }

    fun addShapeLayer(shapeKind: ShapeKind) {
        val layer = StudioLayer(
            layerId = UUID.randomUUID().toString().take(8),
            name = shapeKind.name,
            type = LayerType.SHAPE,
            x = 0.5f,
            y = 0.5f,
            widthFraction = 0.3f,
            heightFraction = 0.3f,
            colorHex = "#C4C8CE",
            opacity = 0.85f,
            shapeKind = shapeKind
        )
        mutateProject { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.layerId
    }

    fun addDrawingStroke(normalizedPoints: String, colorHex: String, strokeWidth: Float) {
        if (normalizedPoints.isBlank()) return
        val strokeLayer = StudioLayer(
            layerId = UUID.randomUUID().toString().take(8),
            name = "Brush Stroke",
            type = LayerType.DRAWING,
            colorHex = colorHex,
            strokeWidthDp = strokeWidth,
            pathPoints = normalizedPoints
        )
        mutateProject { it.copy(layers = it.layers + strokeLayer) }
    }

    fun selectLayer(layerId: String?) {
        _selectedLayerId.value = layerId
    }

    fun updateSelectedLayer(transform: (StudioLayer) -> StudioLayer) {
        val targetId = _selectedLayerId.value ?: return
        mutateProject { proj ->
            proj.copy(
                layers = proj.layers.map { if (it.layerId == targetId) transform(it) else it }
            )
        }
    }

    fun deleteSelectedLayer() {
        val targetId = _selectedLayerId.value ?: return
        mutateProject { proj ->
            proj.copy(layers = proj.layers.filterNot { it.layerId == targetId })
        }
        _selectedLayerId.value = null
    }

    fun runAiPhotoOperation(prompt: String) {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            _statusMessage.value = "Running Gemini AI edit…"
            val bmp = _sourceBitmap.value
            val result = aiService.processPrompt(
                prompt = prompt,
                activeProject = current,
                selectedLayerId = _selectedLayerId.value,
                activeBitmap = bmp
            )
            result.fold(
                onSuccess = { exec ->
                    if (exec.generatedBitmap != null) {
                        val saved = MediaStorageHelper.saveBitmapToUserStorage(
                            appContext,
                            currentUserId,
                            exec.generatedBitmap,
                            prefix = "ai_photo"
                        ).getOrNull()
                        _sourceBitmap.value = exec.generatedBitmap
                        if (saved != null) {
                            mutateProject { it.copy(mediaUri = saved) }
                        }
                    } else if (exec.updatedProject != null && exec.updatedProject != current) {
                        pushUndoAndApply(current, exec.updatedProject)
                    }
                    _statusMessage.value = exec.reply
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "AI operation failed"
                }
            )
            _isBusy.value = false
        }
    }

    fun undo() {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        _projectState.value = UiState.Success(prev)
        refreshUndoFlags()
        refreshBitmapIfUriChanged(current.mediaUri, prev.mediaUri)
        scheduleAutosave(prev)
    }

    fun redo() {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        _projectState.value = UiState.Success(next)
        refreshUndoFlags()
        refreshBitmapIfUriChanged(current.mediaUri, next.mediaUri)
        scheduleAutosave(next)
    }

    private fun refreshBitmapIfUriChanged(oldUri: String, newUri: String) {
        if (oldUri != newUri && newUri.isNotBlank()) {
            viewModelScope.launch {
                _sourceBitmap.value = MediaStorageHelper.decodeBitmapSafely(appContext, newUri, 1600)
            }
        }
    }

    fun saveNow() {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateProject(currentUserId, current)
            _statusMessage.value = if (res.isSuccess) "Saved to cloud" else res.exceptionOrNull()?.localizedMessage ?: "Save failed"
            _isBusy.value = false
        }
    }

    fun exportPhoto(format: String = "PNG", quality: Int = 95, onExported: (File) -> Unit) {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val rendered = PhotoRenderEngine.renderFinalBitmap(appContext, current, _sourceBitmap.value)
            val res = MediaStorageHelper.exportBitmapToGalleryAndCache(
                context = appContext,
                bitmap = rendered,
                title = current.name,
                format = format,
                quality = quality
            )
            res.fold(
                onSuccess = { file ->
                    _lastExportedFile.value = file
                    _statusMessage.value = "Exported ${file.name} to Pictures/CreativeStudio"
                    onExported(file)
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Export failed"
                }
            )
            _isBusy.value = false
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    private fun mutateProject(transform: (CreativeProject) -> CreativeProject) {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        val next = transform(current)
        if (next == current) return
        pushUndoAndApply(current, next)
    }

    private fun pushUndoAndApply(current: CreativeProject, next: CreativeProject) {
        undoStack.addLast(current)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        _projectState.value = UiState.Success(next)
        refreshUndoFlags()
        scheduleAutosave(next)
    }

    private fun refreshUndoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun scheduleAutosave(project: CreativeProject) {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1200L)
            repository.updateProject(currentUserId, project)
        }
    }

    companion object {
        fun provideFactory(
            appContext: Context,
            repository: ProjectsRepository,
            currentUserId: String,
            projectId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PhotoEditorViewModel(
                    appContext = appContext.applicationContext,
                    repository = repository,
                    aiService = AiCreativeService(),
                    currentUserId = currentUserId,
                    projectId = projectId
                )
            }
        }
    }
}
