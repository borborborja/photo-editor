package com.hinnka.mycamera.fossin

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapseedGestureTest {
    @Test
    fun verticalSelectionStopsAtTheFirstAndLastParameter() {
        assertEquals(
            0,
            snapseedParameterIndex(0, 3, SnapseedParameterDirection.Previous),
        )
        assertEquals(
            1,
            snapseedParameterIndex(0, 3, SnapseedParameterDirection.Next),
        )
        assertEquals(
            2,
            snapseedParameterIndex(2, 3, SnapseedParameterDirection.Next),
        )
    }

    @Test
    fun horizontalAdjustmentUsesTheWholeParameterRangeAndClampsIt() {
        assertEquals(0f, snapseedAdjustedValue(0f, -1f..1f, 0f, 240f), 0f)
        assertEquals(1f, snapseedAdjustedValue(0f, -1f..1f, 120f, 240f), 0f)
        assertEquals(-1f, snapseedAdjustedValue(0f, -1f..1f, -480f, 240f), 0f)
    }

    @Test
    fun verticalDragCanNavigateSeveralParametersWithoutLiftingTheFinger() {
        assertEquals(3, snapseedParameterIndexForDrag(1, 5, -104f, 52f))
        assertEquals(0, snapseedParameterIndexForDrag(1, 5, 500f, 52f))
        assertEquals(4, snapseedParameterIndexForDrag(3, 5, -500f, 52f))
    }

    @Test
    fun feedbackPercentageIsAlwaysNormalized() {
        assertEquals(0, snapseedValuePercent(-2f, -2f..2f))
        assertEquals(50, snapseedValuePercent(0f, -2f..2f))
        assertEquals(100, snapseedValuePercent(4f, -2f..2f))
    }
}
