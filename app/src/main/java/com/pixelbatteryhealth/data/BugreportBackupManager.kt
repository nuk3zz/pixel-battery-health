package com.pixelbatteryhealth.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns

class BugreportBackupManager(private val context: Context) {

    fun backupToDownloads(uri: Uri) {
        // MediaStore contribution to Downloads without permissions requires API 29+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val fileName = getFileName(uri) ?: "bugreport-${System.currentTimeMillis()}.zip"
        
        try {
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val itemUri = contentResolver.insert(collection, contentValues) ?: return

            contentResolver.openInputStream(uri)?.use { input ->
                contentResolver.openOutputStream(itemUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(itemUri, contentValues, null, null)
        } catch (_: Exception) {
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
}
