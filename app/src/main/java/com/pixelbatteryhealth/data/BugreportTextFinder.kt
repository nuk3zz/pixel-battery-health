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
        val markers = listOf(
            "Estimated battery capacity",
            "android.os.extra.CYCLE_COUNT",
            "BUGREPORT",
        )

        return runCatching {
            file.bufferedReader().useLines { lines ->
                var score = 0
                val found = mutableSetOf<String>()
                lines.take(2_000).forEach { line ->
                    markers.forEach { marker ->
                        if (marker !in found && line.contains(marker, ignoreCase = true)) {
                            found += marker
                            score += 1
                        }
                    }
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
