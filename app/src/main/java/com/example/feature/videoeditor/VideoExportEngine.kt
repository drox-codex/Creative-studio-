package com.example.feature.videoeditor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.core.common.MediaStorageHelper
import com.example.core.database.CreativeProject
import com.example.core.database.TimelineClip
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Real Media3 Transformer pipeline for exporting multi-clip trimmed, speed-adjusted,
 * color-graded video compositions to MP4.
 */
object VideoExportEngine {

    @OptIn(UnstableApi::class)
    suspend fun exportVideoComposition(
        context: Context,
        project: CreativeProject,
        resolution: String,
        onProgress: (Int) -> Unit
    ): Result<File> = suspendCancellableCoroutine { cont ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val videoClips = project.clips.filter { it.trackType == "VIDEO" && it.mediaUri.isNotBlank() }
                if (videoClips.isEmpty()) {
                    if (cont.isActive) cont.resume(Result.failure(IllegalStateException("Import at least one video clip before exporting.")))
                    return@post
                }

                val targetHeight = if (resolution == "720p") 720 else 1080
                val editedVideoItems = videoClips.map { clip ->
                    buildEditedVideoItem(clip, project, targetHeight)
                }

                val videoSequence = EditedMediaItemSequence.Builder(editedVideoItems).build()
                val audioClips = project.clips.filter { it.trackType == "AUDIO" && it.mediaUri.isNotBlank() && !it.muted }
                val sequences = mutableListOf(videoSequence)
                if (audioClips.isNotEmpty()) {
                    val audioItems = audioClips.map { aClip ->
                        val item = MediaItem.Builder()
                            .setUri(Uri.parse(aClip.mediaUri))
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(aClip.trimStartMs.coerceAtLeast(0L))
                                    .setEndPositionMs(aClip.trimEndMs.coerceAtLeast(aClip.trimStartMs + 500L))
                                    .build()
                            )
                            .build()
                        EditedMediaItem.Builder(item).setRemoveVideo(true).build()
                    }
                    sequences.add(EditedMediaItemSequence.Builder(audioItems).build())
                }

                val composition = Composition.Builder(sequences).build()
                val safeName = project.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40).ifBlank { "video" }
                val outFile = File(MediaStorageHelper.exportsDir(context), "${safeName}_${System.currentTimeMillis()}.mp4")
                if (outFile.exists()) outFile.delete()

                var finished = false
                val progressHolder = ProgressHolder()
                lateinit var transformer: Transformer

                val progressRunnable = object : Runnable {
                    override fun run() {
                        if (finished) return
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress)
                        }
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            mainHandler.postDelayed(this, 350L)
                        }
                    }
                }

                transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            finished = true
                            mainHandler.removeCallbacks(progressRunnable)
                            saveVideoToMediaStore(context, outFile)
                            onProgress(100)
                            if (cont.isActive) cont.resume(Result.success(outFile))
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            finished = true
                            mainHandler.removeCallbacks(progressRunnable)
                            if (cont.isActive) cont.resume(Result.failure(exportException))
                        }
                    })
                    .build()

                cont.invokeOnCancellation {
                    finished = true
                    mainHandler.post {
                        mainHandler.removeCallbacks(progressRunnable)
                        transformer.cancel()
                    }
                }

                transformer.start(composition, outFile.absolutePath)
                mainHandler.post(progressRunnable)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Result.failure(e))
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildEditedVideoItem(
        clip: TimelineClip,
        project: CreativeProject,
        targetHeight: Int
    ): EditedMediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
            .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 500L))
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(clip.mediaUri))
            .setClippingConfiguration(clipping)
            .build()

        val videoEffects = mutableListOf<Effect>()
        videoEffects.add(Presentation.createForHeight(targetHeight))

        if (clip.speed != 1f) {
            videoEffects.add(SpeedChangeEffect(clip.speed.coerceIn(0.25f, 4f)))
        }

        val adj = project.adjustments
        if (adj.filterPreset == "Silver Gelatin" || adj.filterPreset == "Noir" || adj.saturation == 0f) {
            videoEffects.add(RgbFilter.createGrayscaleFilter())
        } else if (adj.saturation != 1f) {
            val satDelta = ((adj.saturation - 1f) * 100f).coerceIn(-100f, 100f)
            videoEffects.add(HslAdjustment.Builder().adjustSaturation(satDelta).build())
        }

        if (adj.contrast != 1f) {
            val contrastDelta = (adj.contrast - 1f).coerceIn(-1f, 1f)
            videoEffects.add(Contrast(contrastDelta))
        }

        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(clip.muted || clip.volume <= 0.01f)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
    }

    private fun saveVideoToMediaStore(context: Context, file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !file.exists()) return
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/CreativeStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (itemUri != null) {
                resolver.openOutputStream(itemUri)?.use { os ->
                    file.inputStream().use { it.copyTo(os) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
        } catch (_: Exception) {
        }
    }
}
