package com.example.feature.designeditor

import android.content.Context
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
import com.example.core.database.ShapeKind
import com.example.core.database.StudioLayer
import com.example.feature.projects.ProjectsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class DesignToolTab(val label: String) {
    TEXT("Text"),
    SHAPES("Shapes"),
    ICONS("Icons"),
    IMAGES("Images"),
    BACKGROUND("Canvas"),
    LAYERS("Layers")
}

class DesignEditorViewModel(
    private val appContext: Context,
    private val repository: ProjectsRepository,
    private val currentUserId: String,
    private val projectId: String
) : ViewModel() {

    private val _projectState = MutableStateFlow<UiState<CreativeProject>>(UiState.Loading)
    val projectState: StateFlow<UiState<CreativeProject>> = _projectState.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId: StateFlow<String?> = _selectedLayerId.asStateFlow()

    private val _showGrid = MutableStateFlow(true)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

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

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var autosaveJob: Job? = null

    init {
        loadProject()
    }

    fun loadProject() {
        viewModelScope.launch {
            _projectState.value = UiState.Loading
            repository.getProject(currentUserId, projectId).fold(
                onSuccess = { proj ->
                    if (proj == null) _projectState.value = UiState.Error("Design project not found.")
                    else _projectState.value = UiState.Success(proj)
                },
                onFailure = { err ->
                    _projectState.value = UiState.Error(err.localizedMessage ?: "Failed to load design.")
                }
            )
        }
    }

    fun selectLayer(layerId: String?) {
        _selectedLayerId.value = layerId
    }

    fun toggleGrid() {
        _showGrid.value = !_showGrid.value
    }

    fun addTextLayer(
        text: String = "HEADLINE",
        fontId: String = "plus_jakarta",
        fontSizeSp: Float = 28f,
        colorHex: String = "#F5F5F5"
    ) {
        val layer = StudioLayer(
            layerId = UUID.randomUUID().toString().take(8),
            name = text.take(18),
            type = LayerType.TEXT,
            x = 0.5f,
            y = 0.5f,
            widthFraction = 0.72f,
            heightFraction = 0.16f,
            text = text,
            fontId = fontId,
            fontSizeSp = fontSizeSp,
            colorHex = colorHex
        )
        mutate { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.layerId
    }

    fun addShapeLayer(shapeKind: ShapeKind) {
        val layer = StudioLayer(
            layerId = UUID.randomUUID().toString().take(8),
            name = shapeKind.name.lowercase().replaceFirstChar { it.uppercase() },
            type = LayerType.SHAPE,
            x = 0.5f,
            y = 0.5f,
            widthFraction = if (shapeKind == ShapeKind.LINE) 0.5f else 0.34f,
            heightFraction = if (shapeKind == ShapeKind.LINE) 0.012f else 0.24f,
            colorHex = "#191C21",
            strokeColorHex = "#C4C8CE",
            strokeWidthDp = if (shapeKind == ShapeKind.LINE) 0f else 1.5f,
            shapeKind = shapeKind
        )
        mutate { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.layerId
    }

    fun addIconLayer(iconKey: String, label: String) {
        val layer = StudioLayer(
            layerId = UUID.randomUUID().toString().take(8),
            name = label,
            type = LayerType.ICON,
            x = 0.5f,
            y = 0.5f,
            widthFraction = 0.16f,
            heightFraction = 0.16f,
            colorHex = "#E1E4E8",
            iconKey = iconKey
        )
        mutate { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.layerId
    }

    fun addImageLayer(uri: Uri) {
        viewModelScope.launch {
            val res = MediaStorageHelper.importMediaToUserStorage(appContext, currentUserId, uri, "png")
            res.onSuccess { localUri ->
                val layer = StudioLayer(
                    layerId = UUID.randomUUID().toString().take(8),
                    name = "Placed Image",
                    type = LayerType.IMAGE,
                    x = 0.5f,
                    y = 0.5f,
                    widthFraction = 0.52f,
                    heightFraction = 0.42f,
                    imageUri = localUri
                )
                mutate { it.copy(layers = it.layers + layer) }
                _selectedLayerId.value = layer.layerId
            }
        }
    }

    fun setCanvasBackground(hex: String) {
        mutate { it.copy(backgroundColorHex = hex) }
    }

    fun setCanvasDimensions(width: Int, height: Int) {
        mutate { it.copy(canvasWidth = width.coerceIn(256, 4096), canvasHeight = height.coerceIn(256, 4096)) }
    }

    fun applyBrandKit(primaryHex: String, accentHex: String) {
        mutate { proj ->
            proj.copy(
                backgroundColorHex = primaryHex,
                layers = proj.layers.map { l ->
                    if (l.locked) l
                    else if (l.type == LayerType.SHAPE) l.copy(strokeColorHex = accentHex)
                    else l
                }
            )
        }
        _statusMessage.value = "Applied Brand Kit colors"
    }

    fun updateSelectedLayer(transform: (StudioLayer) -> StudioLayer) {
        val id = _selectedLayerId.value ?: return
        mutate { proj ->
            proj.copy(
                layers = proj.layers.map { layer ->
                    if (layer.layerId == id && !layer.locked) transform(layer) else layer
                }
            )
        }
    }

    fun moveLayerBy(id: String, dxFrac: Float, dyFrac: Float) {
        mutate { proj ->
            proj.copy(
                layers = proj.layers.map { layer ->
                    if (layer.layerId == id && !layer.locked) {
                        var nx = (layer.x + dxFrac).coerceIn(0.05f, 0.95f)
                        var ny = (layer.y + dyFrac).coerceIn(0.05f, 0.95f)
                        // Snap to center guides
                        if (kotlin.math.abs(nx - 0.5f) < 0.018f) nx = 0.5f
                        if (kotlin.math.abs(ny - 0.5f) < 0.018f) ny = 0.5f
                        layer.copy(x = nx, y = ny)
                    } else layer
                }
            )
        }
    }

    fun toggleLockLayer(id: String) {
        mutate { proj ->
            proj.copy(layers = proj.layers.map { if (it.layerId == id) it.copy(locked = !it.locked) else it })
        }
    }

    fun toggleHideLayer(id: String) {
        mutate { proj ->
            proj.copy(layers = proj.layers.map { if (it.layerId == id) it.copy(hidden = !it.hidden) else it })
        }
    }

    fun duplicateSelectedLayer() {
        val proj = (_projectState.value as? UiState.Success)?.data ?: return
        val target = proj.layers.firstOrNull { it.layerId == _selectedLayerId.value } ?: return
        val copy = target.copy(
            layerId = UUID.randomUUID().toString().take(8),
            name = "${target.name} Copy",
            x = (target.x + 0.04f).coerceAtMost(0.92f),
            y = (target.y + 0.04f).coerceAtMost(0.92f),
            locked = false
        )
        mutate { it.copy(layers = it.layers + copy) }
        _selectedLayerId.value = copy.layerId
    }

    fun alignSelectedCenter() {
        updateSelectedLayer { it.copy(x = 0.5f, y = 0.5f) }
    }

    fun bringSelectedForward() {
        val id = _selectedLayerId.value ?: return
        mutate { proj ->
            val idx = proj.layers.indexOfFirst { it.layerId == id }
            if (idx in 0 until proj.layers.lastIndex) {
                val mutable = proj.layers.toMutableList()
                val item = mutable.removeAt(idx)
                mutable.add(idx + 1, item)
                proj.copy(layers = mutable)
            } else proj
        }
    }

    fun sendSelectedBackward() {
        val id = _selectedLayerId.value ?: return
        mutate { proj ->
            val idx = proj.layers.indexOfFirst { it.layerId == id }
            if (idx > 0) {
                val mutable = proj.layers.toMutableList()
                val item = mutable.removeAt(idx)
                mutable.add(idx - 1, item)
                proj.copy(layers = mutable)
            } else proj
        }
    }

    fun deleteLayer(id: String) {
        mutate { proj -> proj.copy(layers = proj.layers.filterNot { it.layerId == id }) }
        if (_selectedLayerId.value == id) _selectedLayerId.value = null
    }

    fun undo() {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        _projectState.value = UiState.Success(prev)
        refreshUndoFlags()
        scheduleAutosave(prev)
    }

    fun redo() {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        _projectState.value = UiState.Success(next)
        refreshUndoFlags()
        scheduleAutosave(next)
    }

    fun saveNow() {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateProject(currentUserId, current)
            _statusMessage.value = if (res.isSuccess) "Design saved" else res.exceptionOrNull()?.localizedMessage ?: "Save failed"
            _isBusy.value = false
        }
    }

    fun exportDesign(format: String, quality: Int) {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = if (format.equals("PDF", ignoreCase = true)) {
                DesignRenderEngine.exportDesignToPdf(appContext, current)
            } else {
                val bmp = DesignRenderEngine.renderDesignBitmap(appContext, current)
                MediaStorageHelper.exportBitmapToGalleryAndCache(
                    context = appContext,
                    bitmap = bmp,
                    title = current.name,
                    format = format,
                    quality = quality
                )
            }
            res.fold(
                onSuccess = { file ->
                    _lastExportedFile.value = file
                    _statusMessage.value = "Exported ${file.name}"
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

    private fun mutate(transform: (CreativeProject) -> CreativeProject) {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        val next = transform(current)
        if (next == current) return
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
                DesignEditorViewModel(
                    appContext = appContext.applicationContext,
                    repository = repository,
                    currentUserId = currentUserId,
                    projectId = projectId
                )
            }
        }
    }
}
