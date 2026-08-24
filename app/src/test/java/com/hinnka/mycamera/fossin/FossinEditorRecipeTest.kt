package com.hinnka.mycamera.fossin

import com.hinnka.mycamera.model.ColorRecipeParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FossinEditorRecipeTest {
    @Test
    fun brushAndHealingStrokesRoundTripThroughActivityPersistenceCodec() {
        val brush = listOf(
            BrushStroke(
                points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.8f, 0.7f)),
                radius = 0.18f,
                exposure = -0.6f,
                saturation = 1.4f,
                warmth = 0.25f,
            ),
        )
        val healing = listOf(
            HealingStroke(
                points = listOf(NormalizedPoint(0.4f, 0.5f)),
                radius = 0.08f,
                strength = 0.9f,
            ),
        )

        assertEquals(brush, decodeBrushStrokes(encodeBrushStrokes(brush)))
        assertEquals(healing, decodeHealingStrokes(encodeHealingStrokes(healing)))
    }

    @Test
    fun malformedAndOutOfRangeSelectivePointsAreSafeToRestore() {
        val restored = decodeSelectivePoints("2.2:-1:0:4:3:4:-2|bad|0.5:0.5:0.2:0:1:1:0")

        assertEquals(2, restored.size)
        assertEquals(1f, restored[0].x, 0f)
        assertEquals(0f, restored[0].y, 0f)
        assertEquals(0.01f, restored[0].radius, 0f)
        assertEquals(2f, restored[0].contrast, 0f)
        assertEquals(2f, restored[0].saturation, 0f)
        assertEquals(-1f, restored[0].structure, 0f)
    }

    @Test
    fun identityCurvesStayDisabledAndEditedCurvesAreClampedForTheRenderer() {
        assertEquals(null, curveArray(defaultCurvePoints))

        val edited = listOf(
            CurvePoint(-0.2f, 1.2f),
            CurvePoint(0.5f, 0.4f),
            CurvePoint(1.2f, -0.1f),
        )
        assertArrayEquals(
            floatArrayOf(0f, 1f, 0.5f, 0.4f, 1f, 0f),
            curveArray(edited),
            0f,
        )
    }

    @Test
    fun snapEffectsAreIndependentAndDefaultRecipeRemainsAnIdentity() {
        val base = ColorRecipeParams(exposure = 0.15f, contrast = 1.1f)
        assertEquals(base, applySnapEffectRecipe(base, defaultSnapEffects))

        val dramatic = applySnapEffectRecipe(
            base,
            defaultSnapEffects + (SnapEffect.Drama to 1f),
        )
        assertTrue(dramatic.contrast > base.contrast)
        assertTrue(dramatic.clarity > base.clarity)
        assertFalse(dramatic.isDefault())
    }

    @Test
    fun tonalContrastValueIsBoundedAndIdentityWhenAllControlsAreZero() {
        assertEquals(0.27f, tonalContrastValue(0.27f, 0f, 0f, 0f), 0f)
        listOf(0f, 0.1f, 0.5f, 0.9f, 1f).forEach { luminance ->
            val value = tonalContrastValue(luminance, 1f, -1f, 1f)
            assertTrue("tonal contrast must stay normalized", value in 0f..1f)
        }
        assertTrue(tonalContrastValue(0.2f, 1f, 0f, 0f) < 0.2f)
        assertTrue(tonalContrastValue(0.8f, 0f, 0f, 1f) > 0.8f)
    }
}
