package com.example.feature.designeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.core.common.MediaStorageHelper
import com.example.core.database.CreativeProject
import com.example.feature.photoeditor.PhotoRenderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders Design Studio projects into full-resolution Bitmaps and PDF documents.
 */
object DesignRenderEngine {

    suspend fun renderDesignBitmap(
        context: Context,
        project: CreativeProject,
        maxExportSide: Int = 2160
    ): Bitmap = withContext(Dispatchers.Default) {
        val scale = if (maxOf(project.canvasWidth, project.canvasHeight) > maxExportSide) {
            maxExportSide.toFloat() / maxOf(project.canvasWidth, project.canvasHeight)
        } else 1f

        val w = (project.canvasWidth * scale).toInt().coerceAtLeast(64)
        val h = (project.canvasHeight * scale).toInt().coerceAtLeast(64)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = PhotoRenderEngine.parseColorSafe(
                project.backgroundColorHex,
                android.graphics.Color.parseColor("#090A0C")
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        for (layer in project.layers) {
            if (!layer.hidden) {
                PhotoRenderEngine.drawLayerOnCanvas(context, canvas, w.toFloat(), h.toFloat(), layer)
            }
        }
        bitmap
    }

    suspend fun exportDesignToPdf(
        context: Context,
        project: CreativeProject
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val bitmap = renderDesignBitmap(context, project, maxExportSide = 1600)
            val pdfDoc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDoc.finishPage(page)

            val safeName = project.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40).ifBlank { "design" }
            val outFile = File(MediaStorageHelper.exportsDir(context), "${safeName}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
