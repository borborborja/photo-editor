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
}
