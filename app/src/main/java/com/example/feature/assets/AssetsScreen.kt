package com.example.feature.assets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.database.ShapeKind
import com.example.core.designsystem.StudioColors
import com.example.core.designsystem.StudioFilterChip
import com.example.core.designsystem.StudioFontOption
import com.example.core.designsystem.StudioPrimaryButton
import com.example.core.designsystem.StudioRadii
import com.example.core.designsystem.StudioSpacing
import com.example.core.designsystem.StudioSurfaceCard
import com.example.core.designsystem.StudioTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    onUseTemplate: (DesignTemplateAsset) -> Unit,
    onCreatePhotoWithFilter: (FilterPresetAsset) -> Unit,
    onCreateDesignWithBackground: (BackgroundAsset) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AssetCategory.TEMPLATES) }
    var previewTemplate by remember { mutableStateOf<DesignTemplateAsset?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        StudioTopBar(
            title = stringResource(R.string.assets_title),
            subtitle = "Templates, typography, vectors & presets"
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    "Search assets…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search assets",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(StudioRadii.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = StudioColors.MetallicSilver,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = StudioSpacing.md, vertical = StudioSpacing.xs)
                .testTag("assets_search_input")
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = StudioSpacing.md, vertical = StudioSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.xs)
        ) {
            items(AssetCategory.entries) { category ->
                StudioFilterChip(
                    label = category.label,
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category }
                )
            }
        }

        val query = searchQuery.trim().lowercase()

        when (selectedCategory) {
            AssetCategory.TEMPLATES -> {
                val filtered = AssetCatalog.templates.filter {
                    query.isEmpty() || it.title.lowercase().contains(query) || it.categoryTag.lowercase().contains(query)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp),
                    contentPadding = PaddingValues(StudioSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { tpl ->
                        TemplatePreviewCard(
                            template = tpl,
                            onClick = { previewTemplate = tpl }
                        )
                    }
                }
            }

            AssetCategory.ICONS -> {
                val filtered = AssetCatalog.icons.filter {
                    query.isEmpty() || it.label.lowercase().contains(query)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    contentPadding = PaddingValues(StudioSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.key }) { asset ->
                        StudioSurfaceCard(
                            onClick = {
                                previewTemplate = AssetCatalog.templates.first()
                            },
                            contentPadding = PaddingValues(StudioSpacing.md)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = asset.vector,
                                    contentDescription = asset.label,
                                    tint = StudioColors.SubtleHighlight,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(StudioSpacing.xs))
                                Text(
                                    text = asset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            AssetCategory.SHAPES -> {
                val shapes = ShapeKind.entries.filter {
                    query.isEmpty() || it.name.lowercase().contains(query)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(StudioSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shapes) { shape ->
                        StudioSurfaceCard(contentPadding = PaddingValues(StudioSpacing.md)) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(
                                            when (shape) {
                                                ShapeKind.CIRCLE -> CircleShape
                                                ShapeKind.ROUNDED_RECT, ShapeKind.BADGE -> RoundedCornerShape(10.dp)
                                                else -> RoundedCornerShape(2.dp)
                                            }
                                        )
                                        .background(StudioColors.SurfaceInteractive)
                                        .border(
                                            1.dp,
                                            StudioColors.MetallicSilver,
                                            when (shape) {
                                                ShapeKind.CIRCLE -> CircleShape
                                                ShapeKind.ROUNDED_RECT, ShapeKind.BADGE -> RoundedCornerShape(10.dp)
                                                else -> RoundedCornerShape(2.dp)
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.height(StudioSpacing.xs))
                                Text(
                                    text = shape.name.replace('_', ' '),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            AssetCategory.FONTS -> {
                val fonts = StudioFontOption.entries.filter {
                    query.isEmpty() || it.displayName.lowercase().contains(query)
                }
                LazyColumn(
                    contentPadding = PaddingValues(StudioSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                ) {
                    items(fonts) { font ->
                        StudioSurfaceCard {
                            Text(
                                text = font.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioColors.MetallicSilver
                            )
                            Spacer(modifier = Modifier.height(StudioSpacing.xs))
                            Text(
                                text = "The quick obsidian studio crafts precision.",
                                fontFamily = font.family,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            AssetCategory.FILTERS -> {
                val filters = AssetCatalog.filterPresets.filter {
                    query.isEmpty() || it.name.lowercase().contains(query)
                }
                LazyColumn(
                    contentPadding = PaddingValues(StudioSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                ) {
                    items(filters, key = { it.id }) { filter ->
                        StudioSurfaceCard(onClick = { onCreatePhotoWithFilter(filter) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = filter.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = filter.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Open Photo",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StudioColors.MetallicSilver
                                )
                            }
                        }
                    }
                }
            }

            AssetCategory.TEXT_PRESETS -> {
                val presets = AssetCatalog.textPresets.filter {
                    query.isEmpty() || it.title.lowercase().contains(query) || it.sampleText.lowercase().contains(query)
                }
                LazyColumn(
                    contentPadding = PaddingValues(StudioSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                ) {
                    items(presets, key = { it.id }) { preset ->
                        val fontOption = StudioFontOption.fromId(preset.fontId)
                        StudioSurfaceCard {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(StudioSpacing.xs))
                            Text(
                                text = preset.sampleText,
                                fontFamily = fontOption.family,
                                fontSize = 22.sp,
                                fontWeight = if (preset.bold) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = preset.letterSpacingSp.sp,
                                color = Color(android.graphics.Color.parseColor(preset.colorHex))
                            )
                        }
                    }
                }
            }

            AssetCategory.BACKGROUNDS -> {
                val bgs = AssetCatalog.backgrounds.filter {
                    query.isEmpty() || it.name.lowercase().contains(query)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(StudioSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(bgs, key = { it.id }) { bg ->
                        StudioSurfaceCard(onClick = { onCreateDesignWithBackground(bg) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(76.dp)
                                    .clip(RoundedCornerShape(StudioRadii.sm))
                                    .background(Color(android.graphics.Color.parseColor(bg.hex)))
                                    .border(1.dp, StudioColors.Border, RoundedCornerShape(StudioRadii.sm))
                            )
                            Spacer(modifier = Modifier.height(StudioSpacing.xs))
                            Text(
                                text = bg.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = bg.hex,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AssetCategory.TRANSITIONS -> {
                LazyColumn(
                    contentPadding = PaddingValues(StudioSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(StudioSpacing.sm)
                ) {
                    items(AssetCatalog.transitions) { (kind, desc) ->
                        StudioSurfaceCard {
                            Text(
                                text = kind.name.replace('_', ' '),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (previewTemplate != null) {
        val tpl = previewTemplate!!
        ModalBottomSheet(
            onDismissRequest = { previewTemplate = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StudioSpacing.lg, vertical = StudioSpacing.md)
            ) {
                Text(
                    text = tpl.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${tpl.categoryTag} · ${tpl.canvasWidth} × ${tpl.canvasHeight}px · ${tpl.layers.size} layers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(StudioSpacing.md))
                TemplateThumbnailBox(
                    template = tpl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )
                Spacer(modifier = Modifier.height(StudioSpacing.lg))
                StudioPrimaryButton(
                    text = "Use Template in Design Studio",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = {
                        previewTemplate = null
                        onUseTemplate(tpl)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(StudioSpacing.lg))
            }
        }
    }
}

@Composable
fun TemplatePreviewCard(
    template: DesignTemplateAsset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudioSurfaceCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(StudioSpacing.sm)
    ) {
        TemplateThumbnailBox(
            template = template,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.height(StudioSpacing.xs))
        Text(
            text = template.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = template.categoryTag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun TemplateThumbnailBox(
    template: DesignTemplateAsset,
    modifier: Modifier = Modifier
) {
    val bgColor = runCatching { Color(android.graphics.Color.parseColor(template.backgroundColorHex)) }
        .getOrDefault(StudioColors.BackgroundPrimary)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(StudioRadii.sm),
        color = bgColor,
        border = BorderStroke(1.dp, StudioColors.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(StudioSpacing.sm),
            contentAlignment = Alignment.Center
        ) {
            val primaryText = template.layers.firstOrNull { it.type == com.example.core.database.LayerType.TEXT && it.fontSizeSp >= 20f }
                ?: template.layers.firstOrNull { it.type == com.example.core.database.LayerType.TEXT }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (primaryText != null) {
                    Text(
                        text = primaryText.text.replace('\n', ' '),
                        fontFamily = StudioFontOption.fromId(primaryText.fontId).family,
                        fontSize = 13.sp,
                        fontWeight = if (primaryText.bold) FontWeight.Bold else FontWeight.Medium,
                        color = runCatching { Color(android.graphics.Color.parseColor(primaryText.colorHex)) }
                            .getOrDefault(StudioColors.TextPrimary),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${template.canvasWidth}×${template.canvasHeight}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextSecondary
                )
            }
        }
    }
}
