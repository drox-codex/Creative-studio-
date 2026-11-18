package com.example.feature.designeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.common.MediaStorageHelper
import com.example.core.common.UiState
import com.example.core.database.CreativeProject
import com.example.core.database.LayerType
import com.example.core.database.ShapeKind
import com.example.core.database.StudioLayer
import com.example.core.database.UserProfile
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
import com.example.feature.photoeditor.PhotoRenderEngine
import kotlin.math.roundToInt

@Composable
fun DesignEditorScreen(
    viewModel: DesignEditorViewModel,
    userProfile: UserProfile,
    defaultExportQuality: Int,
    onNavigateBack: () -> Unit,
    onOpenAiAssistant: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val projectState by viewModel.projectState.collectAsStateWithLifecycle()
    val selectedLayerId by viewModel.selectedLayerId.collectAsStateWithLifecycle()
    val showGrid by viewModel.showGrid.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val lastExported by viewModel.lastExportedFile.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(DesignToolTab.TEXT) }
    var showEditTextDialog by remember { mutableStateOf(false) }
    var textEditDraft by remember { mutableStateOf("") }
    var showExportMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addImageLayer(uri)
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
                    StudioTopBar(title = "Design Studio", onNavigateBack = onNavigateBack)
                    StudioErrorBanner(message = ui.message, modifier = Modifier.padding(StudioSpacing.md))
                }
            }
            is UiState.Success -> {
                val project = ui.data
                val selectedLayer = project.layers.firstOrNull { it.layerId == selectedLayerId }

                Column(modifier = Modifier.fillMaxSize()) {
                    StudioTopBar(
                        title = project.name,
                        subtitle = "${project.canvasWidth} × ${project.canvasHeight}px · ${project.layers.size} layers",
                        onNavigateBack = onNavigateBack,
                        actions = {
                            IconButton(onClick = { viewModel.toggleGrid() }) {
                                Icon(
                                    imageVector = Icons.Rounded.GridOn,
                                    contentDescription = "Toggle guides",
                                    tint = if (showGrid) StudioColors.SubtleHighlight else StudioColors.TextTertiary
                                )
                            }
                            IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = "Undo",
                                    tint = if (canUndo) StudioColors.SubtleHighlight else StudioColors.TextTertiary
                                )
                            }
                            IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                                    contentDescription = "Redo",
                                    tint = if (canRedo) StudioColors.SubtleHighlight else StudioColors.TextTertiary
                                )
                            }
                            IconButton(
                                onClick = { onOpenAiAssistant(project.id, selectedLayerId) },
                                modifier = Modifier.testTag("design_ai_button")
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
                                    contentDescription = "Save design",
                                    tint = StudioColors.MetallicSilver
                                )
                            }
                            if (lastExported != null) {
                                IconButton(
                                    onClick = {
                                        val mime = if (lastExported!!.extension == "pdf") "application/pdf" else "image/png"
                                        MediaStorageHelper.shareFile(context, lastExported!!, mime)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Share export",
                                        tint = StudioColors.SubtleHighlight
                                    )
                                }
                            }
                            Box {
                                StudioPrimaryButton(
                                    text = "Export",
                                    onClick = { showExportMenu = true },
                                    testTag = "design_export_button"
                                )
                                DropdownMenu(
                                    expanded = showExportMenu,
                                    onDismissRequest = { showExportMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Export PNG (High-Res)") },
                                        onClick = {
                                            showExportMenu = false
                                            viewModel.exportDesign("PNG", defaultExportQuality)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export JPEG") },
                                        onClick = {
                                            showExportMenu = false
                                            viewModel.exportDesign("JPEG", defaultExportQuality)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export PDF Document") },
                                        onClick = {
                                            showExportMenu = false
                                            viewModel.exportDesign("PDF", defaultExportQuality)
                                        }
                                    )
                                }
                            }
                        }
                    )

                    // Selected Element Quick Controls Bar
                    if (selectedLayer != null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = StudioSpacing.md, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            if (selectedLayer.type == LayerType.TEXT) {
                                item {
                                    StudioFilterChip(
                                        label = "Edit Text",
                                        selected = false,
                                        onClick = {
                                            textEditDraft = selectedLayer.text
                                            showEditTextDialog = true
                                        }
                                    )
                                }
                                item {
                                    StudioFilterChip(
                                        label = "Silver",
                                        selected = selectedLayer.colorHex.equals("#C4C8CE", true),
                                        onClick = { viewModel.updateSelectedLayer { it.copy(colorHex = "#C4C8CE") } }
                                    )
                                }
                                item {
                                    StudioFilterChip(
                                        label = "White",
                                        selected = selectedLayer.colorHex.equals("#F5F5F5", true),
                                        onClick = { viewModel.updateSelectedLayer { it.copy(colorHex = "#F5F5F5") } }
                                    )
                                }
                                item {
                                    StudioFilterChip(
                                        label = "Shadow",
                                        selected = selectedLayer.textShadow,
                                        onClick = { viewModel.updateSelectedLayer { it.copy(textShadow = !it.textShadow) } }
                                    )
                                }
                            }
                            item {
                                StudioFilterChip(
                                    label = "Center",
                                    icon = Icons.Rounded.CenterFocusStrong,
                                    selected = false,
                                    onClick = { viewModel.alignSelectedCenter() }
                                )
                            }
                            item {
                                StudioFilterChip(
                                    label = "Duplicate",
                                    icon = Icons.Rounded.FileCopy,
                                    selected = false,
                                    onClick = { viewModel.duplicateSelectedLayer() }
                                )
                            }
                            item {
                                StudioFilterChip(
                                    label = if (selectedLayer.locked) "Unlock" else "Lock",
                                    icon = if (selectedLayer.locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                    selected = selectedLayer.locked,
                                    onClick = { viewModel.toggleLockLayer(selectedLayer.layerId) }
                                )
                            }
                            item {
                                StudioFilterChip(
                                    label = "Forward",
                                    selected = false,
                                    onClick = { viewModel.bringSelectedForward() }
                                )
                            }
                            item {
                                StudioFilterChip(
                                    label = "Back",
                                    selected = false,
                                    onClick = { viewModel.sendSelectedBackward() }
                                )
                            }
                            item {
                                StudioFilterChip(
                                    label = "Delete",
                                    icon = Icons.Rounded.DeleteOutline,
                                    selected = false,
                                    onClick = { viewModel.deleteLayer(selectedLayer.layerId) }
                                )
                            }
                        }
                    }

                    // Interactive Design Canvas Stage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(StudioSpacing.md)
                            .clickable { viewModel.selectLayer(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        DesignCanvasStage(
                            project = project,
                            selectedLayerId = selectedLayerId,
                            showGrid = showGrid,
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onMoveLayer = { id, dx, dy -> viewModel.moveLayerBy(id, dx, dy) }
                        )
                        if (isBusy) {
                            CircularProgressIndicator(color = StudioColors.SubtleHighlight)
                        }
                    }

                    // Bottom Contextual Inspector + Tool Tabs
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = StudioSpacing.sm)) {
                            if (selectedLayer != null && activeTab != DesignToolTab.LAYERS) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = StudioSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedLayer.type == LayerType.TEXT) "Size ${selectedLayer.fontSizeSp.roundToInt()}sp"
                                        else "Scale ${(selectedLayer.widthFraction * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioColors.MetallicSilver,
                                        modifier = Modifier.width(84.dp)
                                    )
                                    Slider(
                                        value = if (selectedLayer.type == LayerType.TEXT) selectedLayer.fontSizeSp else selectedLayer.widthFraction,
                                        onValueChange = { v ->
                                            viewModel.updateSelectedLayer {
                                                if (it.type == LayerType.TEXT) it.copy(fontSizeSp = v)
                                                else it.copy(widthFraction = v, heightFraction = (v * 0.8f).coerceIn(0.05f, 0.95f))
                                            }
                                        },
                                        valueRange = if (selectedLayer.type == LayerType.TEXT) 12f..64f else 0.1f..0.95f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = StudioColors.PureWhite,
                                            activeTrackColor = StudioColors.MetallicSilver
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            when (activeTab) {
                                DesignToolTab.TEXT -> {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                                    ) {
                                        items(AssetCatalog.textPresets, key = { it.id }) { preset ->
                                            StudioSecondaryButton(
                                                text = preset.title,
                                                icon = Icons.Rounded.Title,
                                                onClick = {
                                                    viewModel.addTextLayer(
                                                        text = preset.sampleText,
                                                        fontId = preset.fontId,
                                                        fontSizeSp = preset.fontSizeSp,
                                                        colorHex = preset.colorHex
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                DesignToolTab.SHAPES -> {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                                    ) {
                                        items(ShapeKind.entries) { kind ->
                                            StudioSecondaryButton(
                                                text = kind.name.replace('_', ' '),
                                                onClick = { viewModel.addShapeLayer(kind) }
                                            )
                                        }
                                    }
                                }

                                DesignToolTab.ICONS -> {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
                                    ) {
                                        items(AssetCatalog.icons, key = { it.key }) { icon ->
                                            StudioSecondaryButton(
                                                text = icon.label,
                                                icon = icon.vector,
                                                onClick = { viewModel.addIconLayer(icon.key, icon.label) }
                                            )
                                        }
                                    }
                                }

                                DesignToolTab.IMAGES -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                                    ) {
                                        StudioSecondaryButton(
                                            text = "Place Image from Gallery",
                                            icon = Icons.Rounded.AddPhotoAlternate,
                                            onClick = {
                                                imagePicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                DesignToolTab.BACKGROUND -> {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        item {
                                            StudioFilterChip(
                                                label = "Brand Kit (${userProfile.brandName})",
                                                selected = false,
                                                onClick = {
                                                    viewModel.applyBrandKit(
                                                        userProfile.brandPrimaryHex,
                                                        userProfile.brandAccentHex
                                                    )
                                                }
                                            )
                                        }
                                        items(AssetCatalog.backgrounds, key = { it.id }) { bg ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(StudioRadii.full))
                                                    .border(
                                                        1.dp,
                                                        if (project.backgroundColorHex.equals(bg.hex, true)) StudioColors.PureWhite else StudioColors.Border,
                                                        RoundedCornerShape(StudioRadii.full)
                                                    )
                                                    .clickable { viewModel.setCanvasBackground(bg.hex) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(android.graphics.Color.parseColor(bg.hex)))
                                                        .border(1.dp, StudioColors.Border, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(bg.name, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        item {
                                            StudioFilterChip(
                                                label = "1:1",
                                                selected = project.canvasWidth == project.canvasHeight,
                                                onClick = { viewModel.setCanvasDimensions(1080, 1080) }
                                            )
                                        }
                                        item {
                                            StudioFilterChip(
                                                label = "4:5",
                                                selected = project.canvasHeight == 1350,
                                                onClick = { viewModel.setCanvasDimensions(1080, 1350) }
                                            )
                                        }
                                        item {
                                            StudioFilterChip(
                                                label = "16:9",
                                                selected = project.canvasWidth == 1920,
                                                onClick = { viewModel.setCanvasDimensions(1920, 1080) }
                                            )
                                        }
                                    }
                                }

                                DesignToolTab.LAYERS -> {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp),
                                        contentPadding = PaddingValues(horizontal = StudioSpacing.md),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(project.layers.reversed(), key = { it.layerId }) { layer ->
                                            val isSel = layer.layerId == selectedLayerId
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(StudioRadii.sm))
                                                    .background(if (isSel) StudioColors.SurfaceInteractive else StudioColors.BackgroundSecondary)
                                                    .border(
                                                        1.dp,
                                                        if (isSel) StudioColors.MetallicSilver else StudioColors.Border,
                                                        RoundedCornerShape(StudioRadii.sm)
                                                    )
                                                    .clickable { viewModel.selectLayer(layer.layerId) }
                                                    .padding(horizontal = StudioSpacing.sm, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${layer.type.name} · ${layer.name}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = StudioColors.TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.toggleHideLayer(layer.layerId) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (layer.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                        contentDescription = "Toggle visibility",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.toggleLockLayer(layer.layerId) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (layer.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                                        contentDescription = "Toggle lock",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(StudioSpacing.xs))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = StudioSpacing.xs),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                DesignToolTab.entries.forEach { tab ->
                                    val selected = activeTab == tab
                                    val icon = when (tab) {
                                        DesignToolTab.TEXT -> Icons.Rounded.Title
                                        DesignToolTab.SHAPES -> Icons.Rounded.Category
                                        DesignToolTab.ICONS -> Icons.Rounded.AutoAwesome
                                        DesignToolTab.IMAGES -> Icons.Rounded.AddPhotoAlternate
                                        DesignToolTab.BACKGROUND -> Icons.Rounded.Palette
                                        DesignToolTab.LAYERS -> Icons.Rounded.Layers
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(StudioRadii.sm))
                                            .clickable { activeTab = tab }
                                            .padding(horizontal = StudioSpacing.xs, vertical = 6.dp)
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

    if (showEditTextDialog) {
        AlertDialog(
            onDismissRequest = { showEditTextDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Edit Text Element") },
            text = {
                OutlinedTextField(
                    value = textEditDraft,
                    onValueChange = { textEditDraft = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateSelectedLayer { it.copy(text = textEditDraft, name = textEditDraft.take(18)) }
                        showEditTextDialog = false
                    }
                ) { Text("Apply", color = StudioColors.SubtleHighlight) }
            },
            dismissButton = {
                TextButton(onClick = { showEditTextDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DesignCanvasStage(
    project: CreativeProject,
    selectedLayerId: String?,
    showGrid: Boolean,
    onSelectLayer: (String?) -> Unit,
    onMoveLayer: (String, Float, Float) -> Unit
) {
    val bgColor = runCatching { Color(android.graphics.Color.parseColor(project.backgroundColorHex)) }
        .getOrDefault(StudioColors.BackgroundPrimary)
    val aspect = (project.canvasWidth.toFloat() / project.canvasHeight.coerceAtLeast(1)).coerceIn(0.45f, 2.1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(StudioRadii.sm))
            .background(bgColor)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(StudioRadii.sm))
    ) {
        val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val stageH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                val gridColor = Color(0x28C4C8CE)
                drawLine(gridColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), pathEffect = dash)
                drawLine(gridColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), pathEffect = dash)
            }
        }

        project.layers.filterNot { it.hidden }.forEach { layer ->
            DesignLayerView(
                layer = layer,
                isSelected = layer.layerId == selectedLayerId,
                stageW = stageW,
                stageH = stageH,
                maxWidthDp = maxWidth,
                maxHeightDp = maxHeight,
                onSelect = { onSelectLayer(layer.layerId) },
                onMove = { dx, dy -> onMoveLayer(layer.layerId, dx, dy) }
            )
        }
    }
}

@Composable
private fun DesignLayerView(
    layer: StudioLayer,
    isSelected: Boolean,
    stageW: Float,
    stageH: Float,
    maxWidthDp: androidx.compose.ui.unit.Dp,
    maxHeightDp: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit
) {
    val xOff = ((layer.x - 0.5f) * stageW).roundToInt()
    val yOff = ((layer.y - 0.5f) * stageH).roundToInt()
    val boxW = (maxWidthDp * layer.widthFraction).coerceAtLeast(24.dp)
    val boxH = (maxHeightDp * layer.heightFraction).coerceAtLeast(12.dp)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(xOff, yOff) }
                .size(width = boxW, height = boxH)
                .alpha(layer.opacity)
                .graphicsLayer { rotationZ = layer.rotation }
                .then(
                    if (isSelected) Modifier.border(1.5.dp, StudioColors.PureWhite, RoundedCornerShape(4.dp))
                    else Modifier
                )
                .pointerInput(layer.layerId, layer.locked) {
                    if (!layer.locked) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, drag ->
                                change.consume()
                                onMove(drag.x / stageW, drag.y / stageH)
                            }
                        )
                    }
                }
                .clickable { onSelect() },
            contentAlignment = Alignment.Center
        ) {
            when (layer.type) {
                LayerType.SHAPE -> {
                    val fill = Color(PhotoRenderEngine.parseColorSafe(layer.colorHex, android.graphics.Color.DKGRAY))
                    val stroke = Color(PhotoRenderEngine.parseColorSafe(layer.strokeColorHex, android.graphics.Color.LTGRAY))
                    val shape = when (layer.shapeKind) {
                        ShapeKind.CIRCLE -> CircleShape
                        ShapeKind.ROUNDED_RECT, ShapeKind.BADGE -> RoundedCornerShape(layer.cornerRadiusDp.dp)
                        else -> RoundedCornerShape(2.dp)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .background(fill)
                            .then(
                                if (layer.strokeWidthDp > 0f) Modifier.border(layer.strokeWidthDp.dp, stroke, shape)
                                else Modifier
                            )
                    )
                }

                LayerType.TEXT -> {
                    Text(
                        text = layer.text,
                        fontFamily = StudioFontOption.fromId(layer.fontId).family,
                        fontSize = (layer.fontSizeSp * 0.72f).sp,
                        fontWeight = if (layer.bold) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = layer.letterSpacingSp.sp,
                        textAlign = when (layer.textAlign) {
                            "LEFT" -> TextAlign.Start
                            "RIGHT" -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        color = Color(PhotoRenderEngine.parseColorSafe(layer.colorHex, android.graphics.Color.WHITE)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LayerType.ICON -> {
                    Icon(
                        imageVector = AssetCatalog.resolveIcon(layer.iconKey),
                        contentDescription = layer.name,
                        tint = Color(PhotoRenderEngine.parseColorSafe(layer.colorHex, android.graphics.Color.WHITE)),
                        modifier = Modifier.fillMaxSize(0.8f)
                    )
                }

                LayerType.IMAGE -> {
                    AsyncImage(
                        model = layer.imageUri,
                        contentDescription = layer.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                    )
                }

                LayerType.DRAWING -> {}
            }
        }
    }
}
