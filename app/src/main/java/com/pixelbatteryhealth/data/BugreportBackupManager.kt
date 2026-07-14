package com.pixelbatteryhealth.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.IOException

class BugreportBackupManager(private val context: Context) {

    fun backupToDownloads(
        uri: Uri,
        onProgress: (Float?) -> Unit = {},
        checkCancelled: () -> Unit = {},
    ) {
        // MediaStore contribution to Downloads without permissions requires API 29+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val fileName = getFileName(uri) ?: "bugreport-${System.currentTimeMillis()}.zip"

        var itemUri: Uri? = null
        try {
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val createdUri = contentResolver.insert(collection, contentValues) ?: return
            itemUri = createdUri
            val sourceSize = getFileSize(uri)
            var copiedBytes = 0L
            var lastPercent = -1

            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open selected bugreport")
            val outputStream = contentResolver.openOutputStream(createdUri)
                ?: throw IOException("Could not create bugreport backup")
            inputStream.use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copiedBytes += count
                        val fraction = sourceSize?.takeIf { it > 0L }
                            ?.let { (copiedBytes.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
                        val percent = fraction?.let { (it * 100).toInt() }
                        if (percent == null || percent != lastPercent) {
                            lastPercent = percent ?: lastPercent
                            onProgress(fraction)
                        }
                    }
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(createdUri, contentValues, null, null)
            onProgress(1f)
        } catch (error: Exception) {
            itemUri?.let { context.contentResolver.delete(it, null, null) }
            if (error is kotlinx.coroutines.CancellationException) throw error
        }
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) cursor.getString(index) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getFileSize(uri: Uri): Long? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
            }
        }.getOrNull()
}
