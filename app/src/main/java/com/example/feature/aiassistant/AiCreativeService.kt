package com.example.feature.aiassistant

import android.graphics.Bitmap
import android.util.Log
import com.example.core.database.CreativeProject
import com.example.core.database.LayerType
import com.example.core.database.PhotoAdjustments
import com.example.core.database.ProjectType
import com.example.core.database.StudioLayer
import com.example.core.database.TimelineClip
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

data class AiExecutionResult(
    val actionType: String,
    val reply: String,
    val executable: Boolean,
    val updatedProject: CreativeProject? = null,
    val generatedBitmap: Bitmap? = null
)

class AiCreativeService {

    private val plannerSchema = Schema.obj(
        properties = mapOf(
            "actionType" to Schema.enumeration(
                values = listOf(
                    "PHOTO_ADJUST",
                    "AI_IMAGE_EDIT",
                    "AI_IMAGE_GENERATE",
                    "ADD_TEXT_LAYER",
                    "UPDATE_SELECTED_LAYER",
                    "UPDATE_BACKGROUND",
                    "APPLY_BRAND_KIT",
                    "VIDEO_EDIT",
                    "COMPOSITION_ADVICE",
                    "UNSUPPORTED"
                ),
                description = "Exact operation category to execute on the active project"
            ),
            "executable" to Schema.boolean(description = "True if the command can be applied automatically"),
            "reply" to Schema.string(description = "Concise explanation of changes or why operation cannot run"),
            "imageEditPrompt" to Schema.string(description = "Detailed instruction for Gemini image editing/generation", nullable = true),
            "filterPreset" to Schema.string(description = "Filter name: Original, Silver Gelatin, Noir, Matte Slate, Warm Film, Crisp Studio, Fade", nullable = true),
            "brightnessDelta" to Schema.double(description = "Brightness offset -100..100", nullable = true),
            "contrast" to Schema.double(description = "Contrast multiplier 0.5..1.8", nullable = true),
            "saturation" to Schema.double(description = "Saturation multiplier 0.0..2.0", nullable = true),
            "temperature" to Schema.double(description = "Temperature warmth -50..50", nullable = true),
            "exposure" to Schema.double(description = "Exposure -1.0..1.0", nullable = true),
            "vignette" to Schema.double(description = "Vignette 0.0..1.0", nullable = true),
            "textContent" to Schema.string(description = "Text string to add or update", nullable = true),
            "colorHex" to Schema.string(description = "Hex color string like #C4C8CE or #090A0C", nullable = true),
            "fontId" to Schema.string(description = "Font id: plus_jakarta, inter, playfair, space_grotesk, jetbrains_mono", nullable = true),
            "fontSizeSp" to Schema.double(description = "Font size in sp 10..72", nullable = true),
            "backgroundColorHex" to Schema.string(description = "Canvas background hex color", nullable = true),
            "videoSpeed" to Schema.double(description = "Video speed multiplier 0.25..2.0", nullable = true),
            "captionText" to Schema.string(description = "Timed caption overlay text for video", nullable = true)
        ),
        optionalProperties = listOf(
            "imageEditPrompt", "filterPreset", "brightnessDelta", "contrast",
            "saturation", "temperature", "exposure", "vignette", "textContent",
            "colorHex", "fontId", "fontSizeSp", "backgroundColorHex",
            "videoSpeed", "captionText"
        )
    )

