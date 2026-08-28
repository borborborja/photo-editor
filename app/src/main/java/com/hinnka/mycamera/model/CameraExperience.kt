package com.hinnka.mycamera.model

import com.hinnka.mycamera.video.CaptureMode

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
 * Beginner deliberately has a small, familiar mode rail.  Video is now part
 * of that rail, while Quick Shot remains a Pro capture control instead of a
 * third always-visible mode.
 */
fun supportedCaptureModes(experience: CameraExperience): Set<CaptureMode> = when (experience) {
    CameraExperience.BEGINNER -> setOf(CaptureMode.PHOTO, CaptureMode.VIDEO)
    CameraExperience.PRO -> setOf(CaptureMode.PHOTO, CaptureMode.VIDEO, CaptureMode.QUICK_SHOT)
}

fun resolveCaptureModeForExperience(
    experience: CameraExperience,
    requestedMode: CaptureMode,
): CaptureMode = requestedMode.takeIf { it in supportedCaptureModes(experience) } ?: CaptureMode.PHOTO

/**
 * Returns only the familiar zoom stops that the current lens can genuinely
 * reach. A device with no ultra-wide or telephoto lens therefore never shows
 * a control that will be silently clamped by Camera2.
 */
fun availableBeginnerZoomStops(
    minimumZoom: Float,
    maximumZoom: Float,
): List<Float> {
    val lowerBound = minimumZoom.coerceAtLeast(0.1f)
    val upperBound = maximumZoom.coerceAtLeast(lowerBound)
    val standardStops = listOf(0.5f, 1f, 2f).filter { stop ->
        stop >= lowerBound - 0.05f && stop <= upperBound + 0.05f
    }

    return standardStops.ifEmpty { listOf(1f.coerceIn(lowerBound, upperBound)) }
}

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
