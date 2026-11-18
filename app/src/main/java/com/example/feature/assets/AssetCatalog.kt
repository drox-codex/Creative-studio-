package com.example.feature.assets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.core.database.CreativeProject
import com.example.core.database.LayerType
import com.example.core.database.PhotoAdjustments
import com.example.core.database.ProjectType
import com.example.core.database.ShapeKind
import com.example.core.database.StudioLayer
import com.example.core.database.TransitionKind
import java.util.UUID

enum class AssetCategory(val label: String) {
    TEMPLATES("Templates"),
    ICONS("Icons"),
    SHAPES("Shapes"),
    FONTS("Fonts"),
    FILTERS("Filters"),
    TEXT_PRESETS("Text"),
    BACKGROUNDS("Backdrops"),
    TRANSITIONS("Transitions")
}

data class DesignTemplateAsset(
    val id: String,
    val title: String,
    val categoryTag: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val backgroundColorHex: String,
    val layers: List<StudioLayer>
) {
    fun toProject(customName: String? = null): CreativeProject = CreativeProject(
        name = customName?.takeIf { it.isNotBlank() } ?: title,
        projectType = ProjectType.DESIGN,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        backgroundColorHex = backgroundColorHex,
        thumbnailHint = "template:$id",
        layers = layers.map { it.copy(layerId = UUID.randomUUID().toString().take(8)) }
    )
}

data class FilterPresetAsset(
    val id: String,
    val name: String,
    val description: String,
    val adjustments: PhotoAdjustments
)

data class IconAsset(
    val key: String,
    val label: String,
    val vector: ImageVector
)

data class TextPresetAsset(
    val id: String,
    val title: String,
    val sampleText: String,
    val fontId: String,
    val fontSizeSp: Float,
    val letterSpacingSp: Float,
    val bold: Boolean,
    val colorHex: String
)

data class BackgroundAsset(
    val id: String,
    val name: String,
    val hex: String
)

object AssetCatalog {

    val icons: List<IconAsset> = listOf(
        IconAsset("sparkles", "Spark", Icons.Rounded.AutoAwesome),
        IconAsset("aperture", "Aperture", Icons.Rounded.Camera),
        IconAsset("layers", "Layers", Icons.Rounded.Layers),
        IconAsset("star", "Star", Icons.Rounded.Star),
        IconAsset("arrow_forward", "Arrow", Icons.AutoMirrored.Rounded.ArrowForward),
        IconAsset("bolt", "Bolt", Icons.Rounded.Bolt),
        IconAsset("check_circle", "Check", Icons.Rounded.CheckCircle),
        IconAsset("diamond", "Diamond", Icons.Rounded.Diamond),
        IconAsset("grid", "Grid", Icons.Rounded.GridView),
        IconAsset("verified", "Verified", Icons.Rounded.Verified)
    )

    fun resolveIcon(key: String): ImageVector =
        icons.firstOrNull { it.key == key }?.vector ?: Icons.Rounded.AutoAwesome

    val filterPresets: List<FilterPresetAsset> = listOf(
        FilterPresetAsset(
            id = "original",
            name = "Original",
            description = "Unprocessed neutral tone",
            adjustments = PhotoAdjustments(filterPreset = "Original")
        ),
        FilterPresetAsset(
            id = "silver_gelatin",
            name = "Silver Gelatin",
            description = "High-contrast darkroom monochrome",
            adjustments = PhotoAdjustments(
                contrast = 1.24f,
                saturation = 0f,
                brightness = 4f,
                sharpness = 0.25f,
                vignette = 0.28f,
                filterPreset = "Silver Gelatin"
            )
        ),
        FilterPresetAsset(
            id = "noir",
            name = "Noir",
            description = "Deep obsidian shadows and crisp highlights",
            adjustments = PhotoAdjustments(
                contrast = 1.38f,
                saturation = 0f,
                brightness = -8f,
                exposure = -0.1f,
                vignette = 0.45f,
                filterPreset = "Noir"
            )
        ),
        FilterPresetAsset(
            id = "matte_slate",
            name = "Matte Slate",
            description = "Cool desaturated editorial look",
            adjustments = PhotoAdjustments(
                contrast = 0.94f,
                saturation = 0.65f,
                temperature = -14f,
                shadows = 18f,
                filterPreset = "Matte Slate"
            )
        ),
        FilterPresetAsset(
            id = "warm_film",
            name = "Warm Film",
            description = "Subtle golden warmth and soft contrast",
            adjustments = PhotoAdjustments(
                contrast = 1.08f,
                saturation = 1.12f,
                temperature = 18f,
                highlights = -12f,
                vignette = 0.2f,
                filterPreset = "Warm Film"
            )
        ),
        FilterPresetAsset(
            id = "crisp_studio",
            name = "Crisp Studio",
            description = "Commercial clarity and punchy detail",
            adjustments = PhotoAdjustments(
                contrast = 1.18f,
                saturation = 1.06f,
                sharpness = 0.45f,
                exposure = 0.08f,
                filterPreset = "Crisp Studio"
            )
        ),
        FilterPresetAsset(
            id = "fade",
            name = "Fade",
            description = "Lifted blacks with restrained midtones",
            adjustments = PhotoAdjustments(
                brightness = 10f,
                contrast = 0.85f,
                saturation = 0.78f,
                shadows = 25f,
                filterPreset = "Fade"
            )
        )
    )

