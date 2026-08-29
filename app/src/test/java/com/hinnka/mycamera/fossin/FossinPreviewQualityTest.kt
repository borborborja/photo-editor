package com.hinnka.mycamera.fossin

import org.junit.Assert.assertEquals
import org.junit.Test

class FossinPreviewQualityTest {
    @Test
    fun `preview budget favours responsiveness for raw and complex stacks`() {
        assertEquals(FossinPreviewQuality.SIMPLE_MAX_EDGE, FossinPreviewQuality.maxEdge(false, 1, 0))
        assertEquals(FossinPreviewQuality.BALANCED_MAX_EDGE, FossinPreviewQuality.maxEdge(false, 5, 0))
        assertEquals(FossinPreviewQuality.HEAVY_MAX_EDGE, FossinPreviewQuality.maxEdge(false, 2, 2))
        assertEquals(FossinPreviewQuality.HEAVY_MAX_EDGE, FossinPreviewQuality.maxEdge(true, 0, 0))
        assertEquals(90L, FossinPreviewQuality.rawGestureDebounceMillis(true))
        assertEquals(0L, FossinPreviewQuality.rawGestureDebounceMillis(false))
    }
}
