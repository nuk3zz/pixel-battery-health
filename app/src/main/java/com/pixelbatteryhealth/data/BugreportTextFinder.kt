package com.pixelbatteryhealth.data

import java.io.File
import kotlinx.coroutines.CancellationException

class BugreportTextFinder {
    fun findBestTextFile(
        root: File,
        onProgress: (Float) -> Unit = {},
        checkCancelled: () -> Unit = {},
    ): File? {
        val txtFiles = root.walkTopDown()
            .filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
            .toList()

        if (txtFiles.isEmpty()) return null

        val totalBytes = txtFiles.sumOf { it.length() }.coerceAtLeast(1L)
        var completedBytes = 0L

        return txtFiles
            .map { file ->
                checkCancelled()
                val fileSize = file.length()
                val candidate = Candidate(
                    file = file,
                    score = score(
                        file = file,
                        onBytesRead = { fileBytes ->
                            onProgress(((completedBytes + fileBytes).toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat())
                        },
                        checkCancelled = checkCancelled,
                    ),
                    size = fileSize,
                )
                completedBytes += fileSize
                onProgress((completedBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat())
                candidate
            }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.size })
            .first()
            .file
    }

    private fun score(
        file: File,
        onBytesRead: (Long) -> Unit,
        checkCancelled: () -> Unit,
    ): Int {
        val markers = mapOf(
            "Estimated battery capacity" to 8,
            "android.os.extra.CYCLE_COUNT" to 6,
            "Current Battery Service state" to 4,
            "DUMP OF SERVICE batterystats" to 4,
            "BUGREPORT" to 2,
        )

        return try {
            ProgressInputStream(file.inputStream(), onBytesRead, checkCancelled).bufferedReader().useLines { lines ->
                var score = 0
                val found = mutableSetOf<String>()
                for (line in lines) {
                    checkCancelled()
                    markers.forEach { (marker, weight) ->
                        if (marker !in found && line.contains(marker, ignoreCase = true)) {
                            found += marker
                            score += weight
                        }
                    }
                    if (found.size == markers.size) break
                }
                score
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            0
        }
    }

    private data class Candidate(
        val file: File,
        val score: Int,
        val size: Long,
    )
}
