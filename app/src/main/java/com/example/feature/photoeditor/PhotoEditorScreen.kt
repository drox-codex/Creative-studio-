package com.example.feature.photoeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Filter
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.common.MediaStorageHelper
import com.example.core.common.UiState
import com.example.core.database.CreativeProject
import com.example.core.database.LayerType
import com.example.core.database.MaskKind
import com.example.core.database.ShapeKind
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioErrorBanner
import com.example.core.designsystem.StudioFilterChip
import com.example.core.designsystem.StudioFontOption
import com.example.core.designsystem.StudioPrimaryButton
import com.example.core.designsystem.StudioRadii
import com.example.core.designsystem.StudioSecondaryButton
import com.example.core.designsystem.StudioSpacing
import com.example.core.designsystem.StudioTopBar
import com.example.feature.assets.AssetCatalog
import kotlin.math.roundToInt

@Composable
fun PhotoEditorScreen(
    viewModel: PhotoEditorViewModel,
    defaultExportFormat: String,
    defaultExportQuality: Int,
    onNavigateBack: () -> Unit,
    onOpenAiAssistant: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val projectState by viewModel.projectState.collectAsStateWithLifecycle()
    val sourceBitmap by viewModel.sourceBitmap.collectAsStateWithLifecycle()
    val selectedLayerId by viewModel.selectedLayerId.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val lastExported by viewModel.lastExportedFile.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(PhotoToolTab.ADJUST) }
    var selectedAdjustParam by remember { mutableStateOf("Brightness") }
    var brushColorHex by remember { mutableStateOf("#F5F5F5") }
    var brushSize by remember { mutableFloatStateOf(8f) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var newTextDraft by remember { mutableStateOf("") }
    var showAiReplacePrompt by remember { mutableStateOf(false) }
    var aiCustomPrompt by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    val primaryPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importPrimaryPhoto(uri)
    }

    val overlayPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addImageOverlayLayer(uri)
    }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage!!)
            viewModel.clearStatus()
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
                    StudioTopBar(title = "Photo Editor", onNavigateBack = onNavigateBack)
                    StudioErrorBanner(message = ui.message, modifier = Modifier.padding(StudioSpacing.md))
                }
            }
            is UiState.Success -> {
                val project = ui.data
                Column(modifier = Modifier.fillMaxSize()) {
                    StudioTopBar(
                        title = project.name,
                        subtitle = "${project.adjustments.filterPreset} · ${project.canvasWidth}×${project.canvasHeight}",
                        onNavigateBack = onNavigateBack,
                        actions = {
                            IconButton(
                                onClick = { viewModel.undo() },
                                enabled = canUndo,
                                modifier = Modifier.testTag("photo_undo_button")
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
                                modifier = Modifier.testTag("photo_redo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                                    contentDescription = "Redo",
                                    tint = if (canRedo) StudioColors.SubtleHighlight else StudioColors.TextTertiary
                                )
                            }
                            IconButton(
                                onClick = { onOpenAiAssistant(project.id, selectedLayerId) },
                                modifier = Modifier.testTag("photo_ai_button")
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
                                    contentDescription = "Save",
                                    tint = StudioColors.MetallicSilver
                                )
                            }
                            if (lastExported != null) {
                                IconButton(
                                    onClick = {
                                        MediaStorageHelper.shareFile(
                                            context,
                                            lastExported!!,
                                            if (defaultExportFormat == "JPEG") "image/jpeg" else "image/png"
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Share export",
                                        tint = StudioColors.SubtleHighlight
                                    )
                                }
                            }
                            StudioPrimaryButton(
                                text = "Export",
                                onClick = {
                                    viewModel.exportPhoto(defaultExportFormat, defaultExportQuality) {}
                                },
                                testTag = "photo_export_button"
                            )
                        }
                    )

                    // Interactive Photo Canvas Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(StudioSpacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        PhotoCanvasStage(
                            project = project,
                            sourceBitmap = sourceBitmap,
                            selectedLayerId = selectedLayerId,
                            drawingEnabled = activeTab == PhotoToolTab.DRAW,
                            brushColorHex = brushColorHex,
                            brushSize = brushSize,
                            onImportClick = {
                                primaryPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onMoveLayer = { id, dxFrac, dyFrac ->
                                viewModel.selectLayer(id)
                                viewModel.updateSelectedLayer { layer ->
                                    layer.copy(
                                        x = (layer.x + dxFrac).coerceIn(0.05f, 0.95f),
                                        y = (layer.y + dyFrac).coerceIn(0.05f, 0.95f)
                                    )
                                }
                            },
                            onCommitStroke = { pts ->
                                viewModel.addDrawingStroke(pts, brushColorHex, brushSize)
                            }
                        )

                        if (isBusy) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color(0x99090A0C)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = StudioColors.SubtleHighlight)
                            }
                        }
                    }

                    // Contextual Tool Panel
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = StudioSpacing.sm)
                        ) {
                            when (activeTab) {
                                PhotoToolTab.CROP -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                                    ) {
                                        StudioSecondaryButton(
                                            text = "Rotate 90",
                                            icon = Icons.AutoMirrored.Rounded.RotateRight,
                                            onClick = { viewModel.rotate90() },
                                            modifier = Modifier.weight(1f)
                                        )
                                        StudioSecondaryButton(
                                            text = "Flip H",
                                            icon = Icons.Rounded.Flip,
                                            onClick = { viewModel.flipHorizontal() },
                                            modifier = Modifier.weight(1f)
                                        )
                                        StudioSecondaryButton(
                                            text = "Flip V",
                                            icon = Icons.Rounded.Flip,
                                            onClick = { viewModel.flipVertical() },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(StudioSpacing.xs))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                                    ) {
                                        items(listOf("Free", "1:1", "4:5", "16:9", "9:16")) { aspect ->
                                            StudioFilterChip(
                                                label = aspect,
                                                selected = project.adjustments.cropAspect == aspect,
                                                onClick = { viewModel.setCropPreset(aspect) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(StudioSpacing.xs))
                                    Row(
                                        modifier = Modifier.padding(horizontal = StudioSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Straighten ${project.adjustments.straightenDegrees.roundToInt()}°",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = StudioColors.MetallicSilver,
                                            modifier = Modifier.width(110.dp)
                                        )
                                        Slider(
                                            value = project.adjustments.straightenDegrees,
                                            onValueChange = { deg ->
                                                viewModel.updateAdjustments { it.copy(straightenDegrees = deg) }
                                            },
                                            valueRange = -45f..45f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = StudioColors.PureWhite,
                                                activeTrackColor = StudioColors.MetallicSilver
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                PhotoToolTab.FILTER -> {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                                    ) {
                                        items(AssetCatalog.filterPresets, key = { it.id }) { preset ->
                                            StudioFilterChip(
                                                label = preset.name,
                                                selected = project.adjustments.filterPreset == preset.name,
                                                onClick = {
                                                    viewModel.updateAdjustments { current ->
                                                        preset.adjustments.copy(
                                                            rotationDegrees = current.rotationDegrees,
                                                            straightenDegrees = current.straightenDegrees,
                                                            flipH = current.flipH,
                                                            flipV = current.flipV,
                                                            cropAspect = current.cropAspect,
                                                            cropLeft = current.cropLeft,
                                                            cropTop = current.cropTop,
                                                            cropRight = current.cropRight,
                                                            cropBottom = current.cropBottom
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                PhotoToolTab.ADJUST -> {
                                    val adj = project.adjustments
                                    val params = listOf(
                                        "Brightness", "Contrast", "Saturation",
                                        "Temperature", "Exposure", "Highlights",
                                        "Shadows", "Sharpness", "Vignette"
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                                    ) {
                                        items(params) { p ->
                                            StudioFilterChip(
                                                label = p,
                                                selected = selectedAdjustParam == p,
                                                onClick = { selectedAdjustParam = p }
                                            )
                                        }
                                    }
                                    val (valNow, range) = when (selectedAdjustParam) {
                                        "Brightness" -> adj.brightness to (-100f..100f)
                                        "Contrast" -> adj.contrast to (0.5f..1.8f)
                                        "Saturation" -> adj.saturation to (0f..2f)
                                        "Temperature" -> adj.temperature to (-50f..50f)
                                        "Exposure" -> adj.exposure to (-1f..1f)
                                        "Highlights" -> adj.highlights to (-100f..100f)
                                        "Shadows" -> adj.shadows to (-100f..100f)
                                        "Sharpness" -> adj.sharpness to (0f..1f)
                                        else -> adj.vignette to (0f..1f)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = StudioSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = String.format("%.2f", valNow),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = StudioColors.SubtleHighlight,
                                            modifier = Modifier.width(56.dp)
                                        )
                                        Slider(
                                            value = valNow,
                                            onValueChange = { v ->
                                                viewModel.updateAdjustments { cur ->
                                                    when (selectedAdjustParam) {
                                                        "Brightness" -> cur.copy(brightness = v)
                                                        "Contrast" -> cur.copy(contrast = v)
                                                        "Saturation" -> cur.copy(saturation = v)
                                                        "Temperature" -> cur.copy(temperature = v)
                                                        "Exposure" -> cur.copy(exposure = v)
                                                        "Highlights" -> cur.copy(highlights = v)
                                                        "Shadows" -> cur.copy(shadows = v)
                                                        "Sharpness" -> cur.copy(sharpness = v)
                                                        else -> cur.copy(vignette = v)
                                                    }
                                                }
                                            },
                                            valueRange = range,
                                            colors = SliderDefaults.colors(
                                                thumbColor = StudioColors.PureWhite,
                                                activeTrackColor = StudioColors.MetallicSilver
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                PhotoToolTab.LAYERS -> {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                                    ) {
                                        item {
                                            StudioSecondaryButton(
                                                text = "Replace Photo",
                                                icon = Icons.Rounded.AddPhotoAlternate,
                                                onClick = {
                                                    primaryPhotoPicker.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                            )
                                        }
                                        item {
                                            StudioSecondaryButton(
                                                text = "Add Overlay",
                                                icon = Icons.Rounded.Layers,
                                                onClick = {
                                                    overlayPhotoPicker.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                            )
                                        }
                                        item {
                                            StudioSecondaryButton(
                                                text = "Add Text",
                                                icon = Icons.Rounded.Title,
                                                onClick = { showAddTextDialog = true }
                                            )
                                        }
                                        item {
                                            StudioSecondaryButton(
                                                text = "Add Shape",
                                                onClick = { viewModel.addShapeLayer(ShapeKind.ROUNDED_RECT) }
                                            )
                                        }
                                        if (selectedLayerId != null) {
                                            item {
                                                StudioSecondaryButton(
                                                    text = "Mask Circle",
                                                    onClick = {
                                                        viewModel.updateSelectedLayer {
                                                            it.copy(
                                                                maskKind = if (it.maskKind == MaskKind.CIRCLE) MaskKind.NONE else MaskKind.CIRCLE
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                            item {
                                                StudioSecondaryButton(
                                                    text = "Delete Layer",
                                                    icon = Icons.Rounded.DeleteOutline,
                                                    onClick = { viewModel.deleteSelectedLayer() }
                                                )
                                            }
                                        }
                                    }
                                }

                                PhotoToolTab.DRAW -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = StudioSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                                    ) {
                                        listOf("#F5F5F5", "#C4C8CE", "#090A0C", "#D6B985").forEach { hex ->
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                                    .border(
                                                        width = if (brushColorHex == hex) 2.dp else 1.dp,
                                                        color = if (brushColorHex == hex) StudioColors.PureWhite else StudioColors.Border,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { brushColorHex = hex }
                                            )
                                        }
                                        Slider(
                                            value = brushSize,
                                            onValueChange = { brushSize = it },
                                            valueRange = 2f..24f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = StudioColors.PureWhite,
                                                activeTrackColor = StudioColors.MetallicSilver
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                PhotoToolTab.AI -> {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                                    ) {
                                        item {
                                            StudioSecondaryButton(
                                                text = "Remove Background",
                                                icon = Icons.Rounded.AutoFixHigh,
                                                onClick = {
                                                    viewModel.runAiPhotoOperation("Remove the background and isolate the subject on a pure #090A0C studio black backdrop")
                                                }
                                            )
                                        }
                                        item {
                                            StudioSecondaryButton(
                                                text = "Enhance Quality",
                                                icon = Icons.Rounded.AutoAwesome,
                                                onClick = {
                                                    viewModel.runAiPhotoOperation("Improve image quality, clarity, and dynamic range without changing the subject")
                                                }
                                            )
                                        }
                                        item {
                                            StudioSecondaryButton(
                                                text = "Cinematic Look",
                                                onClick = {
                                                    viewModel.runAiPhotoOperation("Make the image look cinematic with balanced silver contrast")
                                                }
                                            )
                                        }
                                        item {
                                            StudioSecondaryButton(
                                                text = "Object / BG Prompt",
                                                onClick = { showAiReplacePrompt = true }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(StudioSpacing.xs))

                            // Bottom Tool Selector Tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = StudioSpacing.xs),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                PhotoToolTab.entries.forEach { tab ->
                                    val selected = activeTab == tab
                                    val icon = when (tab) {
                                        PhotoToolTab.CROP -> Icons.Rounded.Crop
                                        PhotoToolTab.FILTER -> Icons.Rounded.Filter
                                        PhotoToolTab.ADJUST -> Icons.Rounded.Tune
                                        PhotoToolTab.LAYERS -> Icons.Rounded.Layers
                                        PhotoToolTab.DRAW -> Icons.Rounded.Brush
                                        PhotoToolTab.AI -> Icons.Rounded.AutoAwesome
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(StudioRadii.sm))
                                            .clickable { activeTab = tab }
                                            .padding(horizontal = StudioSpacing.sm, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = tab.label,
                                            tint = if (selected) StudioColors.SubtleHighlight else StudioColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
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
            title = { Text("Add Text Layer") },
            text = {
                OutlinedTextField(
                    value = newTextDraft,
                    onValueChange = { newTextDraft = it },
                    placeholder = { Text("Enter text…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTextLayer(newTextDraft.ifBlank { "Studio Text" })
                        newTextDraft = ""
                        showAddTextDialog = false
                    }
                ) { Text("Add", color = StudioColors.SubtleHighlight) }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAiReplacePrompt) {
        AlertDialog(
            onDismissRequest = { showAiReplacePrompt = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("AI Edit Instruction") },
            text = {
                OutlinedTextField(
                    value = aiCustomPrompt,
                    onValueChange = { aiCustomPrompt = it },
                    placeholder = { Text("e.g., Remove the background or replace sky…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aiCustomPrompt.isNotBlank()) {
                            viewModel.runAiPhotoOperation(aiCustomPrompt)
                        }
                        aiCustomPrompt = ""
                        showAiReplacePrompt = false
                    }
                ) { Text("Run AI", color = StudioColors.SubtleHighlight) }
            },
            dismissButton = {
                TextButton(onClick = { showAiReplacePrompt = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PhotoCanvasStage(
    project: CreativeProject,
    sourceBitmap: android.graphics.Bitmap?,
    selectedLayerId: String?,
    drawingEnabled: Boolean,
    brushColorHex: String,
    brushSize: Float,
    onImportClick: () -> Unit,
    onSelectLayer: (String?) -> Unit,
    onMoveLayer: (String, Float, Float) -> Unit,
    onCommitStroke: (String) -> Unit
) {
    val adj = project.adjustments
    val aspect = (project.canvasWidth.toFloat() / project.canvasHeight.coerceAtLeast(1)).coerceIn(0.45f, 2.2f)
    val activeStroke = remember { mutableStateListOf<Offset>() }

    val colorFilter = remember(adj) {
        val cm = PhotoRenderEngine.buildColorMatrix(adj)
        ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(cm.array))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(StudioRadii.md))
            .background(StudioColors.BackgroundSecondary)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(StudioRadii.md))
            .clickable(enabled = sourceBitmap == null) { onImportClick() }
    ) {
        val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val stageH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        if (sourceBitmap != null) {
            Image(
                bitmap = sourceBitmap.asImageBitmap(),
                contentDescription = project.name,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = adj.rotationDegrees + adj.straightenDegrees
                        scaleX = if (adj.flipH) -1f else 1f
                        scaleY = if (adj.flipV) -1f else 1f
                    }
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = "Import Photo",
                    tint = StudioColors.MetallicSilver,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(StudioSpacing.xs))
                Text(
                    text = "Tap to import a photo",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioColors.TextPrimary
                )
                Text(
                    text = "Non-destructive RAW-style adjustments & AI tools",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextSecondary
                )
            }
        }

        // Render saved layers
        project.layers.filterNot { it.hidden }.forEach { layer ->
            val isSelected = layer.layerId == selectedLayerId
            when (layer.type) {
                LayerType.DRAWING -> {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pts = layer.pathPoints.split(';')
                        val path = Path()
                        pts.forEachIndexed { idx, tok ->
                            val xy = tok.split(',')
                            if (xy.size == 2) {
                                val px = (xy[0].toFloatOrNull() ?: 0f) * size.width
                                val py = (xy[1].toFloatOrNull() ?: 0f) * size.height
                                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color(PhotoRenderEngine.parseColorSafe(layer.colorHex, android.graphics.Color.WHITE)),
                            style = Stroke(width = layer.strokeWidthDp, cap = StrokeCap.Round)
                        )
                    }
                }
                LayerType.TEXT -> {
                    val xOff = ((layer.x - 0.5f) * stageW).roundToInt()
                    val yOff = ((layer.y - 0.5f) * stageH).roundToInt()
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset(xOff, yOff) }
                            .then(
                                if (isSelected) Modifier.border(1.dp, StudioColors.SubtleHighlight, RoundedCornerShape(4.dp))
                                else Modifier
                            )
                            .pointerInput(layer.layerId, drawingEnabled) {
                                if (!drawingEnabled) {
                                    detectDragGestures(
                                        onDragStart = { onSelectLayer(layer.layerId) },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            onMoveLayer(layer.layerId, drag.x / stageW, drag.y / stageH)
                                        }
                                    )
                                }
                            }
                            .clickable { onSelectLayer(layer.layerId) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = layer.text,
                            fontFamily = StudioFontOption.fromId(layer.fontId).family,
                            fontSize = layer.fontSizeSp.sp,
                            fontWeight = if (layer.bold) FontWeight.Bold else FontWeight.Medium,
                            color = Color(PhotoRenderEngine.parseColorSafe(layer.colorHex, android.graphics.Color.WHITE))
                        )
                    }
                }
                LayerType.IMAGE -> {
                    val xOff = ((layer.x - 0.5f) * stageW).roundToInt()
                    val yOff = ((layer.y - 0.5f) * stageH).roundToInt()
                    val clipShape = when (layer.maskKind) {
                        MaskKind.CIRCLE -> CircleShape
                        MaskKind.ROUNDED_RECT -> RoundedCornerShape(16.dp)
                        else -> RoundedCornerShape(4.dp)
                    }
                    AsyncImage(
                        model = layer.imageUri,
                        contentDescription = layer.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset(xOff, yOff) }
                            .size(width = (maxWidth * layer.widthFraction), height = (maxHeight * layer.heightFraction))
                            .clip(clipShape)
                            .then(
                                if (isSelected) Modifier.border(1.5.dp, StudioColors.SubtleHighlight, clipShape)
                                else Modifier
                            )
                            .pointerInput(layer.layerId, drawingEnabled) {
                                if (!drawingEnabled) {
                                    detectDragGestures(
                                        onDragStart = { onSelectLayer(layer.layerId) },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            onMoveLayer(layer.layerId, drag.x / stageW, drag.y / stageH)
                                        }
                                    )
                                }
                            }
                    )
                }
                LayerType.SHAPE -> {
                    val xOff = ((layer.x - 0.5f) * stageW).roundToInt()
                    val yOff = ((layer.y - 0.5f) * stageH).roundToInt()
                    val shapeClip = when (layer.shapeKind) {
                        ShapeKind.CIRCLE -> CircleShape
                        ShapeKind.ROUNDED_RECT, ShapeKind.BADGE -> RoundedCornerShape(layer.cornerRadiusDp.dp)
                        else -> RoundedCornerShape(2.dp)
                    }
                    val fillColor = Color(PhotoRenderEngine.parseColorSafe(layer.colorHex, android.graphics.Color.LTGRAY))
                        .copy(alpha = layer.opacity.coerceIn(0.05f, 1f))
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset(xOff, yOff) }
                            .size(width = (maxWidth * layer.widthFraction), height = (maxHeight * layer.heightFraction))
                            .clip(shapeClip)
                            .background(fillColor)
                            .then(
                                if (isSelected) Modifier.border(1.5.dp, StudioColors.SubtleHighlight, shapeClip)
                                else Modifier
                            )
                            .pointerInput(layer.layerId, drawingEnabled) {
                                if (!drawingEnabled) {
                                    detectDragGestures(
                                        onDragStart = { onSelectLayer(layer.layerId) },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            onMoveLayer(layer.layerId, drag.x / stageW, drag.y / stageH)
                                        }
                                    )
                                }
                            }
                            .clickable { onSelectLayer(layer.layerId) }
                    )
                }
                else -> {}
            }
        }

        // Interactive Freehand Drawing Overlay
        if (drawingEnabled) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(brushColorHex, brushSize) {
                        detectDragGestures(
                            onDragStart = { start ->
                                activeStroke.clear()
                                activeStroke.add(start)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                activeStroke.add(change.position)
                            },
                            onDragEnd = {
                                if (activeStroke.size > 1) {
                                    val encoded = activeStroke.joinToString(";") { pt ->
                                        "${(pt.x / stageW).coerceIn(0f, 1f)},${(pt.y / stageH).coerceIn(0f, 1f)}"
                                    }
                                    onCommitStroke(encoded)
                                }
                                activeStroke.clear()
                            }
                        )
                    }
            ) {
                if (activeStroke.size > 1) {
                    val p = Path().apply {
                        moveTo(activeStroke.first().x, activeStroke.first().y)
                        activeStroke.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = p,
                        color = Color(PhotoRenderEngine.parseColorSafe(brushColorHex, android.graphics.Color.WHITE)),
                        style = Stroke(width = brushSize, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
