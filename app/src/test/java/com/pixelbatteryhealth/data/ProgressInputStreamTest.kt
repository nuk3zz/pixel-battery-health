package com.pixelbatteryhealth.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressInputStreamTest {
    @Test
    fun reportsBytesReadUntilComplete() {
        val source = ByteArray(32_768) { (it % 251).toByte() }
        val updates = mutableListOf<Long>()

        val result = ProgressInputStream(
            input = ByteArrayInputStream(source),
            onBytesRead = updates::add,
            checkCancelled = {},
        ).readBytes()

        assertTrue(source.contentEquals(result))
        assertTrue(updates.zipWithNext().all { (before, after) -> after > before })
        assertEquals(source.size.toLong(), updates.last())
    }
}
