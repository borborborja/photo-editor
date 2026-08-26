package com.hinnka.mycamera.fossin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun gestureStartsBySelectingOrAdjustingAccordingToItsFirstDirection() {
        assertEquals(
            SnapseedGesturePhase.Pending,
            snapseedInitialGesturePhase(4f, 3f, touchSlopPx = 14f),
        )
        assertEquals(
            SnapseedGesturePhase.Selecting,
            snapseedInitialGesturePhase(8f, -30f, touchSlopPx = 14f),
        )
        assertEquals(
            SnapseedGesturePhase.Adjusting,
            snapseedInitialGesturePhase(30f, -8f, touchSlopPx = 14f),
        )
    }

    @Test
    fun verticalSelectionCanTurnIntoHorizontalAdjustmentWithoutLifting() {
        assertFalse(
            snapseedShouldBeginHorizontalAdjustment(
                horizontalSinceSelectionPx = 18f,
                latestHorizontalDeltaPx = 3f,
                latestVerticalDeltaPx = 8f,
                turnSlopPx = 12f,
            ),
        )
        assertTrue(
            snapseedShouldBeginHorizontalAdjustment(
                horizontalSinceSelectionPx = 14f,
                latestHorizontalDeltaPx = 8f,
                latestVerticalDeltaPx = 2f,
                turnSlopPx = 12f,
            ),
        )
    }
}
