package com.pixelbatteryhealth.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BugreportTextFinderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val finder = BugreportTextFinder()

    @Test
    fun markerRichFileBeatsLargerPlainText() {
        val root = temporaryFolder.newFolder("bugreport")
        val large = root.resolve("large.txt").apply {
            writeText("plain\n".repeat(2_000))
        }
        val nested = root.resolve("nested").apply { mkdirs() }
        val useful = nested.resolve("main.txt").apply {
            writeText(
                """
                    BUGREPORT
                    Estimated battery capacity: 4212 mAh
                    android.os.extra.CYCLE_COUNT=785
                """.trimIndent(),
            )
        }

        assertEquals(useful, finder.findBestTextFile(root))
        assertTrue(large.length() > useful.length())
    }

    @Test
    fun fallsBackToLargestTxt() {
        val root = temporaryFolder.newFolder("bugreport")
        val small = root.resolve("small.txt").apply { writeText("small") }
        val large = root.resolve("large.txt").apply { writeText("large".repeat(100)) }

        assertEquals(large, finder.findBestTextFile(root))
        assertTrue(small.length() < large.length())
    }

    @Test
    fun findsBatteryMarkerAfterFirstTwoThousandLines() {
        val root = temporaryFolder.newFolder("late-marker")
        val decoy = root.resolve("decoy.txt").apply {
            writeText("BUGREPORT\n" + "plain\n".repeat(3_000))
        }
        val useful = root.resolve("actual.txt").apply {
            writeText("plain\n".repeat(2_500) + "Estimated battery capacity: 4230 mAh")
        }

        assertEquals(useful, finder.findBestTextFile(root))
        assertTrue(decoy.length() > useful.length())
    }

    @Test
    fun returnsNullWhenNoTxtFilesExist() {
        val root = temporaryFolder.newFolder("bugreport")
        root.resolve("image.png").writeText("nope")

        assertNull(finder.findBestTextFile(root))
    }
}
