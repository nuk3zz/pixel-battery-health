package com.pixelbatteryhealth.data

import java.io.FilterInputStream
import java.io.InputStream

internal class ProgressInputStream(
    input: InputStream,
    private val onBytesRead: (Long) -> Unit,
    private val checkCancelled: () -> Unit,
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int {
        checkCancelled()
        return super.read().also { value ->
            if (value >= 0) reportBytes(1)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkCancelled()
        return super.read(buffer, offset, length).also { count ->
            if (count > 0) reportBytes(count)
        }
    }

    private fun reportBytes(count: Int) {
        bytesRead += count
        onBytesRead(bytesRead)
    }
}