    val textPresets: List<TextPresetAsset> = listOf(
        TextPresetAsset(
            id = "editorial_headline",
            title = "Editorial Display",
            sampleText = "DESIGN SYSTEM",
            fontId = "plus_jakarta",
            fontSizeSp = 32f,
            letterSpacingSp = 2.0f,
            bold = true,
            colorHex = "#F5F5F5"
        ),
        TextPresetAsset(
            id = "serif_title",
            title = "Gallery Serif",
            sampleText = "Monograph 04",
            fontId = "playfair",
            fontSizeSp = 30f,
            letterSpacingSp = 0.5f,
            bold = false,
            colorHex = "#E1E4E8"
        ),
        TextPresetAsset(
            id = "technical_spec",
            title = "Technical Mono",
            sampleText = "ARCHIVE // 2026",
            fontId = "jetbrains_mono",
            fontSizeSp = 15f,
            letterSpacingSp = 2.5f,
            bold = true,
            colorHex = "#C4C8CE"
        ),
        TextPresetAsset(
            id = "grotesk_statement",
            title = "Modern Grotesk",
            sampleText = "FUTURE CRAFT",
            fontId = "space_grotesk",
            fontSizeSp = 28f,
            letterSpacingSp = 1.2f,
            bold = true,
            colorHex = "#FFFFFF"
        )
    )

    val backgrounds: List<BackgroundAsset> = listOf(
        BackgroundAsset("obsidian", "Obsidian", "#090A0C"),
        BackgroundAsset("carbon", "Carbon", "#111317"),
        BackgroundAsset("graphite", "Graphite", "#191C21"),
        BackgroundAsset("slate", "Deep Slate", "#1A1E24"),
        BackgroundAsset("warm_charcoal", "Charcoal", "#1D1B1A"),
        BackgroundAsset("silver_mist", "Silver Mist", "#C4C8CE"),
        BackgroundAsset("ivory", "Gallery White", "#F5F5F5")
    )

