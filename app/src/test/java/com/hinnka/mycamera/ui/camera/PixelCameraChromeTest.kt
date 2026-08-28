package com.hinnka.mycamera.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelCameraChromeTest {

    @Test
    fun `zoom pill never offers an unreachable standard stop`() {
        assertEquals(listOf(1f, 2f), pixelZoomStops(0.9f, 2.2f))
        assertEquals(listOf(0.5f, 1f, 2f, 5f), pixelZoomStops(0.45f, 5.1f))
    }

    @Test
    fun `zoom pill retains a useful fallback for unusual lenses`() {
        assertEquals(listOf(3f), pixelZoomStops(3f, 4f))
    }
}
