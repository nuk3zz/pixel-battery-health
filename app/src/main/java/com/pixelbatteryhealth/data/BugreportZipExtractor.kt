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
    fun extract(
        uri: Uri,
        onProgress: (Float?) -> Unit = {},
        checkCancelled: () -> Unit = {},
    ): ExtractedBugreport {
        cleanupPreviousExtractions()
        val destination = File(context.cacheDir, "$EXTRACTION_DIRECTORY_PREFIX${UUID.randomUUID()}")
        if (!destination.mkdirs()) throw IOException("Could not create extraction directory")

        var entryCount = 0
        var extractedBytes = 0L
        val sourceSize = uri.size(context)
        var lastPercent = -1
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw IOException("Could not open selected file")
                val progressInput = ProgressInputStream(
                    input = input,
                    onBytesRead = { bytesRead ->
                        val fraction = sourceSize?.takeIf { it > 0L }
                            ?.let { (bytesRead.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
                        val percent = fraction?.let { (it * 100).toInt() }
                        if (percent == null || percent != lastPercent) {
                            lastPercent = percent ?: lastPercent
                            onProgress(fraction)
                        }
                    },
                    checkCancelled = checkCancelled,
                )
                ZipInputStream(progressInput.buffered()).use { zip ->
                    while (true) {
                        checkCancelled()
                        val entry = zip.nextEntry ?: break
                        entryCount += 1
                        if (entryCount > MAX_ENTRY_COUNT) {
                            throw ZipException("ZIP contains too many entries")
                        }
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
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    checkCancelled()
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    extractedBytes += count
                                    if (extractedBytes > MAX_EXTRACTED_BYTES) {
                                        throw ZipException("ZIP expands beyond the supported size")
                                    }
                                    fileOut.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            if (entryCount == 0) throw ZipException("ZIP did not contain entries")
            onProgress(1f)
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

    private fun Uri.size(context: Context): Long? =
        runCatching {
            context.contentResolver.query(this, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
                }
        }.getOrNull()

    private fun cleanupPreviousExtractions() {
        context.cacheDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(EXTRACTION_DIRECTORY_PREFIX) }
            ?.forEach(File::deleteRecursively)
    }

    private companion object {
        const val EXTRACTION_DIRECTORY_PREFIX = "bugreport-"
        const val MAX_ENTRY_COUNT = 20_000
        const val MAX_EXTRACTED_BYTES = 2L * 1024L * 1024L * 1024L
    }
}

data class ExtractedBugreport(
    val root: File,
    val sourceName: String,
)
