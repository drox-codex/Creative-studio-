package com.example.core.database

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ProjectType {
    PHOTO,
    DESIGN,
    VIDEO;

    companion object {
        fun safeFrom(raw: String?): ProjectType =
            entries.firstOrNull { it.name == raw } ?: DESIGN
    }
}

enum class LayerType {
    TEXT,
    SHAPE,
    ICON,
    IMAGE,
    DRAWING;

    companion object {
        fun safeFrom(raw: String?): LayerType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class ShapeKind {
    RECTANGLE,
    ROUNDED_RECT,
    CIRCLE,
    LINE,
    STAR,
    BADGE;

    companion object {
        fun safeFrom(raw: String?): ShapeKind =
            entries.firstOrNull { it.name == raw } ?: RECTANGLE
    }
}

enum class MaskKind {
    NONE,
    CIRCLE,
    ROUNDED_RECT,
    VIGNETTE;

    companion object {
        fun safeFrom(raw: String?): MaskKind =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class TransitionKind {
    NONE,
    CROSSFADE,
    DIP_BLACK,
    SLIDE_LEFT;

    companion object {
        fun safeFrom(raw: String?): TransitionKind =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

@Keep
data class PhotoAdjustments(
    val brightness: Float = 0f,      // -100f..100f
    val contrast: Float = 1f,        // 0.5f..1.8f
    val saturation: Float = 1f,      // 0f..2f
    val temperature: Float = 0f,     // -50f..50f
    val exposure: Float = 0f,        // -1f..1f
    val highlights: Float = 0f,      // -100f..100f
    val shadows: Float = 0f,         // -100f..100f
    val sharpness: Float = 0f,       // 0f..1f
    val vignette: Float = 0f,        // 0f..1f
    val filterPreset: String = "Original",
    val rotationDegrees: Float = 0f, // 0, 90, 180, 270 + straighten
    val straightenDegrees: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val cropAspect: String = "Free", // Free, 1:1, 4:5, 16:9, 3:2, 9:16
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
) {
    fun toMap(): Map<String, Any> = mapOf(
        "brightness" to brightness.toDouble(),
        "contrast" to contrast.toDouble(),
        "saturation" to saturation.toDouble(),
        "temperature" to temperature.toDouble(),
        "exposure" to exposure.toDouble(),
        "highlights" to highlights.toDouble(),
        "shadows" to shadows.toDouble(),
        "sharpness" to sharpness.toDouble(),
        "vignette" to vignette.toDouble(),
        "filterPreset" to filterPreset,
        "rotationDegrees" to rotationDegrees.toDouble(),
        "straightenDegrees" to straightenDegrees.toDouble(),
        "flipH" to flipH,
        "flipV" to flipV,
        "cropAspect" to cropAspect,
        "cropLeft" to cropLeft.toDouble(),
        "cropTop" to cropTop.toDouble(),
        "cropRight" to cropRight.toDouble(),
        "cropBottom" to cropBottom.toDouble()
    )

    companion object {
        fun fromMap(map: Map<String, Any?>?): PhotoAdjustments {
            if (map == null) return PhotoAdjustments()
            return PhotoAdjustments(
                brightness = (map["brightness"] as? Number)?.toFloat() ?: 0f,
                contrast = (map["contrast"] as? Number)?.toFloat() ?: 1f,
                saturation = (map["saturation"] as? Number)?.toFloat() ?: 1f,
                temperature = (map["temperature"] as? Number)?.toFloat() ?: 0f,
                exposure = (map["exposure"] as? Number)?.toFloat() ?: 0f,
                highlights = (map["highlights"] as? Number)?.toFloat() ?: 0f,
                shadows = (map["shadows"] as? Number)?.toFloat() ?: 0f,
                sharpness = (map["sharpness"] as? Number)?.toFloat() ?: 0f,
                vignette = (map["vignette"] as? Number)?.toFloat() ?: 0f,
                filterPreset = (map["filterPreset"] as? String) ?: "Original",
                rotationDegrees = (map["rotationDegrees"] as? Number)?.toFloat() ?: 0f,
                straightenDegrees = (map["straightenDegrees"] as? Number)?.toFloat() ?: 0f,
                flipH = (map["flipH"] as? Boolean) ?: false,
                flipV = (map["flipV"] as? Boolean) ?: false,
                cropAspect = (map["cropAspect"] as? String) ?: "Free",
                cropLeft = (map["cropLeft"] as? Number)?.toFloat() ?: 0f,
                cropTop = (map["cropTop"] as? Number)?.toFloat() ?: 0f,
                cropRight = (map["cropRight"] as? Number)?.toFloat() ?: 1f,
                cropBottom = (map["cropBottom"] as? Number)?.toFloat() ?: 1f
            )
        }
    }
}

@Keep
data class StudioLayer(
    val layerId: String = "",
    val name: String = "Layer",
    val type: LayerType = LayerType.TEXT,
    val x: Float = 0.5f,          // Normalized 0..1 center X
    val y: Float = 0.5f,          // Normalized 0..1 center Y
    val widthFraction: Float = 0.5f,
    val heightFraction: Float = 0.2f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val locked: Boolean = false,
    val hidden: Boolean = false,
    val text: String = "",
    val fontId: String = "plus_jakarta",
    val fontSizeSp: Float = 26f,
    val letterSpacingSp: Float = 0f,
    val bold: Boolean = false,
    val textAlign: String = "CENTER", // LEFT, CENTER, RIGHT
    val textShadow: Boolean = false,
    val colorHex: String = "#F5F5F5",
    val strokeColorHex: String = "#C4C8CE",
    val strokeWidthDp: Float = 0f,
    val cornerRadiusDp: Float = 8f,
    val shapeKind: ShapeKind = ShapeKind.RECTANGLE,
    val iconKey: String = "sparkles",
    val imageUri: String = "",
    val maskKind: MaskKind = MaskKind.NONE,
    val pathPoints: String = "" // Normalized "x,y;x,y|..." for freehand drawing strokes
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("layerId", layerId)
        put("name", name)
        put("type", type.name)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("widthFraction", widthFraction.toDouble())
        put("heightFraction", heightFraction.toDouble())
        put("rotation", rotation.toDouble())
        put("opacity", opacity.toDouble())
        put("locked", locked)
        put("hidden", hidden)
        if (text.isNotEmpty()) put("text", text)
        put("fontId", fontId)
        put("fontSizeSp", fontSizeSp.toDouble())
        put("letterSpacingSp", letterSpacingSp.toDouble())
        put("bold", bold)
        put("textAlign", textAlign)
        put("textShadow", textShadow)
        put("colorHex", colorHex)
        put("strokeColorHex", strokeColorHex)
        put("strokeWidthDp", strokeWidthDp.toDouble())
        put("cornerRadiusDp", cornerRadiusDp.toDouble())
        put("shapeKind", shapeKind.name)
        if (iconKey.isNotEmpty()) put("iconKey", iconKey)
        if (imageUri.isNotEmpty()) put("imageUri", imageUri)
        put("maskKind", maskKind.name)
        if (pathPoints.isNotEmpty()) put("pathPoints", pathPoints)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): StudioLayer = StudioLayer(
            layerId = (map["layerId"] as? String) ?: "",
            name = (map["name"] as? String) ?: "Layer",
            type = LayerType.safeFrom(map["type"] as? String),
            x = (map["x"] as? Number)?.toFloat() ?: 0.5f,
            y = (map["y"] as? Number)?.toFloat() ?: 0.5f,
            widthFraction = (map["widthFraction"] as? Number)?.toFloat() ?: 0.5f,
            heightFraction = (map["heightFraction"] as? Number)?.toFloat() ?: 0.2f,
            rotation = (map["rotation"] as? Number)?.toFloat() ?: 0f,
            opacity = (map["opacity"] as? Number)?.toFloat() ?: 1f,
            locked = (map["locked"] as? Boolean) ?: false,
            hidden = (map["hidden"] as? Boolean) ?: false,
            text = (map["text"] as? String) ?: "",
            fontId = (map["fontId"] as? String) ?: "plus_jakarta",
            fontSizeSp = (map["fontSizeSp"] as? Number)?.toFloat() ?: 26f,
            letterSpacingSp = (map["letterSpacingSp"] as? Number)?.toFloat() ?: 0f,
            bold = (map["bold"] as? Boolean) ?: false,
            textAlign = (map["textAlign"] as? String) ?: "CENTER",
            textShadow = (map["textShadow"] as? Boolean) ?: false,
            colorHex = (map["colorHex"] as? String) ?: "#F5F5F5",
            strokeColorHex = (map["strokeColorHex"] as? String) ?: "#C4C8CE",
            strokeWidthDp = (map["strokeWidthDp"] as? Number)?.toFloat() ?: 0f,
            cornerRadiusDp = (map["cornerRadiusDp"] as? Number)?.toFloat() ?: 8f,
            shapeKind = ShapeKind.safeFrom(map["shapeKind"] as? String),
            iconKey = (map["iconKey"] as? String) ?: "sparkles",
            imageUri = (map["imageUri"] as? String) ?: "",
            maskKind = MaskKind.safeFrom(map["maskKind"] as? String),
            pathPoints = (map["pathPoints"] as? String) ?: ""
        )
    }
}

@Keep
data class TimelineClip(
    val clipId: String = "",
    val trackType: String = "VIDEO", // VIDEO, AUDIO, TEXT
    val title: String = "Clip",
    val mediaUri: String = "",
    val sourceDurationMs: Long = 10_000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 10_000L,
    val timelineStartMs: Long = 0L,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val reversed: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val transitionAfter: TransitionKind = TransitionKind.NONE,
    val overlayText: String = "",
    val keyframeStartScale: Float = 1f,
    val keyframeEndScale: Float = 1f,
    val keyframeStartOpacity: Float = 1f,
    val keyframeEndOpacity: Float = 1f
) {
    val effectiveDurationMs: Long
        get() = ((trimEndMs - trimStartMs).coerceAtLeast(300L) / speed.coerceIn(0.25f, 4f)).toLong()

    fun toMap(): Map<String, Any> = buildMap {
        put("clipId", clipId)
        put("trackType", trackType)
        put("title", title)
        if (mediaUri.isNotEmpty()) put("mediaUri", mediaUri)
        put("sourceDurationMs", sourceDurationMs)
        put("trimStartMs", trimStartMs)
        put("trimEndMs", trimEndMs)
        put("timelineStartMs", timelineStartMs)
        put("speed", speed.toDouble())
        put("volume", volume.toDouble())
        put("muted", muted)
        put("reversed", reversed)
        put("fadeInMs", fadeInMs)
        put("fadeOutMs", fadeOutMs)
        put("transitionAfter", transitionAfter.name)
        if (overlayText.isNotEmpty()) put("overlayText", overlayText)
        put("keyframeStartScale", keyframeStartScale.toDouble())
        put("keyframeEndScale", keyframeEndScale.toDouble())
        put("keyframeStartOpacity", keyframeStartOpacity.toDouble())
        put("keyframeEndOpacity", keyframeEndOpacity.toDouble())
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): TimelineClip = TimelineClip(
            clipId = (map["clipId"] as? String) ?: "",
            trackType = (map["trackType"] as? String) ?: "VIDEO",
            title = (map["title"] as? String) ?: "Clip",
            mediaUri = (map["mediaUri"] as? String) ?: "",
            sourceDurationMs = (map["sourceDurationMs"] as? Number)?.toLong() ?: 10_000L,
            trimStartMs = (map["trimStartMs"] as? Number)?.toLong() ?: 0L,
            trimEndMs = (map["trimEndMs"] as? Number)?.toLong() ?: 10_000L,
            timelineStartMs = (map["timelineStartMs"] as? Number)?.toLong() ?: 0L,
            speed = (map["speed"] as? Number)?.toFloat() ?: 1f,
            volume = (map["volume"] as? Number)?.toFloat() ?: 1f,
            muted = (map["muted"] as? Boolean) ?: false,
            reversed = (map["reversed"] as? Boolean) ?: false,
            fadeInMs = (map["fadeInMs"] as? Number)?.toLong() ?: 0L,
            fadeOutMs = (map["fadeOutMs"] as? Number)?.toLong() ?: 0L,
            transitionAfter = TransitionKind.safeFrom(map["transitionAfter"] as? String),
            overlayText = (map["overlayText"] as? String) ?: "",
            keyframeStartScale = (map["keyframeStartScale"] as? Number)?.toFloat() ?: 1f,
            keyframeEndScale = (map["keyframeEndScale"] as? Number)?.toFloat() ?: 1f,
            keyframeStartOpacity = (map["keyframeStartOpacity"] as? Number)?.toFloat() ?: 1f,
            keyframeEndOpacity = (map["keyframeEndOpacity"] as? Number)?.toFloat() ?: 1f
        )
    }
}

@Keep
@IgnoreExtraProperties
data class CreativeProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "Untitled Project",
    val projectType: ProjectType = ProjectType.DESIGN,
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 1080,
    val backgroundColorHex: String = "#090A0C",
    val thumbnailHint: String = "",
    val mediaUri: String = "",
    val adjustments: PhotoAdjustments = PhotoAdjustments(),
    val layers: List<StudioLayer> = emptyList(),
    val clips: List<TimelineClip> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = buildMap {
        put("userId", uid)
        put("name", name.trim().ifBlank { "Untitled Project" }.take(120))
        put("projectType", projectType.name)
        put("canvasWidth", canvasWidth.coerceIn(64, 8192))
        put("canvasHeight", canvasHeight.coerceIn(64, 8192))
        put("backgroundColorHex", backgroundColorHex.ifBlank { "#090A0C" }.take(16))
        if (thumbnailHint.isNotBlank()) put("thumbnailHint", thumbnailHint.take(500))
        if (mediaUri.isNotBlank()) put("mediaUri", mediaUri.take(1000))
        put("adjustments", adjustments.toMap())
        put("layers", layers.take(60).map { it.toMap() })
        put("clips", clips.take(40).map { it.toMap() })
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    fun toUpdateMap(): Map<String, Any> = buildMap {
        put("name", name.trim().ifBlank { "Untitled Project" }.take(120))
        put("canvasWidth", canvasWidth.coerceIn(64, 8192))
        put("canvasHeight", canvasHeight.coerceIn(64, 8192))
        put("backgroundColorHex", backgroundColorHex.ifBlank { "#090A0C" }.take(16))
        put("thumbnailHint", thumbnailHint.take(500))
        put("mediaUri", mediaUri.take(1000))
        put("adjustments", adjustments.toMap())
        put("layers", layers.take(60).map { it.toMap() })
        put("clips", clips.take(40).map { it.toMap() })
        put("updatedAt", FieldValue.serverTimestamp())
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromSnapshot(doc: DocumentSnapshot): CreativeProject? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val rawLayers = (data["layers"] as? List<*>)
                ?.mapNotNull { it as? Map<String, Any?> }
                ?.map { StudioLayer.fromMap(it) }
                ?: emptyList()
            val rawClips = (data["clips"] as? List<*>)
                ?.mapNotNull { it as? Map<String, Any?> }
                ?.map { TimelineClip.fromMap(it) }
                ?: emptyList()
            val adjMap = data["adjustments"] as? Map<String, Any?>
            return CreativeProject(
                id = doc.id,
                userId = (data["userId"] as? String) ?: "",
                name = (data["name"] as? String) ?: "Untitled Project",
                projectType = ProjectType.safeFrom(data["projectType"] as? String),
                canvasWidth = (data["canvasWidth"] as? Number)?.toInt() ?: 1080,
                canvasHeight = (data["canvasHeight"] as? Number)?.toInt() ?: 1080,
                backgroundColorHex = (data["backgroundColorHex"] as? String) ?: "#090A0C",
                thumbnailHint = (data["thumbnailHint"] as? String) ?: "",
                mediaUri = (data["mediaUri"] as? String) ?: "",
                adjustments = PhotoAdjustments.fromMap(adjMap),
                layers = rawLayers,
                clips = rawClips,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}