    private val plannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = plannerSchema
                temperature = 0.2f
            }
        )
    }

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun processPrompt(
        prompt: String,
        activeProject: CreativeProject?,
        selectedLayerId: String?,
        activeBitmap: Bitmap?
    ): Result<AiExecutionResult> {
        return try {
            val selectedLayer = activeProject?.layers?.firstOrNull { it.layerId == selectedLayerId }
            val contextSummary = buildString {
                if (activeProject == null) {
                    appendLine("Active project: NONE (User is outside an editor; can advise, generate brand assets, or ask them to open a project).")
                } else {
                    appendLine("Active project: name='${activeProject.name}', type=${activeProject.projectType.name}, size=${activeProject.canvasWidth}x${activeProject.canvasHeight}, bg=${activeProject.backgroundColorHex}")
                    appendLine("Current adjustments: filter=${activeProject.adjustments.filterPreset}, brightness=${activeProject.adjustments.brightness}, contrast=${activeProject.adjustments.contrast}, saturation=${activeProject.adjustments.saturation}, temp=${activeProject.adjustments.temperature}")
                    appendLine("Layers (${activeProject.layers.size}): " + activeProject.layers.joinToString("; ") {
                        "[id=${it.layerId}, type=${it.type.name}, name=${it.name}, text='${it.text.take(30)}', color=${it.colorHex}, locked=${it.locked}]"
                    })
                    if (selectedLayer != null) {
                        appendLine("Selected element: id=${selectedLayer.layerId}, type=${selectedLayer.type.name}, name=${selectedLayer.name}, text='${selectedLayer.text}', color=${selectedLayer.colorHex}")
                    } else {
                        appendLine("Selected element: NONE")
                    }
                    if (activeProject.projectType == ProjectType.VIDEO) {
                        appendLine("Video clips (${activeProject.clips.size}): " + activeProject.clips.joinToString("; ") {
                            "[id=${it.clipId}, track=${it.trackType}, title=${it.title}, speed=${it.speed}]"
                        })
                    }
                }
                appendLine("IMPORTANT RULES: Never offer or attempt AI video generation or text-to-video generation; return UNSUPPORTED if asked to generate a video. Only modify requested aspects and preserve unrelated layers. For metallic silver text use #C4C8CE. For obsidian black use #090A0C.")
            }

            val planResponse = withTimeout(60_000L) {
                plannerModel.generateContent(
                    content {
                        if (activeBitmap != null) {
                            image(downscaleForAi(activeBitmap))
                        }
                        text("SYSTEM CONTEXT:\n$contextSummary\n\nUSER REQUEST:\n$prompt")
                    }
                )
            }

            val jsonText = planResponse.text
                ?: return Result.failure(IllegalStateException("AI planner returned an empty response."))

            val json = JSONObject(jsonText)
            val actionType = json.optString("actionType", "UNSUPPORTED")
            val executable = json.optBoolean("executable", false)
            val reply = json.optString("reply", "Done.")

            if (!executable || actionType == "UNSUPPORTED" || actionType == "COMPOSITION_ADVICE") {
                return Result.success(
                    AiExecutionResult(
                        actionType = actionType,
                        reply = reply,
                        executable = false,
                        updatedProject = null
                    )
                )
            }

            // Image generation or image-to-image editing via gemini-nano-banana-2.1
            if (actionType == "AI_IMAGE_EDIT" || actionType == "AI_IMAGE_GENERATE") {
                val editInstruction = json.optString("imageEditPrompt").ifBlank { prompt }
                if (actionType == "AI_IMAGE_EDIT" && activeBitmap == null) {
                    return Result.success(
                        AiExecutionResult(
                            actionType = "UNSUPPORTED",
                            reply = "No source image is loaded in this project yet. Import a photo or ask me to generate an image asset.",
                            executable = false
                        )
                    )
                }
                val generated = withTimeout(60_000L) {
                    val response = if (actionType == "AI_IMAGE_EDIT" && activeBitmap != null) {
                        imageModel.generateContent(
                            content {
                                image(downscaleForAi(activeBitmap))
                                text(editInstruction)
                            }
                        )
                    } else {
                        imageModel.generateContent(editInstruction)
                    }
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()?.image
                }
                    ?: return Result.failure(IllegalStateException("Image model did not return an image."))

                return Result.success(
                    AiExecutionResult(
                        actionType = actionType,
                        reply = reply,
                        executable = true,
                        updatedProject = activeProject,
                        generatedBitmap = generated
                    )
                )
            }

            if (activeProject == null) {
                return Result.success(
                    AiExecutionResult(
                        actionType = "UNSUPPORTED",
                        reply = "$reply Open a Photo, Design, or Video project first to apply this edit directly.",
                        executable = false
                    )
                )
            }

            val mutated = applyCommandToProject(activeProject, selectedLayerId, actionType, json)
            Result.success(
                AiExecutionResult(
                    actionType = actionType,
                    reply = reply,
                    executable = true,
                    updatedProject = mutated
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI request timed out", e)
            Result.failure(IllegalStateException("AI request timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI operation failed", e)
            Result.failure(e)
        }
    }

    private fun applyCommandToProject(
        project: CreativeProject,
        selectedLayerId: String?,
        actionType: String,
        json: JSONObject
    ): CreativeProject {
        return when (actionType) {
            "PHOTO_ADJUST" -> {
                val adj = project.adjustments
                val nextAdj = adj.copy(
                    filterPreset = json.optString("filterPreset").ifBlank { adj.filterPreset },
                    brightness = if (json.has("brightnessDelta") && !json.isNull("brightnessDelta")) {
                        json.optDouble("brightnessDelta").toFloat().coerceIn(-100f, 100f)
                    } else adj.brightness,
                    contrast = if (json.has("contrast") && !json.isNull("contrast")) {
                        json.optDouble("contrast").toFloat().coerceIn(0.5f, 1.8f)
                    } else adj.contrast,
                    saturation = if (json.has("saturation") && !json.isNull("saturation")) {
                        json.optDouble("saturation").toFloat().coerceIn(0f, 2f)
                    } else adj.saturation,
                    temperature = if (json.has("temperature") && !json.isNull("temperature")) {
                        json.optDouble("temperature").toFloat().coerceIn(-50f, 50f)
                    } else adj.temperature,
                    exposure = if (json.has("exposure") && !json.isNull("exposure")) {
                        json.optDouble("exposure").toFloat().coerceIn(-1f, 1f)
                    } else adj.exposure,
                    vignette = if (json.has("vignette") && !json.isNull("vignette")) {
                        json.optDouble("vignette").toFloat().coerceIn(0f, 1f)
                    } else adj.vignette
                )
                project.copy(adjustments = nextAdj)
            }

            "ADD_TEXT_LAYER" -> {
                val text = json.optString("textContent").ifBlank { "Studio Text" }
                val color = json.optString("colorHex").ifBlank { "#F5F5F5" }
                val fontId = json.optString("fontId").ifBlank { "plus_jakarta" }
                val sizeSp = if (json.has("fontSizeSp") && !json.isNull("fontSizeSp")) {
                    json.optDouble("fontSizeSp", 26.0).toFloat().coerceIn(12f, 72f)
                } else 26f
                val newLayer = StudioLayer(
                    layerId = UUID.randomUUID().toString().take(8),
                    name = text.take(20),
                    type = LayerType.TEXT,
                    x = 0.5f,
                    y = 0.5f,
                    widthFraction = 0.72f,
                    heightFraction = 0.16f,
                    text = text,
                    fontId = fontId,
                    fontSizeSp = sizeSp,
                    colorHex = color
                )
                project.copy(layers = project.layers + newLayer)
            }

            "UPDATE_SELECTED_LAYER" -> {
                val targetId = selectedLayerId
                    ?: project.layers.lastOrNull { it.type == LayerType.TEXT && !it.locked }?.layerId
                    ?: project.layers.lastOrNull { !it.locked }?.layerId
                if (targetId == null) {
                    project
                } else {
                    val updatedLayers = project.layers.map { layer ->
                        if (layer.layerId != targetId || layer.locked) {
                            layer
                        } else {
                            layer.copy(
                                text = json.optString("textContent").ifBlank { layer.text },
                                colorHex = json.optString("colorHex").ifBlank { layer.colorHex },
                                fontId = json.optString("fontId").ifBlank { layer.fontId },
                                fontSizeSp = if (json.has("fontSizeSp") && !json.isNull("fontSizeSp")) {
                                    json.optDouble("fontSizeSp").toFloat().coerceIn(10f, 72f)
                                } else layer.fontSizeSp
                            )
                        }
                    }
                    project.copy(layers = updatedLayers)
                }
            }

            "UPDATE_BACKGROUND" -> {
                val bg = json.optString("backgroundColorHex")
                    .ifBlank { json.optString("colorHex") }
                    .ifBlank { "#090A0C" }
                project.copy(backgroundColorHex = bg)
            }

            "APPLY_BRAND_KIT" -> {
                val bg = json.optString("backgroundColorHex").ifBlank { "#090A0C" }
                val accent = json.optString("colorHex").ifBlank { "#C4C8CE" }
                val fontId = json.optString("fontId").ifBlank { "plus_jakarta" }
                val updatedLayers = project.layers.map { layer ->
                    if (layer.locked) layer
                    else if (layer.type == LayerType.TEXT) layer.copy(fontId = fontId, colorHex = "#F5F5F5")
                    else if (layer.type == LayerType.SHAPE) layer.copy(strokeColorHex = accent)
                    else layer
                }
                project.copy(backgroundColorHex = bg, layers = updatedLayers)
            }

            "VIDEO_EDIT" -> {
                var nextProject = project
                if (json.has("videoSpeed") && !json.isNull("videoSpeed")) {
                    val speed = json.optDouble("videoSpeed", 1.0).toFloat().coerceIn(0.25f, 2f)
                    nextProject = nextProject.copy(
                        clips = nextProject.clips.map { clip ->
                            if (clip.trackType == "VIDEO") clip.copy(speed = speed) else clip
                        }
                    )
                }
                val caption = json.optString("captionText")
                if (caption.isNotBlank()) {
                    val newCaption = TimelineClip(
                        clipId = UUID.randomUUID().toString().take(8),
                        trackType = "TEXT",
                        title = caption.take(24),
                        overlayText = caption,
                        timelineStartMs = 0L,
                        sourceDurationMs = 4000L,
                        trimStartMs = 0L,
                        trimEndMs = 4000L
                    )
                    nextProject = nextProject.copy(clips = nextProject.clips + newCaption)
                }
                if (json.has("filterPreset") && !json.isNull("filterPreset")) {
                    val preset = json.optString("filterPreset")
                    if (preset.isNotBlank()) {
                        nextProject = nextProject.copy(
                            adjustments = nextProject.adjustments.copy(filterPreset = preset)
                        )
                    }
                }
                nextProject
            }

            else -> project
        }
    }

    private fun downscaleForAi(bitmap: Bitmap, maxDim: Int = 1024): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private companion object {
        const val TAG = "AiCreativeService"
    }
}
