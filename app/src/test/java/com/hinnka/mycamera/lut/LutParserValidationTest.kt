package com.hinnka.mycamera.lut

import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LutParserValidationTest {
    @Test
    fun oversizedDimensionIsRejectedBeforeAllocation() {
        assertRejected(plutBytes(version = 1, dimension = 1024, dataType = 0, payloadSize = 0))
    }

    @Test
    fun truncatedPayloadIsRejected() {
        assertRejected(plutBytes(version = 1, dimension = 2, dataType = 0, payloadSize = 23))
    }

    @Test
    fun unsupportedDataTypeIsRejected() {
        assertRejected(plutBytes(version = 1, dimension = 2, dataType = 2, payloadSize = 0))
    }

    @Test
    fun unsupportedVersionIsRejected() {
        assertRejected(plutBytes(version = 4, dimension = 2, dataType = 0, payloadSize = 0))
    }

    @Test
    fun trailingPayloadIsRejected() {
        val validPayload = plutBytes(version = 1, dimension = 2, dataType = 0)
        assertRejected(validPayload + byteArrayOf(1))
    }

    private fun assertRejected(bytes: ByteArray) {
        try {
            LutParser.parse(ByteArrayInputStream(bytes))
            fail("Malformed PLUT should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }

    private fun plutBytes(
        version: Int,
        dimension: Int,
        dataType: Int,
        payloadSize: Int = dimension * dimension * dimension * 3 * if (dataType == 1) 2 else 1,
    ): ByteArray {
        return ByteBuffer.allocate(16 + payloadSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("PLUT".toByteArray(Charsets.US_ASCII))
                putInt(version)
                putInt(dimension)
                putInt(dataType)
                repeat(payloadSize) { put(0) }
            }
            .array()
    }
}
