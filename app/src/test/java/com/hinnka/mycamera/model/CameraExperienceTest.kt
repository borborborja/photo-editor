package com.hinnka.mycamera.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraExperienceTest {

    @Test
    fun `unknown persisted values safely use the beginner defaults`() {
        assertEquals(CameraExperience.BEGINNER, CameraExperience.fromPersistedName("legacy"))
        assertEquals(BeginnerSimulation.NATURAL, BeginnerSimulation.fromPersistedName("legacy"))
    }

    @Test
    fun `built in simulations keep predictable local recipe values`() {
        assertEquals(ColorRecipeParams.DEFAULT, BeginnerSimulation.NATURAL.recipe)
        assertEquals(0.18f, BeginnerSimulation.WARM.recipe.temperature)
        assertEquals(-0.16f, BeginnerSimulation.COOL.recipe.temperature)
        assertEquals(1.18f, BeginnerSimulation.VIVID.recipe.saturation)
        assertEquals(0f, BeginnerSimulation.MONO.recipe.saturation)
    }

    @Test
    fun `camera experience cannot change while video owns the camera`() {
        assertEquals(false, canChangeCameraExperience(isVideoRecording = true, isVideoProcessing = false))
        assertEquals(false, canChangeCameraExperience(isVideoRecording = false, isVideoProcessing = true))
        assertEquals(true, canChangeCameraExperience(isVideoRecording = false, isVideoProcessing = false))
    }

    @Test
    fun `beginner zoom only exposes stops supported by the active lens`() {
        assertEquals(listOf(1f), availableBeginnerZoomStops(0.9f, 1.1f))
        assertEquals(listOf(0.5f, 1f), availableBeginnerZoomStops(0.45f, 1.2f))
        assertEquals(listOf(1f, 2f), availableBeginnerZoomStops(0.9f, 2.2f))
    }

    @Test
    fun `beginner zoom retains a valid fallback for uncommon lens ranges`() {
        assertEquals(listOf(2.5f), availableBeginnerZoomStops(2.5f, 4f))
    }
}
