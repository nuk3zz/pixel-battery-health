package com.pixelbatteryhealth.data

import java.io.File

class BugreportTextFinder {
    fun findBestTextFile(root: File): File? {
        val txtFiles = root.walkTopDown()
            .filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
            .toList()

        if (txtFiles.isEmpty()) return null

        return txtFiles
            .map { file -> Candidate(file, score(file), file.length()) }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.size })
            .first()
            .file
    }

    private fun score(file: File): Int {
        val markers = mapOf(
            "Estimated battery capacity" to 8,
            "android.os.extra.CYCLE_COUNT" to 6,
            "Current Battery Service state" to 4,
            "DUMP OF SERVICE batterystats" to 4,
            "BUGREPORT" to 2,
        )

        return runCatching {
            file.bufferedReader().useLines { lines ->
                var score = 0
                val found = mutableSetOf<String>()
                for (line in lines) {
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
        }.getOrDefault(0)
    }

    private data class Candidate(
        val file: File,
        val score: Int,
        val size: Long,
    )
}
