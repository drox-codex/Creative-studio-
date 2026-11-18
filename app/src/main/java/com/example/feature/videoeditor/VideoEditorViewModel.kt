package com.example.feature.videoeditor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.common.MediaStorageHelper
import com.example.core.common.UiState
import com.example.core.database.CreativeProject
import com.example.core.database.PhotoAdjustments
import com.example.core.database.TimelineClip
import com.example.core.database.TransitionKind
import com.example.feature.projects.ProjectsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class VideoToolTab(val label: String) {
    TRIM("Trim"),
    SPLIT("Split"),
    SPEED("Speed"),
    TRANSITION("Transition"),
    TEXT("Text"),
    FILTERS("Filters"),
    AUDIO("Audio"),
    KEYFRAMES("Keyframes")
}

class VideoEditorViewModel(
    private val appContext: Context,
    private val repository: ProjectsRepository,
    private val currentUserId: String,
    private val projectId: String
) : ViewModel() {

    private val _projectState = MutableStateFlow<UiState<CreativeProject>>(UiState.Loading)
    val projectState: StateFlow<UiState<CreativeProject>> = _projectState.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0)
    val exportProgress: StateFlow<Int> = _exportProgress.asStateFlow()

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
                    if (proj == null) {
                        _projectState.value = UiState.Error("Video project not found.")
                    } else {
                        val normalized = recalculateTimelineOffsets(proj)
                        _projectState.value = UiState.Success(normalized)
                        _selectedClipId.value = normalized.clips.firstOrNull { it.trackType == "VIDEO" }?.clipId
                            ?: normalized.clips.firstOrNull()?.clipId
                    }
                },
                onFailure = { err ->
                    _projectState.value = UiState.Error(err.localizedMessage ?: "Failed to load video project.")
                }
            )
        }
    }

    fun selectClip(clipId: String?) {
        _selectedClipId.value = clipId
    }

    fun setPlayhead(ms: Long) {
        val total = totalDurationMs().coerceAtLeast(1000L)
        _playheadMs.value = ms.coerceIn(0L, total)
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun togglePlayback() {
        _isPlaying.value = !_isPlaying.value
    }

    fun totalDurationMs(): Long {
        val proj = (_projectState.value as? UiState.Success)?.data ?: return 0L
        val videoTotal = proj.clips.filter { it.trackType == "VIDEO" }.sumOf { it.effectiveDurationMs }
        val otherMax = proj.clips.filter { it.trackType != "VIDEO" }
            .maxOfOrNull { it.timelineStartMs + it.effectiveDurationMs } ?: 0L
        return maxOf(videoTotal, otherMax)
    }

    fun importVideoClip(uri: Uri) {
        viewModelScope.launch {
            val stored = MediaStorageHelper.importMediaToUserStorage(appContext, currentUserId, uri, "mp4")
            stored.fold(
                onSuccess = { localUri ->
                    val durationMs = probeMediaDuration(localUri)
                    val videoCount = currentClipsCount("VIDEO") + 1
                    val newClip = TimelineClip(
                        clipId = UUID.randomUUID().toString().take(8),
                        trackType = "VIDEO",
                        title = "Clip $videoCount",
                        mediaUri = localUri,
                        sourceDurationMs = durationMs,
                        trimStartMs = 0L,
                        trimEndMs = durationMs
                    )
                    mutateProject { proj ->
                        val updatedClips = proj.clips + newClip
                        val firstMedia = proj.mediaUri.ifBlank { localUri }
                        recalculateTimelineOffsets(proj.copy(mediaUri = firstMedia, clips = updatedClips))
                    }
                    _selectedClipId.value = newClip.clipId
                    _statusMessage.value = "Video clip imported (${durationMs / 1000}s)"
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to import video clip."
                }
            )
        }
    }

    fun importAudioClip(uri: Uri) {
        viewModelScope.launch {
            val stored = MediaStorageHelper.importMediaToUserStorage(appContext, currentUserId, uri, "m4a")
            stored.fold(
                onSuccess = { localUri ->
                    val durationMs = probeMediaDuration(localUri)
                    val audioCount = currentClipsCount("AUDIO") + 1
                    val newAudio = TimelineClip(
                        clipId = UUID.randomUUID().toString().take(8),
                        trackType = "AUDIO",
                        title = "Audio $audioCount",
                        mediaUri = localUri,
                        sourceDurationMs = durationMs,
                        trimStartMs = 0L,
                        trimEndMs = durationMs,
                        timelineStartMs = _playheadMs.value
                    )
                    mutateProject { proj ->
                        proj.copy(clips = proj.clips + newAudio)
                    }
                    _selectedClipId.value = newAudio.clipId
                    _statusMessage.value = "Audio track added"
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to import audio track."
                }
            )
        }
    }

    fun addTextOverlayClip(text: String, durationMs: Long = 4000L) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val textClip = TimelineClip(
            clipId = UUID.randomUUID().toString().take(8),
            trackType = "TEXT",
            title = clean.take(16),
            mediaUri = "",
            sourceDurationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            timelineStartMs = _playheadMs.value,
            overlayText = clean
        )
        mutateProject { proj -> proj.copy(clips = proj.clips + textClip) }
        _selectedClipId.value = textClip.clipId
    }

    fun updateSelectedClip(transform: (TimelineClip) -> TimelineClip) {
        val targetId = _selectedClipId.value ?: return
        mutateProject { proj ->
            val updated = proj.clips.map { clip ->
                if (clip.clipId == targetId) transform(clip) else clip
            }
            recalculateTimelineOffsets(proj.copy(clips = updated))
        }
    }

    fun splitSelectedClipAtPlayhead() {
        val proj = (_projectState.value as? UiState.Success)?.data ?: return
        val targetId = _selectedClipId.value ?: return
        val clip = proj.clips.firstOrNull { it.clipId == targetId } ?: return
        val localPlayheadOffsetMs = (_playheadMs.value - clip.timelineStartMs)
        val sourceSplitOffset = (localPlayheadOffsetMs * clip.speed).toLong()
        val splitPoint = clip.trimStartMs + sourceSplitOffset
        val minMargin = 400L

        // If playhead is outside clip range, split cleanly in the middle
        val effectiveSplit = if (splitPoint <= clip.trimStartMs + minMargin || splitPoint >= clip.trimEndMs - minMargin) {
            (clip.trimStartMs + clip.trimEndMs) / 2L
        } else {
            splitPoint
        }
        if (effectiveSplit <= clip.trimStartMs + 250L || effectiveSplit >= clip.trimEndMs - 250L) {
            _statusMessage.value = "Clip is too short to split."
            return
        }

        val firstHalf = clip.copy(
            title = "${clip.title} A",
            trimEndMs = effectiveSplit
        )
        val secondHalf = clip.copy(
            clipId = UUID.randomUUID().toString().take(8),
            title = "${clip.title} B",
            trimStartMs = effectiveSplit,
            timelineStartMs = clip.timelineStartMs + firstHalf.effectiveDurationMs
        )
        val newList = proj.clips.flatMap {
            if (it.clipId == targetId) listOf(firstHalf, secondHalf) else listOf(it)
        }
        mutateProject { recalculateTimelineOffsets(it.copy(clips = newList)) }
        _selectedClipId.value = secondHalf.clipId
        _statusMessage.value = "Split clip into two segments"
    }

    fun moveSelectedClipOrder(direction: Int) {
        val proj = (_projectState.value as? UiState.Success)?.data ?: return
        val targetId = _selectedClipId.value ?: return
        val videoClips = proj.clips.filter { it.trackType == "VIDEO" }.toMutableList()
        val idx = videoClips.indexOfFirst { it.clipId == targetId }
        if (idx == -1) return
        val swapIdx = (idx + direction).coerceIn(0, videoClips.lastIndex)
        if (idx == swapIdx) return
        val item = videoClips.removeAt(idx)
        videoClips.add(swapIdx, item)
        val others = proj.clips.filter { it.trackType != "VIDEO" }
        mutateProject { recalculateTimelineOffsets(it.copy(clips = videoClips + others)) }
    }

    fun deleteSelectedClip() {
        val targetId = _selectedClipId.value ?: return
        mutateProject { proj ->
            val remaining = proj.clips.filterNot { it.clipId == targetId }
            recalculateTimelineOffsets(proj.copy(clips = remaining))
        }
        val next = (_projectState.value as? UiState.Success)?.data?.clips?.firstOrNull()?.clipId
        _selectedClipId.value = next
    }

    fun setTransitionOnSelectedClip(transition: TransitionKind) {
        updateSelectedClip { it.copy(transitionAfter = transition) }
    }

    fun updateVideoFilter(adjustments: PhotoAdjustments) {
        mutateProject { it.copy(adjustments = adjustments) }
    }

    fun setAspectRatio(width: Int, height: Int) {
        mutateProject { it.copy(canvasWidth = width, canvasHeight = height) }
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
            val res = repository.updateProject(currentUserId, current)
            _statusMessage.value = if (res.isSuccess) "Timeline saved to cloud" else res.exceptionOrNull()?.localizedMessage ?: "Save failed"
        }
    }

    fun exportVideo(resolution: String) {
        val current = (_projectState.value as? UiState.Success)?.data ?: return
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = 0
            _isPlaying.value = false
            val res = VideoExportEngine.exportVideoComposition(
                context = appContext,
                project = current,
                resolution = resolution,
                onProgress = { pct -> _exportProgress.value = pct }
            )
            res.fold(
                onSuccess = { file ->
                    _lastExportedFile.value = file
                    _statusMessage.value = "Exported ${file.name} to Movies/CreativeStudio"
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Video export failed"
                }
            )
            _isExporting.value = false
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    private fun currentClipsCount(trackType: String): Int =
        (_projectState.value as? UiState.Success)?.data?.clips?.count { it.trackType == trackType } ?: 0

    private suspend fun probeMediaDuration(uriString: String): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, Uri.parse(uriString))
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            (raw ?: 10_000L).coerceAtLeast(1000L)
        } catch (_: Exception) {
            10_000L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun recalculateTimelineOffsets(project: CreativeProject): CreativeProject {
        var cursor = 0L
        val videoClips = project.clips.filter { it.trackType == "VIDEO" }.map { clip ->
            val placed = clip.copy(timelineStartMs = cursor)
            cursor += placed.effectiveDurationMs
            placed
        }
        val otherClips = project.clips.filter { it.trackType != "VIDEO" }
        return project.copy(clips = videoClips + otherClips)
    }

    private fun mutateProject(transform: (CreativeProject) -> CreativeProject) {
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
                VideoEditorViewModel(
                    appContext = appContext.applicationContext,
                    repository = repository,
                    currentUserId = currentUserId,
                    projectId = projectId
                )
            }
        }
    }
}
