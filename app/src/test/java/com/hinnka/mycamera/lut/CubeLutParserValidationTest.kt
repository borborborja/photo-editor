package com.hinnka.mycamera.lut

import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class CubeLutParserValidationTest {
    @Test
    fun oversizedCubeDimensionIsRejectedBeforeAllocation() {
        assertRejected("LUT_3D_SIZE 1024\n")
    }

    @Test
    fun truncatedCubePayloadIsRejected() {
        assertRejected(
            buildString {
                appendLine("LUT_3D_SIZE 2")
                appendLine("0 0 0")
            },
        )
    }

    @Test
    fun extraCubeSamplesAreRejected() {
        assertRejected(
            buildString {
                appendLine("LUT_3D_SIZE 2")
                repeat(9) { appendLine("0 0 0") }
            },
        )
    }

    @Test
    fun invalidDomainIsRejectedInsteadOfProducingNanValues() {
        assertRejected(
            buildString {
                appendLine("DOMAIN_MIN 1 1 1")
                appendLine("DOMAIN_MAX 1 1 1")
                appendLine("LUT_3D_SIZE 2")
                appendLine("0 0 0")
            },
        )
    }

    private fun assertRejected(source: String) {
        try {
            CubeLutParser.parse(ByteArrayInputStream(source.toByteArray()))
            fail("Malformed .cube input should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected parser validation failure.
        }
    }
}
