package com.example.core.common

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Handles scoped media storage, user-isolated local files, bitmap decoding, and sharing.
 */
object MediaStorageHelper {

    fun userMediaDir(context: Context, userId: String): File {
        val dir = File(context.filesDir, "media/$userId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun importMediaToUserStorage(
        context: Context,
        userId: String,
        sourceUri: Uri,
        extension: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dir = userMediaDir(context, userId)
            val fileName = "asset_${UUID.randomUUID().toString().take(8)}.$extension"
            val destFile = File(dir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected media"))
            Result.success(Uri.fromFile(destFile).toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveBitmapToUserStorage(
        context: Context,
        userId: String,
        bitmap: Bitmap,
        prefix: String = "edit"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dir = userMediaDir(context, userId)
            val destFile = File(dir, "${prefix}_${UUID.randomUUID().toString().take(8)}.png")
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Result.success(Uri.fromFile(destFile).toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun decodeBitmapSafely(
        context: Context,
        uriString: String,
        maxDimension: Int = 1600
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null
        try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        } catch (_: Exception) {
            null
        }
    }

    private fun openStream(context: Context, uri: Uri) = when (uri.scheme) {
        "file" -> uri.path?.let { File(it).takeIf(File::exists)?.inputStream() }
        else -> context.contentResolver.openInputStream(uri)
    }

    suspend fun exportBitmapToGalleryAndCache(
        context: Context,
        bitmap: Bitmap,
        title: String,
        format: String = "PNG",
        quality: Int = 95
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val isJpeg = format.equals("JPEG", ignoreCase = true) || format.equals("JPG", ignoreCase = true)
            val ext = if (isJpeg) "jpg" else "png"
            val mime = if (isJpeg) "image/jpeg" else "image/png"
            val compressFormat = if (isJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            val safeName = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40).ifBlank { "export" }
            val outFile = File(exportsDir(context), "${safeName}_${System.currentTimeMillis()}.$ext")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(compressFormat, quality.coerceIn(70, 100), out)
            }

            // Also insert into MediaStore Pictures/CreativeStudio on Android 10+ (zero permissions required)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, outFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CreativeStudio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { os ->
                        outFile.inputStream().use { it.copyTo(os) }
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                }
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Export")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    suspend fun computeStorageBytes(context: Context, userId: String): Long = withContext(Dispatchers.IO) {
        val userBytes = folderSize(userMediaDir(context, userId))
        val cacheBytes = folderSize(context.cacheDir)
        userBytes + cacheBytes
    }

    suspend fun clearExportCache(context: Context): Long = withContext(Dispatchers.IO) {
        val dir = exportsDir(context)
        dir.listFiles()?.forEach { it.deleteRecursively() }
        folderSize(context.cacheDir)
    }

    fun clearUserMedia(context: Context, userId: String) {
        if (userId.isBlank()) return
        val dir = File(context.filesDir, "media/$userId")
        if (dir.exists()) dir.deleteRecursively()
    }

    private fun folderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { folderSize(it) } ?: 0L
    }
}
