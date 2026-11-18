package com.example.feature.photoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.common.MediaStorageHelper
import com.example.core.database.CreativeProject
import com.example.core.database.LayerType
import com.example.core.database.MaskKind
import com.example.core.database.PhotoAdjustments
import com.example.core.database.ShapeKind
import com.example.core.database.StudioLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Non-destructive real Android Bitmap & ColorMatrix rendering engine for Photo Studio.
 */
object PhotoRenderEngine {

    fun buildColorMatrix(adj: PhotoAdjustments): ColorMatrix {
        val result = ColorMatrix()

        // 1. Saturation
        val satMatrix = ColorMatrix().apply { setSaturation(adj.saturation.coerceIn(0f, 2f)) }
        result.postConcat(satMatrix)

        // 2. Contrast + Exposure + Brightness + Highlights/Shadows lift
        val c = adj.contrast.coerceIn(0.5f, 1.8f)
        val exposureScale = Math.pow(2.0, adj.exposure.toDouble()).toFloat()
        val scale = c * exposureScale
        val translate = (-0.5f * c + 0.5f) * 255f + adj.brightness + (adj.shadows * 0.35f) + (adj.highlights * 0.18f)

        // 3. Temperature warmth shift (Red +, Blue -)
        val temp = adj.temperature.coerceIn(-50f, 50f) * 1.1f
        val toneMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate + temp,
                0f, scale, 0f, 0f, translate + (temp * 0.15f),
                0f, 0f, scale, 0f, translate - temp,
                0f, 0f, 0f, 1f, 0f
            )
        )
        result.postConcat(toneMatrix)
        return result
    }

    suspend fun renderFinalBitmap(
        context: Context,
        project: CreativeProject,
        sourceBitmap: Bitmap?
    ): Bitmap = withContext(Dispatchers.Default) {
        val adj = project.adjustments
        val baseWidth = sourceBitmap?.width ?: project.canvasWidth
        val baseHeight = sourceBitmap?.height ?: project.canvasHeight

        // Crop bounds
        val cropL = (adj.cropLeft.coerceIn(0f, 0.9f) * baseWidth).toInt()
        val cropT = (adj.cropTop.coerceIn(0f, 0.9f) * baseHeight).toInt()
        val cropW = ((adj.cropRight.coerceIn(adj.cropLeft + 0.1f, 1f) - adj.cropLeft.coerceIn(0f, 0.9f)) * baseWidth).toInt().coerceAtLeast(64)
        val cropH = ((adj.cropBottom.coerceIn(adj.cropTop + 0.1f, 1f) - adj.cropTop.coerceIn(0f, 0.9f)) * baseHeight).toInt().coerceAtLeast(64)

        val outBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        val bgPaint = Paint().apply {
            color = runCatching { android.graphics.Color.parseColor(project.backgroundColorHex) }
                .getOrDefault(android.graphics.Color.parseColor("#090A0C"))
        }
        canvas.drawRect(0f, 0f, cropW.toFloat(), cropH.toFloat(), bgPaint)

        if (sourceBitmap != null) {
            val cropped = Bitmap.createBitmap(
                sourceBitmap,
                cropL.coerceIn(0, sourceBitmap.width - 1),
                cropT.coerceIn(0, sourceBitmap.height - 1),
                cropW.coerceAtMost(sourceBitmap.width - cropL.coerceIn(0, sourceBitmap.width - 1)),
                cropH.coerceAtMost(sourceBitmap.height - cropT.coerceIn(0, sourceBitmap.height - 1))
            )

            val matrix = Matrix().apply {
                val cx = cropped.width / 2f
                val cy = cropped.height / 2f
                postScale(if (adj.flipH) -1f else 1f, if (adj.flipV) -1f else 1f, cx, cy)
                postRotate(adj.rotationDegrees + adj.straightenDegrees, cx, cy)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(buildColorMatrix(adj))
            }
            canvas.drawBitmap(cropped, matrix, paint)
        }

        // Apply Vignette if > 0
        if (adj.vignette > 0.01f) {
            val vRadius = maxOf(cropW, cropH) * 0.72f
            val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cropW / 2f,
                    cropH / 2f,
                    vRadius,
                    intArrayOf(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.argb((adj.vignette.coerceIn(0f, 1f) * 210).toInt(), 0, 0, 0)
                    ),
                    floatArrayOf(0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, cropW.toFloat(), cropH.toFloat(), vPaint)
        }

        // Composite layers (Images, Shapes, Drawings, Text)
        for (layer in project.layers) {
            if (layer.hidden) continue
            drawLayerOnCanvas(context, canvas, cropW.toFloat(), cropH.toFloat(), layer)
        }

        outBitmap
    }

    suspend fun drawLayerOnCanvas(
        context: Context,
        canvas: Canvas,
        canvasW: Float,
        canvasH: Float,
        layer: StudioLayer
    ) {
        val alphaInt = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()
        val cx = layer.x * canvasW
        val cy = layer.y * canvasH
        val w = layer.widthFraction * canvasW
        val h = layer.heightFraction * canvasH

        canvas.save()
        canvas.rotate(layer.rotation, cx, cy)

        when (layer.type) {
            LayerType.SHAPE -> {
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = parseColorSafe(layer.colorHex, android.graphics.Color.WHITE)
                    alpha = alphaInt
                    style = Paint.Style.FILL
                }
                val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                when (layer.shapeKind) {
                    ShapeKind.CIRCLE -> canvas.drawOval(rect, fillPaint)
                    ShapeKind.ROUNDED_RECT, ShapeKind.BADGE -> {
                        val r = layer.cornerRadiusDp * (canvasW / 360f)
                        canvas.drawRoundRect(rect, r, r, fillPaint)
                    }
                    ShapeKind.LINE -> {
                        fillPaint.strokeWidth = maxOf(4f, h)
                        canvas.drawLine(rect.left, cy, rect.right, cy, fillPaint)
                    }
                    else -> canvas.drawRect(rect, fillPaint)
                }
                if (layer.strokeWidthDp > 0f) {
                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = parseColorSafe(layer.strokeColorHex, android.graphics.Color.LTGRAY)
                        alpha = alphaInt
                        style = Paint.Style.STROKE
                        strokeWidth = layer.strokeWidthDp * (canvasW / 360f)
                    }
                    when (layer.shapeKind) {
                        ShapeKind.CIRCLE -> canvas.drawOval(rect, strokePaint)
                        ShapeKind.ROUNDED_RECT, ShapeKind.BADGE -> {
                            val r = layer.cornerRadiusDp * (canvasW / 360f)
                            canvas.drawRoundRect(rect, r, r, strokePaint)
                        }
                        else -> canvas.drawRect(rect, strokePaint)
                    }
                }
            }

            LayerType.TEXT -> {
                val typeface = resolveTypeface(context, layer.fontId, layer.bold)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = parseColorSafe(layer.colorHex, android.graphics.Color.WHITE)
                    alpha = alphaInt
                    this.typeface = typeface
                    textSize = layer.fontSizeSp * (canvasW / 360f)
                    letterSpacing = layer.letterSpacingSp / 20f
                    textAlign = when (layer.textAlign) {
                        "LEFT" -> Paint.Align.LEFT
                        "RIGHT" -> Paint.Align.RIGHT
                        else -> Paint.Align.CENTER
                    }
                    if (layer.textShadow) {
                        setShadowLayer(8f, 0f, 4f, android.graphics.Color.argb(170, 0, 0, 0))
                    }
                }
                val lines = layer.text.split('\n')
                val lineHeight = paint.fontSpacing
                val totalHeight = lineHeight * lines.size
                var yPos = cy - totalHeight / 2f + lineHeight * 0.78f
                val xPos = when (layer.textAlign) {
                    "LEFT" -> cx - w / 2f
                    "RIGHT" -> cx + w / 2f
                    else -> cx
                }
                for (line in lines) {
                    canvas.drawText(line, xPos, yPos, paint)
                    yPos += lineHeight
                }
            }

            LayerType.IMAGE -> {
                if (layer.imageUri.isNotBlank()) {
                    val overlayBmp = MediaStorageHelper.decodeBitmapSafely(context, layer.imageUri, 1200)
                    if (overlayBmp != null) {
                        val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                        val saved = canvas.saveLayer(rect, null)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                            alpha = alphaInt
                        }
                        if (layer.maskKind == MaskKind.CIRCLE) {
                            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                            canvas.drawOval(rect, maskPaint)
                            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                        } else if (layer.maskKind == MaskKind.ROUNDED_RECT) {
                            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                            val r = 24f * (canvasW / 360f)
                            canvas.drawRoundRect(rect, r, r, maskPaint)
                            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                        }
                        canvas.drawBitmap(overlayBmp, null, rect, paint)
                        canvas.restoreToCount(saved)
                    }
                }
            }

            LayerType.DRAWING -> {
                if (layer.pathPoints.isNotBlank()) {
                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = parseColorSafe(layer.colorHex, android.graphics.Color.WHITE)
                        alpha = alphaInt
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        strokeWidth = maxOf(3f, layer.strokeWidthDp) * (canvasW / 360f)
                    }
                    val path = Path()
                    val pts = layer.pathPoints.split(';')
                    pts.forEachIndexed { idx, token ->
                        val parts = token.split(',')
                        if (parts.size == 2) {
                            val px = (parts[0].toFloatOrNull() ?: 0f) * canvasW
                            val py = (parts[1].toFloatOrNull() ?: 0f) * canvasH
                            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                    }
                    canvas.drawPath(path, strokePaint)
                }
            }

            LayerType.ICON -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = parseColorSafe(layer.colorHex, android.graphics.Color.WHITE)
                    alpha = alphaInt
                    style = Paint.Style.STROKE
                    strokeWidth = 4f * (canvasW / 360f)
                }
                val r = minOf(w, h) * 0.4f
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
        canvas.restore()
    }

    private fun resolveTypeface(context: Context, fontId: String, bold: Boolean): Typeface {
        val fontRes = when (fontId) {
            "inter" -> R.font.inter
            "playfair" -> R.font.playfair_display
            "space_grotesk" -> R.font.space_grotesk
            "jetbrains_mono" -> R.font.jetbrains_mono
            else -> R.font.plus_jakarta_sans
        }
        val base = runCatching { ResourcesCompat.getFont(context, fontRes) }.getOrNull() ?: Typeface.DEFAULT
        return if (bold) Typeface.create(base, Typeface.BOLD) else base
    }

    fun parseColorSafe(hex: String, fallback: Int): Int =
        runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(fallback)
}
