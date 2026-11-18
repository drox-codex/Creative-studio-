package com.example.feature.videoeditor

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Filter
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.core.common.MediaStorageHelper
import com.example.core.common.UiState
import com.example.core.database.CreativeProject
import com.example.core.database.TimelineClip
import com.example.core.database.TransitionKind
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioErrorBanner
import com.example.core.designsystem.StudioFilterChip
import com.example.core.designsystem.StudioPrimaryButton
import com.example.core.designsystem.StudioRadii
import com.example.core.designsystem.StudioSecondaryButton
import com.example.core.designsystem.StudioSpacing
import com.example.core.designsystem.StudioTopBar
import com.example.feature.assets.AssetCatalog
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(
    viewModel: VideoEditorViewModel,
    defaultVideoResolution: String,
    onNavigateBack: () -> Unit,
    onOpenAiAssistant: (String) -> Unit
) {
    val context = LocalContext.current
    val projectState by viewModel.projectState.collectAsStateWithLifecycle()
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val lastExported by viewModel.lastExportedFile.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(VideoToolTab.TRIM) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }
    var showExportMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importVideoClip(uri)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importAudioClip(uri)
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage!!)
            viewModel.clearStatus()
        }
    }

    val currentProject = (projectState as? UiState.Success)?.data
    val videoClips = remember(currentProject?.clips) {
        currentProject?.clips?.filter { it.trackType == "VIDEO" && it.mediaUri.isNotBlank() }.orEmpty()
    }

    // Sync ExoPlayer playlist whenever video clips change
    LaunchedEffect(videoClips) {
        if (videoClips.isNotEmpty()) {
            val items = videoClips.map { clip ->
                MediaItem.Builder()
                    .setUri(Uri.parse(clip.mediaUri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                            .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 300L))
                            .build()
                    )
                    .build()
            }
            exoPlayer.setMediaItems(items)
            exoPlayer.prepare()
            val leadClip = videoClips.first()
            exoPlayer.playbackParameters = PlaybackParameters(leadClip.speed.coerceIn(0.25f, 4f))
            exoPlayer.volume = if (leadClip.muted) 0f else leadClip.volume.coerceIn(0f, 1f)
        } else {
            exoPlayer.clearMediaItems()
        }
    }

    // Sync Play/Pause state and active clip audio/speed parameters
    LaunchedEffect(isPlaying, videoClips) {
        exoPlayer.playWhenReady = isPlaying && videoClips.isNotEmpty()
        while (isPlaying && videoClips.isNotEmpty()) {
            val currentIdx = exoPlayer.currentMediaItemIndex.coerceIn(0, videoClips.lastIndex)
            val activeClip = videoClips[currentIdx]
            exoPlayer.playbackParameters = PlaybackParameters(activeClip.speed.coerceIn(0.25f, 4f))
            exoPlayer.volume = if (activeClip.muted) 0f else activeClip.volume.coerceIn(0f, 1f)
            val offsetInClip = (exoPlayer.currentPosition / activeClip.speed.coerceAtLeast(0.25f)).toLong()
            viewModel.setPlayhead(activeClip.timelineStartMs + offsetInClip)
            delay(80L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val ui = projectState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StudioColors.MetallicSilver)
                }
            }
            is UiState.Error -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    StudioTopBar(title = "Video Studio", onNavigateBack = onNavigateBack)
                    StudioErrorBanner(message = ui.message, modifier = Modifier.padding(StudioSpacing.md))
                }
            }
            is UiState.Success -> {
                val project = ui.data
                val selectedClip = project.clips.firstOrNull { it.clipId == selectedClipId }
                    ?: videoClips.firstOrNull()
                    ?: project.clips.firstOrNull()
                val totalDurationMs = remember(project.clips) {
                    val vSum = project.clips.filter { it.trackType == "VIDEO" }.sumOf { it.effectiveDurationMs }
                    val oMax = project.clips.filter { it.trackType != "VIDEO" }
                        .maxOfOrNull { it.timelineStartMs + it.effectiveDurationMs } ?: 0L
                    maxOf(vSum, oMax, 1000L)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    StudioTopBar(
                        title = project.name,
                        subtitle = "${project.canvasWidth}×${project.canvasHeight} · ${videoClips.size} clips",
                        onNavigateBack = {
                            viewModel.setPlaying(false)
                            onNavigateBack()
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.undo() },
                                enabled = canUndo,
                                modifier = Modifier.testTag("video_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = "Undo",
                                    tint = if (canUndo) StudioColors.SubtleHighlight else StudioColors.TextTertiary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.redo() },
                                enabled = canRedo,
                                modifier = Modifier.testTag("video_redo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                                    contentDescription = "Redo",
                                    tint = if (canRedo) StudioColors.SubtleHighlight else StudioColors.TextTertiary
                                )
                            }
                            IconButton(
                                onClick = { onOpenAiAssistant(project.id) },
                                modifier = Modifier.testTag("video_ai_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "AI Assistant",
                                    tint = StudioColors.MetallicSilver
                                )
                            }
                            IconButton(onClick = { viewModel.saveNow() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = "Save timeline",
                                    tint = StudioColors.MetallicSilver
                                )
                            }
                            if (lastExported != null) {
                                IconButton(
                                    onClick = {
                                        MediaStorageHelper.shareFile(context, lastExported!!, "video/mp4")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Share exported MP4",
                                        tint = StudioColors.SubtleHighlight
                                    )
                                }
                            }
                            Box {
                                StudioPrimaryButton(
                                    text = if (isExporting) "$exportProgress%" else "Export",
                                    enabled = !isExporting,
                                    onClick = { showExportMenu = true },
                                    testTag = "video_export_button"
                                )
                                DropdownMenu(
                                    expanded = showExportMenu,
                                    onDismissRequest = { showExportMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Export 1080p MP4" + if (defaultVideoResolution == "1080p") " (Default)" else "") },
                                        onClick = {
                                            showExportMenu = false
                                            viewModel.exportVideo("1080p")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export 720p MP4" + if (defaultVideoResolution == "720p") " (Default)" else "") },
                                        onClick = {
                                            showExportMenu = false
                                            viewModel.exportVideo("720p")
                                        }
                                    )
                                }
                            }
                        }
                    )

                    if (isExporting) {
                        LinearProgressIndicator(
                            progress = { (exportProgress / 100f).coerceIn(0f, 1f) },
                            color = StudioColors.SubtleHighlight,
                            trackColor = StudioColors.SurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Video Preview Monitor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = StudioSpacing.md, vertical = StudioSpacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoPreviewMonitor(
                            project = project,
                            videoClips = videoClips,
                            exoPlayer = exoPlayer,
                            playheadMs = playheadMs,
                            onImportVideo = {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        )
                    }

                    // Playback Transport & Import Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = StudioSpacing.md, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatDuration(playheadMs)} / ${formatDuration(totalDurationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioColors.MetallicSilver
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.setPlayhead(0L)
                                    if (videoClips.isNotEmpty()) exoPlayer.seekTo(0, 0L)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Jump to start",
                                    tint = StudioColors.SubtleHighlight
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = StudioColors.SubtleHighlight,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable(enabled = videoClips.isNotEmpty()) {
                                        viewModel.togglePlayback()
                                    }
                                    .testTag("video_play_pause_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = StudioColors.BackgroundPrimary
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                modifier = Modifier.testTag("video_import_clip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Import video clip",
                                    tint = StudioColors.SubtleHighlight
                                )
                            }
                            IconButton(
                                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                                modifier = Modifier.testTag("video_import_audio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LibraryMusic,
                                    contentDescription = "Import audio track",
                                    tint = StudioColors.MetallicSilver
                                )
                            }
                        }
                    }

                    // Multi-Track Timeline Section
                    MultiTrackTimelinePanel(
                        clips = project.clips,
                        selectedClipId = selectedClip?.clipId,
                        playheadMs = playheadMs,
                        totalDurationMs = totalDurationMs,
                        onScrub = { targetMs ->
                            viewModel.setPlayhead(targetMs)
                            seekExoPlayerToTimelineMs(exoPlayer, videoClips, targetMs)
                        },
                        onSelectClip = { clipId -> viewModel.selectClip(clipId) }
                    )

                    // Contextual Tool Inspector + Bottom Tabs
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = StudioSpacing.xs)) {
                            VideoContextualInspector(
                                activeTab = activeTab,
                                project = project,
                                selectedClip = selectedClip,
                                onUpdateClip = { transform -> viewModel.updateSelectedClip(transform) },
                                onSplit = { viewModel.splitSelectedClipAtPlayhead() },
                                onReorderLeft = { viewModel.moveSelectedClipOrder(-1) },
                                onReorderRight = { viewModel.moveSelectedClipOrder(1) },
                                onDeleteClip = { viewModel.deleteSelectedClip() },
                                onSetTransition = { kind -> viewModel.setTransitionOnSelectedClip(kind) },
                                onAddCaption = { showAddTextDialog = true },
                                onApplyFilter = { adj -> viewModel.updateVideoFilter(adj) },
                                onSetAspectRatio = { w, h -> viewModel.setAspectRatio(w, h) },
                                onImportAudio = { audioPicker.launch(arrayOf("audio/*")) }
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = StudioSpacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                VideoToolTab.entries.forEach { tab ->
                                    val selected = activeTab == tab
                                    val icon = when (tab) {
                                        VideoToolTab.TRIM -> Icons.Rounded.Tune
                                        VideoToolTab.SPLIT -> Icons.Rounded.ContentCut
                                        VideoToolTab.SPEED -> Icons.Rounded.Speed
                                        VideoToolTab.TRANSITION -> Icons.Rounded.SwapHoriz
                                        VideoToolTab.TEXT -> Icons.Rounded.Title
                                        VideoToolTab.FILTERS -> Icons.Rounded.Filter
                                        VideoToolTab.AUDIO -> Icons.Rounded.GraphicEq
                                        VideoToolTab.KEYFRAMES -> Icons.Rounded.Animation
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(StudioRadii.sm))
                                            .clickable { activeTab = tab }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = tab.label,
                                            tint = if (selected) StudioColors.SubtleHighlight else StudioColors.TextSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) StudioColors.SubtleHighlight else StudioColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        )
    }

    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Add Timed Caption") },
            text = {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    placeholder = { Text("Enter caption or subtitle…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTextOverlayClip(textDraft.ifBlank { "Studio Title" })
                        textDraft = ""
                        showAddTextDialog = false
                    }
                ) { Text("Add", color = StudioColors.SubtleHighlight) }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPreviewMonitor(
    project: CreativeProject,
    videoClips: List<TimelineClip>,
    exoPlayer: ExoPlayer,
    playheadMs: Long,
    onImportVideo: () -> Unit
) {
    val aspect = (project.canvasWidth.toFloat() / project.canvasHeight.coerceAtLeast(1)).coerceIn(0.48f, 2.1f)
    val activeVideoClip = videoClips.firstOrNull {
        playheadMs >= it.timelineStartMs && playheadMs <= (it.timelineStartMs + it.effectiveDurationMs)
    } ?: videoClips.firstOrNull()

    val clipProgress = if (activeVideoClip != null && activeVideoClip.effectiveDurationMs > 0) {
        ((playheadMs - activeVideoClip.timelineStartMs).toFloat() / activeVideoClip.effectiveDurationMs).coerceIn(0f, 1f)
    } else 0f

    val kfScale = if (activeVideoClip != null) {
        activeVideoClip.keyframeStartScale + (activeVideoClip.keyframeEndScale - activeVideoClip.keyframeStartScale) * clipProgress
    } else 1f
    val kfAlpha = if (activeVideoClip != null) {
        activeVideoClip.keyframeStartOpacity + (activeVideoClip.keyframeEndOpacity - activeVideoClip.keyframeStartOpacity) * clipProgress
    } else 1f

    val activeCaptions = project.clips.filter {
        it.trackType == "TEXT" &&
            playheadMs >= it.timelineStartMs &&
            playheadMs <= (it.timelineStartMs + it.effectiveDurationMs)
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(aspect, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(StudioRadii.md))
            .background(StudioColors.BackgroundPrimary)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(StudioRadii.md)),
        contentAlignment = Alignment.Center
    ) {
        if (videoClips.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = onImportVideo)
                    .padding(StudioSpacing.lg)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = "Import video",
                    tint = StudioColors.MetallicSilver,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(StudioSpacing.sm))
                Text(
                    text = "Tap to import video clip",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Multi-track trim, speed, keyframes & MP4 export",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) view.player = exoPlayer
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = kfScale
                        scaleY = kfScale
                        alpha = kfAlpha.coerceIn(0.1f, 1f)
                    }
            )

            // Monochrome filter badge indicator when color preset is active
            if (project.adjustments.filterPreset != "Original") {
                Surface(
                    color = StudioColors.BackgroundPrimary.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(StudioRadii.full),
                    border = BorderStroke(1.dp, StudioColors.Border),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(StudioSpacing.xs)
                ) {
                    Text(
                        text = project.adjustments.filterPreset,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.MetallicSilver,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Live Timed Caption Overlay
            val displayText = activeCaptions.lastOrNull()?.overlayText
                ?: activeVideoClip?.overlayText?.takeIf { it.isNotBlank() }
            if (!displayText.isNullOrBlank()) {
                Surface(
                    color = StudioColors.BackgroundPrimary.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(StudioRadii.sm),
                    border = BorderStroke(1.dp, StudioColors.MetallicSilver.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = StudioSpacing.md, start = StudioSpacing.md, end = StudioSpacing.md)
                ) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = StudioColors.PureWhite,
                        modifier = Modifier.padding(horizontal = StudioSpacing.md, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiTrackTimelinePanel(
    clips: List<TimelineClip>,
    selectedClipId: String?,
    playheadMs: Long,
    totalDurationMs: Long,
    onScrub: (Long) -> Unit,
    onSelectClip: (String) -> Unit
) {
    val videoClips = clips.filter { it.trackType == "VIDEO" }
    val textClips = clips.filter { it.trackType == "TEXT" }
    val audioClips = clips.filter { it.trackType == "AUDIO" }

    Surface(
        color = StudioColors.BackgroundSecondary,
        border = BorderStroke(1.dp, StudioColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StudioSpacing.md, vertical = 4.dp),
        shape = RoundedCornerShape(StudioRadii.sm)
    ) {
        Column(modifier = Modifier.padding(horizontal = StudioSpacing.sm, vertical = 6.dp)) {
            // Playhead Scrubber
            Slider(
                value = (playheadMs.toFloat() / totalDurationMs.coerceAtLeast(1000L)).coerceIn(0f, 1f),
                onValueChange = { frac -> onScrub((frac * totalDurationMs).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = StudioColors.PureWhite,
                    activeTrackColor = StudioColors.MetallicSilver,
                    inactiveTrackColor = StudioColors.SurfaceElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .testTag("video_timeline_scrubber")
            )

            // Video Track
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VID",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextTertiary,
                    modifier = Modifier.width(30.dp)
                )
                if (videoClips.isEmpty()) {
                    Text(
                        text = "No video clips",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextTertiary
                    )
                } else {
                    videoClips.forEach { clip ->
                        val selected = clip.clipId == selectedClipId
                        val widthDp = ((clip.effectiveDurationMs / 1000f) * 26f).coerceIn(68f, 180f).dp
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(StudioRadii.xs),
                                color = if (selected) StudioColors.SurfaceInteractive else StudioColors.SurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) StudioColors.PureWhite else StudioColors.Border
                                ),
                                modifier = Modifier
                                    .width(widthDp)
                                    .height(34.dp)
                                    .clickable { onSelectClip(clip.clipId) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "${clip.title} (${clip.effectiveDurationMs / 1000}s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (clip.transitionAfter != TransitionKind.NONE) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(StudioColors.MetallicSilver),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "T",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioColors.BackgroundPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Text / Caption Track
            if (textClips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TXT",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextTertiary,
                        modifier = Modifier.width(30.dp)
                    )
                    textClips.forEach { clip ->
                        val selected = clip.clipId == selectedClipId
                        Surface(
                            shape = RoundedCornerShape(StudioRadii.xs),
                            color = if (selected) StudioColors.MetallicSilver else StudioColors.SurfaceElevated,
                            border = BorderStroke(1.dp, if (selected) StudioColors.PureWhite else StudioColors.Border),
                            modifier = Modifier
                                .height(24.dp)
                                .clickable { onSelectClip(clip.clipId) }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = clip.overlayText.take(18),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) StudioColors.BackgroundPrimary else StudioColors.TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Audio Track with synthesized metallic waveform bars
            if (audioClips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AUD",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextTertiary,
                        modifier = Modifier.width(30.dp)
                    )
                    audioClips.forEach { clip ->
                        val selected = clip.clipId == selectedClipId
                        Surface(
                            shape = RoundedCornerShape(StudioRadii.xs),
                            color = StudioColors.SurfaceElevated,
                            border = BorderStroke(1.dp, if (selected) StudioColors.PureWhite else StudioColors.Border),
                            modifier = Modifier
                                .width(132.dp)
                                .height(26.dp)
                                .clickable { onSelectClip(clip.clipId) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val barCount = 22
                                    val step = size.width / barCount
                                    for (i in 0 until barCount) {
                                        val hFrac = 0.25f + ((i * 7 + 3) % 5) * 0.14f
                                        val barH = size.height * hFrac
                                        val top = (size.height - barH) / 2f
                                        drawLine(
                                            color = if (clip.muted) StudioColors.TextTertiary else StudioColors.MetallicSilver.copy(alpha = 0.55f),
                                            start = Offset(i * step + 2f, top),
                                            end = Offset(i * step + 2f, top + barH),
                                            strokeWidth = 2f
                                        )
                                    }
                                }
                                Text(
                                    text = clip.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StudioColors.PureWhite,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoContextualInspector(
    activeTab: VideoToolTab,
    project: CreativeProject,
    selectedClip: TimelineClip?,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit,
    onSplit: () -> Unit,
    onReorderLeft: () -> Unit,
    onReorderRight: () -> Unit,
    onDeleteClip: () -> Unit,
    onSetTransition: (TransitionKind) -> Unit,
    onAddCaption: () -> Unit,
    onApplyFilter: (com.example.core.database.PhotoAdjustments) -> Unit,
    onSetAspectRatio: (Int, Int) -> Unit,
    onImportAudio: () -> Unit
) {
    when (activeTab) {
        VideoToolTab.TRIM -> {
            if (selectedClip == null) {
                Text(
                    text = "Import or select a clip to trim start and end points.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = StudioSpacing.md, vertical = StudioSpacing.xs)
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = StudioSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "In ${formatDuration(selectedClip.trimStartMs)} · Out ${formatDuration(selectedClip.trimEndMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.MetallicSilver
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("16:9" to (1920 to 1080), "9:16" to (1080 to 1920), "1:1" to (1080 to 1080)).forEach { (label, dim) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (project.canvasWidth == dim.first && project.canvasHeight == dim.second) {
                                        StudioColors.PureWhite
                                    } else {
                                        StudioColors.TextTertiary
                                    },
                                    modifier = Modifier
                                        .clickable { onSetAspectRatio(dim.first, dim.second) }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                    Slider(
                        value = selectedClip.trimEndMs.toFloat(),
                        onValueChange = { endVal ->
                            val minEnd = (selectedClip.trimStartMs + 500L).toFloat()
                            onUpdateClip {
                                it.copy(trimEndMs = endVal.coerceIn(minEnd, it.sourceDurationMs.toFloat()).toLong())
                            }
                        },
                        valueRange = 500f..selectedClip.sourceDurationMs.coerceAtLeast(1000L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = StudioColors.PureWhite,
                            activeTrackColor = StudioColors.MetallicSilver
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        VideoToolTab.SPLIT -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
            ) {
                item {
                    StudioSecondaryButton(
                        text = "Split at Playhead",
                        icon = Icons.Rounded.ContentCut,
                        enabled = selectedClip != null,
                        onClick = onSplit
                    )
                }
                item {
                    StudioSecondaryButton(
                        text = "Move Left",
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        enabled = selectedClip?.trackType == "VIDEO",
                        onClick = onReorderLeft
                    )
                }
                item {
                    StudioSecondaryButton(
                        text = "Move Right",
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        enabled = selectedClip?.trackType == "VIDEO",
                        onClick = onReorderRight
                    )
                }
                item {
                    StudioSecondaryButton(
                        text = "Delete Clip",
                        icon = Icons.Rounded.DeleteOutline,
                        enabled = selectedClip != null,
                        onClick = onDeleteClip
                    )
                }
            }
        }

        VideoToolTab.SPEED -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
            ) {
                items(listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f)) { spd ->
                    StudioFilterChip(
                        label = "${spd}x",
                        selected = selectedClip?.speed == spd,
                        onClick = { onUpdateClip { it.copy(speed = spd) } }
                    )
                }
                item {
                    StudioFilterChip(
                        label = if (selectedClip?.reversed == true) "Reversed" else "Reverse",
                        selected = selectedClip?.reversed == true,
                        onClick = { onUpdateClip { it.copy(reversed = !it.reversed) } }
                    )
                }
            }
        }

        VideoToolTab.TRANSITION -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
            ) {
                items(TransitionKind.entries) { kind ->
                    StudioFilterChip(
                        label = kind.name.replace('_', ' '),
                        selected = selectedClip?.transitionAfter == kind,
                        onClick = { onSetTransition(kind) }
                    )
                }
            }
        }

        VideoToolTab.TEXT -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
            ) {
                item {
                    StudioSecondaryButton(
                        text = "Add Timed Caption",
                        icon = Icons.Rounded.Title,
                        onClick = onAddCaption
                    )
                }
                if (selectedClip != null) {
                    item {
                        StudioSecondaryButton(
                            text = "Clear Selected Caption",
                            icon = Icons.Rounded.DeleteOutline,
                            onClick = {
                                if (selectedClip.trackType == "TEXT") onDeleteClip()
                                else onUpdateClip { it.copy(overlayText = "") }
                            }
                        )
                    }
                }
            }
        }

        VideoToolTab.FILTERS -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
            ) {
                items(AssetCatalog.filterPresets, key = { it.id }) { preset ->
                    StudioFilterChip(
                        label = preset.name,
                        selected = project.adjustments.filterPreset == preset.name,
                        onClick = { onApplyFilter(preset.adjustments) }
                    )
                }
            }
        }

        VideoToolTab.AUDIO -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StudioSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
            ) {
                IconButton(
                    onClick = { onUpdateClip { it.copy(muted = !it.muted) } },
                    enabled = selectedClip != null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (selectedClip?.muted == true) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Mute toggle",
                        tint = StudioColors.SubtleHighlight
                    )
                }
                Slider(
                    value = selectedClip?.volume ?: 1f,
                    onValueChange = { v -> onUpdateClip { it.copy(volume = v, muted = v <= 0.01f) } },
                    enabled = selectedClip != null,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioColors.PureWhite,
                        activeTrackColor = StudioColors.MetallicSilver
                    ),
                    modifier = Modifier.weight(1f)
                )
                StudioFilterChip(
                    label = "Fade",
                    selected = (selectedClip?.fadeInMs ?: 0L) > 0L,
                    onClick = {
                        onUpdateClip {
                            val fade = if (it.fadeInMs > 0L) 0L else 800L
                            it.copy(fadeInMs = fade, fadeOutMs = fade)
                        }
                    }
                )
                StudioFilterChip(
                    label = "+ Audio",
                    selected = false,
                    onClick = onImportAudio
                )
            }
        }

        VideoToolTab.KEYFRAMES -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StudioSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
            ) {
                Text(
                    text = "Zoom ${(selectedClip?.keyframeEndScale ?: 1f).let { String.format(Locale.US, "%.2fx", it) }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.MetallicSilver
                )
                Slider(
                    value = selectedClip?.keyframeEndScale ?: 1f,
                    onValueChange = { s -> onUpdateClip { it.copy(keyframeStartScale = 1f, keyframeEndScale = s) } },
                    valueRange = 1f..1.45f,
                    enabled = selectedClip != null,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioColors.PureWhite,
                        activeTrackColor = StudioColors.MetallicSilver
                    ),
                    modifier = Modifier.weight(1f)
                )
                StudioFilterChip(
                    label = "Fade In",
                    selected = (selectedClip?.keyframeStartOpacity ?: 1f) < 0.9f,
                    onClick = {
                        onUpdateClip {
                            val nextStart = if (it.keyframeStartOpacity < 0.9f) 1f else 0.15f
                            it.copy(keyframeStartOpacity = nextStart, keyframeEndOpacity = 1f)
                        }
                    }
                )
            }
        }
    }
}

private fun seekExoPlayerToTimelineMs(
    exoPlayer: ExoPlayer,
    videoClips: List<TimelineClip>,
    timelineMs: Long
) {
    if (videoClips.isEmpty()) return
    val idx = videoClips.indexOfFirst {
        timelineMs >= it.timelineStartMs && timelineMs <= (it.timelineStartMs + it.effectiveDurationMs)
    }.let { if (it == -1) videoClips.lastIndex else it }
    val clip = videoClips[idx]
    val localOffset = ((timelineMs - clip.timelineStartMs).coerceAtLeast(0L) * clip.speed).toLong()
    exoPlayer.seekTo(idx, localOffset.coerceIn(0L, (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(0L)))
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
