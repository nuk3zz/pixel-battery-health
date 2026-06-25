package com.pixelbatteryhealth.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

class BugreportZipExtractor(
    private val context: Context,
) {
    @Throws(IOException::class)
    fun extract(uri: Uri): ExtractedBugreport {
        val destination = File(context.cacheDir, "bugreport-${UUID.randomUUID()}")
        if (!destination.mkdirs()) throw IOException("Could not create extraction directory")

        var entryCount = 0
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw IOException("Could not open selected file")
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entryCount += 1
                        val output = File(destination, entry.name)
                        val canonicalDestination = destination.canonicalFile
                        val canonicalOutput = output.canonicalFile

                        if (!canonicalOutput.path.startsWith(canonicalDestination.path + File.separator)) {
                            throw ZipException("Unsafe ZIP entry: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            canonicalOutput.mkdirs()
                        } else {
                            canonicalOutput.parentFile?.mkdirs()
                            canonicalOutput.outputStream().use { fileOut ->
                                zip.copyTo(fileOut)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            if (entryCount == 0) throw ZipException("ZIP did not contain entries")
        } catch (error: Throwable) {
            destination.deleteRecursively()
            throw error
        }

        return ExtractedBugreport(
            root = destination,
            sourceName = uri.displayName(context) ?: context.contentResolver.getType(uri).orEmpty(),
        )
    }

    private fun Uri.displayName(context: Context): String? =
        context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
}

data class ExtractedBugreport(
    val root: File,
    val sourceName: String,
)