    val templates: List<DesignTemplateAsset> = listOf(
        DesignTemplateAsset(
            id = "modern_poster",
            title = "Modern Poster",
            categoryTag = "Poster · 4:5",
            canvasWidth = 1080,
            canvasHeight = 1350,
            backgroundColorHex = "#090A0C",
            layers = listOf(
                StudioLayer(
                    layerId = "bg_frame",
                    name = "Outer Frame",
                    type = LayerType.SHAPE,
                    x = 0.5f,
                    y = 0.5f,
                    widthFraction = 0.88f,
                    heightFraction = 0.90f,
                    colorHex = "#111317",
                    strokeColorHex = "#292D33",
                    strokeWidthDp = 2f,
                    cornerRadiusDp = 12f,
                    shapeKind = ShapeKind.ROUNDED_RECT
                ),
                StudioLayer(
                    layerId = "tag_line",
                    name = "Edition Tag",
                    type = LayerType.TEXT,
                    x = 0.5f,
                    y = 0.16f,
                    widthFraction = 0.7f,
                    heightFraction = 0.08f,
                    text = "EXHIBITION // 01",
                    fontId = "jetbrains_mono",
                    fontSizeSp = 13f,
                    letterSpacingSp = 3f,
                    colorHex = "#9298A1"
                ),
                StudioLayer(
                    layerId = "main_headline",
                    name = "Headline",
                    type = LayerType.TEXT,
                    x = 0.5f,
                    y = 0.44f,
                    widthFraction = 0.8f,
                    heightFraction = 0.24f,
                    text = "DREAM\nBIGGER",
                    fontId = "plus_jakarta",
                    fontSizeSp = 40f,
                    letterSpacingSp = 1.5f,
                    bold = true,
                    colorHex = "#F5F5F5"
                ),
                StudioLayer(
                    layerId = "divider",
                    name = "Silver Rule",
                    type = LayerType.SHAPE,
                    x = 0.5f,
                    y = 0.60f,
                    widthFraction = 0.28f,
                    heightFraction = 0.01f,
                    colorHex = "#C4C8CE",
                    shapeKind = ShapeKind.LINE
                ),
                StudioLayer(
                    layerId = "sub_copy",
                    name = "Subheadline",
                    type = LayerType.TEXT,
                    x = 0.5f,
                    y = 0.68f,
                    widthFraction = 0.72f,
                    heightFraction = 0.08f,
                    text = "Create Your Future",
                    fontId = "inter",
                    fontSizeSp = 16f,
                    letterSpacingSp = 0.6f,
                    colorHex = "#C4C8CE"
                )
            )
        ),
        DesignTemplateAsset(
            id = "minimal_social",
            title = "Social Square",
            categoryTag = "Social · 1:1",
            canvasWidth = 1080,
            canvasHeight = 1080,
            backgroundColorHex = "#111317",
            layers = listOf(
                StudioLayer(
                    layerId = "accent_circle",
                    name = "Studio Orb",
                    type = LayerType.SHAPE,
                    x = 0.5f,
                    y = 0.34f,
                    widthFraction = 0.34f,
                    heightFraction = 0.34f,
                    colorHex = "#191C21",
                    strokeColorHex = "#C4C8CE",
                    strokeWidthDp = 2f,
                    shapeKind = ShapeKind.CIRCLE
                ),
                StudioLayer(
                    layerId = "icon_center",
                    name = "Aperture Mark",
                    type = LayerType.ICON,
                    x = 0.5f,
                    y = 0.34f,
                    widthFraction = 0.14f,
                    heightFraction = 0.14f,
                    colorHex = "#E1E4E8",
                    iconKey = "aperture"
                ),
                StudioLayer(
                    layerId = "social_title",
                    name = "Title",
                    type = LayerType.TEXT,
                    x = 0.5f,
                    y = 0.64f,
                    widthFraction = 0.8f,
                    heightFraction = 0.14f,
                    text = "STUDIO EDITION",
                    fontId = "space_grotesk",
                    fontSizeSp = 28f,
                    letterSpacingSp = 2.4f,
                    bold = true,
                    colorHex = "#F5F5F5"
                ),
                StudioLayer(
                    layerId = "social_sub",
                    name = "Caption",
                    type = LayerType.TEXT,
                    x = 0.5f,
                    y = 0.76f,
                    widthFraction = 0.7f,
                    heightFraction = 0.08f,
                    text = "Precision tools for modern creators",
                    fontId = "inter",
                    fontSizeSp = 14f,
                    colorHex = "#9298A1"
                )
            )
        ),
        DesignTemplateAsset(
            id = "business_card",
            title = "Business Card",
            categoryTag = "Print · 7:4",
            canvasWidth = 1050,
            canvasHeight = 600,
            backgroundColorHex = "#090A0C",
            layers = listOf(
                StudioLayer(
                    layerId = "card_border",
                    name = "Card Outline",
                    type = LayerType.SHAPE,
                    x = 0.5f,
                    y = 0.5f,
                    widthFraction = 0.92f,
                    heightFraction = 0.86f,
                    colorHex = "#111317",
                    strokeColorHex = "#292D33",
                    strokeWidthDp = 1.5f,
                    cornerRadiusDp = 10f,
                    shapeKind = ShapeKind.ROUNDED_RECT
                ),
                StudioLayer(
                    layerId = "card_name",
                    name = "Name",
                    type = LayerType.TEXT,
                    x = 0.35f,
                    y = 0.38f,
                    widthFraction = 0.56f,
                    heightFraction = 0.16f,
                    text = "ALEXANDER VANCE",
                    fontId = "plus_jakarta",
                    fontSizeSp = 22f,
                    letterSpacingSp = 1.8f,
                    bold = true,
                    textAlign = "LEFT",
                    colorHex = "#F5F5F5"
                ),
                StudioLayer(
                    layerId = "card_role",
                    name = "Role",
                    type = LayerType.TEXT,
                    x = 0.35f,
                    y = 0.52f,
                    widthFraction = 0.56f,
                    heightFraction = 0.1f,
                    text = "CREATIVE DIRECTOR",
                    fontId = "jetbrains_mono",
                    fontSizeSp = 12f,
                    letterSpacingSp = 2f,
                    textAlign = "LEFT",
                    colorHex = "#9298A1"
                ),
                StudioLayer(
                    layerId = "card_contact",
                    name = "Contact",
                    type = LayerType.TEXT,
                    x = 0.35f,
                    y = 0.74f,
                    widthFraction = 0.56f,
                    heightFraction = 0.1f,
                    text = "studio@vance.design",
                    fontId = "jetbrains_mono",
                    fontSizeSp = 11f,
                    textAlign = "LEFT",
                    colorHex = "#C4C8CE"
                )
            )
        ),
        DesignTemplateAsset(
            id = "presentation_slide",
            title = "Keynote Deck",
            categoryTag = "Presentation · 16:9",
            canvasWidth = 1920,
            canvasHeight = 1080,
            backgroundColorHex = "#090A0C",
            layers = listOf(
                StudioLayer(
                    layerId = "slide_badge",
                    name = "Section Badge",
                    type = LayerType.SHAPE,
                    x = 0.24f,
                    y = 0.26f,
                    widthFraction = 0.22f,
                    heightFraction = 0.08f,
                    colorHex = "#191C21",
                    strokeColorHex = "#292D33",
                    strokeWidthDp = 1f,
                    cornerRadiusDp = 24f,
                    shapeKind = ShapeKind.BADGE
                ),
                StudioLayer(
                    layerId = "slide_kicker",
                    name = "Kicker",
                    type = LayerType.TEXT,
                    x = 0.24f,
                    y = 0.26f,
                    widthFraction = 0.20f,
                    heightFraction = 0.06f,
                    text = "Q4 STRATEGY",
                    fontId = "jetbrains_mono",
                    fontSizeSp = 11f,
                    letterSpacingSp = 1.6f,
                    colorHex = "#C4C8CE"
                ),
                StudioLayer(
                    layerId = "slide_title",
                    name = "Slide Title",
                    type = LayerType.TEXT,
                    x = 0.45f,
                    y = 0.50f,
                    widthFraction = 0.72f,
                    heightFraction = 0.26f,
                    text = "Visual Architecture\n& Brand Systems",
                    fontId = "playfair",
                    fontSizeSp = 32f,
                    textAlign = "LEFT",
                    colorHex = "#F5F5F5"
                ),
                StudioLayer(
                    layerId = "slide_footer",
                    name = "Slide Number",
                    type = LayerType.TEXT,
                    x = 0.84f,
                    y = 0.86f,
                    widthFraction = 0.16f,
                    heightFraction = 0.06f,
                    text = "01 / 12",
                    fontId = "jetbrains_mono",
                    fontSizeSp = 12f,
                    textAlign = "RIGHT",
                    colorHex = "#9298A1"
                )
            )
        ),
        DesignTemplateAsset(
            id = "video_thumbnail",
            title = "Studio Thumbnail",
            categoryTag = "Thumbnail · 16:9",
            canvasWidth = 1280,
            canvasHeight = 720,
            backgroundColorHex = "#191C21",
            layers = listOf(
                StudioLayer(
                    layerId = "thumb_pill",
                    name = "Highlight Bar",
                    type = LayerType.SHAPE,
                    x = 0.5f,
                    y = 0.36f,
                    widthFraction = 0.56f,
                    heightFraction = 0.18f,
                    colorHex = "#F5F5F5",
                    cornerRadiusDp = 8f,
                    shapeKind = ShapeKind.ROUNDED_RECT
                ),
                StudioLayer(
                    layerId = "thumb_title",
                    name = "Title",
                    type = LayerType.TEXT,
                    x = 0.5f,
                    y = 0.36f,
                    widthFraction = 0.54f,
                    heightFraction = 0.14f,
                    text = "PRO WORKFLOW",
                    fontId = "space_grotesk",
                    fontSizeSp = 26f,
                    letterSpacingSp = 1.4f,
                    bold = true,
                    colorHex = "#090A0C"
                ),
                StudioLayer(
                    layerId = "thumb_sub",
                    name = "Subtitle",
                    type = LayerType.TEXT,
                    x = 0.5f,
                    y = 0.58f,
                    widthFraction = 0.65f,
                    heightFraction = 0.1f,
                    text = "Color Grading & Typography Breakdown",
                    fontId = "inter",
                    fontSizeSp = 15f,
                    colorHex = "#E1E4E8"
                )
            )
        )
    )

    val transitions: List<Pair<TransitionKind, String>> = listOf(
        TransitionKind.NONE to "Hard Cut — Instant frame transition",
        TransitionKind.CROSSFADE to "Crossfade — Smooth opacity dissolve",
        TransitionKind.DIP_BLACK to "Dip to Black — Brief obsidian fade",
        TransitionKind.SLIDE_LEFT to "Slide Left — Directional editorial push"
    )
}
