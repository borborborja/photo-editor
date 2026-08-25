package com.hinnka.mycamera.model

/**
 * The camera surface is deliberately split into two experiences.  Both use the
 * same Camera2 pipeline. The beginner surface deliberately uses its
 * single-frame JPEG path, while Pro keeps access to every capture option.
 */
enum class CameraExperience {
    BEGINNER,
    PRO;

    companion object {
        fun fromPersistedName(value: String?): CameraExperience {
            return entries.firstOrNull { it.name == value } ?: BEGINNER
        }
    }
}

/** A camera surface must not be replaced while its video recorder owns it. */
fun canChangeCameraExperience(
    isVideoRecording: Boolean,
    isVideoProcessing: Boolean,
): Boolean = !isVideoRecording && !isVideoProcessing

/**
 * Small, built-in looks for the beginner camera.  They are color recipes, not
 * LUT files: no external LUT is loaded or embedded when one is selected.
 */
enum class BeginnerSimulation(val recipe: ColorRecipeParams) {
    NATURAL(ColorRecipeParams.DEFAULT),
    WARM(
        ColorRecipeParams(
            temperature = 0.18f,
            saturation = 1.05f,
            contrast = 1.02f,
        )
    ),
    COOL(
        ColorRecipeParams(
            temperature = -0.16f,
            saturation = 0.96f,
            contrast = 1.04f,
        )
    ),
    VIVID(
        ColorRecipeParams(
            saturation = 1.18f,
            contrast = 1.08f,
            clarity = 0.08f,
        )
    ),
    MONO(
        ColorRecipeParams(
            saturation = 0f,
            contrast = 1.10f,
            highlights = -0.08f,
            shadows = 0.06f,
        )
    );

    companion object {
        fun fromPersistedName(value: String?): BeginnerSimulation {
            return entries.firstOrNull { it.name == value } ?: NATURAL
        }
    }
}
