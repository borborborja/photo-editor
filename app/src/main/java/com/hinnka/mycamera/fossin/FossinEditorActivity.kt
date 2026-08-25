package com.hinnka.mycamera.fossin

import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hinnka.mycamera.MainActivity
import com.hinnka.mycamera.R
import com.hinnka.mycamera.lut.LutConfig
import com.hinnka.mycamera.lut.LutImageProcessor
import com.hinnka.mycamera.lut.LutParser
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.theme.PhotonCameraTheme
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.FileNotFoundException
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class FossinEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            PhotonCameraTheme {
                FossinEditor(
                    initialUri = intent.sharedImageUri() ?: intent.data,
                    onOpenCamera = { startActivity(Intent(this, MainActivity::class.java)) },
                    onFinish = { finish() }
                )
            }
        }
    }
}

private fun Intent.sharedImageUri(): Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) {
    getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelableExtra(Intent.EXTRA_STREAM)
}

private enum class EditorTool {
    Looks, Tune, Details, TonalContrast, Curves, WhiteBalance, Crop, Expand, Perspective, Rotate, Color, Hsl, Selective, Brush, Healing, LensBlur, Vignette, Grain, Bloom, Effects, HdrScape, GlamourGlow, Drama, Vintage, GrainyFilm, Retrolux, Grunge, BlackWhite, Noir, Portrait, FaceEnhance, HeadPose, Frame, DoubleExposure, Text
}

private const val FOSSIN_EDITOR_PREFERENCES = "fossin_editor_preferences"
private const val FOSSIN_GESTURE_MODE_ENABLED = "gesture_mode_enabled"
private const val GESTURE_TOUCH_SLOP_DP = 14f
private const val GESTURE_FULL_RANGE_DP = 240f

internal enum class SnapseedParameterDirection { Previous, Next }

/** Pure gesture math kept separate from Compose so the touch behaviour stays testable. */
internal fun snapseedParameterIndex(
    currentIndex: Int,
    parameterCount: Int,
    direction: SnapseedParameterDirection,
): Int {
    if (parameterCount <= 0) return 0
    val current = currentIndex.coerceIn(0, parameterCount - 1)
    return when (direction) {
        SnapseedParameterDirection.Previous -> (current - 1).coerceAtLeast(0)
        SnapseedParameterDirection.Next -> (current + 1).coerceAtMost(parameterCount - 1)
    }
}

internal fun snapseedAdjustedValue(
    startValue: Float,
    range: ClosedFloatingPointRange<Float>,
    horizontalDistancePx: Float,
    fullRangeDistancePx: Float,
): Float {
    val safeDistance = fullRangeDistancePx.coerceAtLeast(1f)
    val rangeSize = range.endInclusive - range.start
    return (startValue + horizontalDistancePx / safeDistance * rangeSize)
        .coerceIn(range.start, range.endInclusive)
}

internal fun snapseedValuePercent(value: Float, range: ClosedFloatingPointRange<Float>): Int {
    val rangeSize = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    return (((value - range.start) / rangeSize) * 100f).roundToInt().coerceIn(0, 100)
}

private enum class SnapseedGestureAxis { Horizontal, Vertical }

private data class GestureParameter(
    val key: String,
    @StringRes val labelRes: Int,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val update: (EditorState, Float) -> EditorState,
)
internal enum class SnapEffect {
    HdrScape, GlamourGlow, Drama, Vintage, GrainyFilm, Retrolux, Grunge, BlackWhite, Noir, Portrait
}
internal val defaultSnapEffects: Map<SnapEffect, Float> = SnapEffect.values().associateWith { 0f }
private enum class HslChannel { Red, Orange, Yellow, Green, Cyan, Blue, Purple, Magenta }
private enum class CurveChannel { Master, Red, Green, Blue }
internal data class CurvePoint(val x: Float, val y: Float)
internal val defaultCurvePoints = listOf(
    CurvePoint(0f, 0f),
    CurvePoint(0.25f, 0.25f),
    CurvePoint(0.5f, 0.5f),
    CurvePoint(0.75f, 0.75f),
    CurvePoint(1f, 1f),
)
private val defaultCurves: Map<CurveChannel, List<CurvePoint>> = CurveChannel.values().associateWith { defaultCurvePoints }
private data class HslValue(val hue: Float = 0f, val chroma: Float = 0f, val lightness: Float = 0f)
internal data class NormalizedPoint(val x: Float, val y: Float)
internal data class BrushStroke(
    val points: List<NormalizedPoint>,
    val radius: Float,
    val exposure: Float,
    val saturation: Float,
    val warmth: Float,
)
internal data class HealingStroke(
    val points: List<NormalizedPoint>,
    val radius: Float,
    val strength: Float,
)
internal data class SelectivePoint(
    val x: Float,
    val y: Float,
    val radius: Float,
    val exposure: Float,
    val contrast: Float,
    val saturation: Float,
    val structure: Float,
)
private enum class FrameStyle(val color: Int) {
    White(android.graphics.Color.WHITE),
    Black(android.graphics.Color.BLACK),
    Warm(android.graphics.Color.rgb(197, 154, 106)),
    Gray(android.graphics.Color.rgb(190, 190, 190)),
    Cream(android.graphics.Color.rgb(255, 243, 214)),
}
private enum class ExpandStyle { Black, White, Warm, Stretch }
private enum class LensBlurShape { Radial, Linear }
private enum class OverlayBlendMode { Normal, Lighten, Darken, Multiply, Screen, Overlay }
private enum class TextColor(val argb: Int) {
    White(android.graphics.Color.WHITE),
    Black(android.graphics.Color.BLACK),
    Warm(android.graphics.Color.rgb(255, 190, 120)),
    Accent(android.graphics.Color.rgb(255, 107, 53)),
}
private enum class TextStyle { Plain, Bold, Outline, Neon, Stamp, Typewriter }
private enum class CropMode(val ratio: Float?) {
    Original(null), Free(null), Square(1f), ThreeTwo(3f / 2f), FourThree(4f / 3f), FiveFour(5f / 4f), SevenFive(7f / 5f), SixteenNine(16f / 9f)
}
private data class LutChoice(val name: String, val config: LutConfig)

private data class DisplayedImageRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private fun displayedImageRect(viewWidth: Float, viewHeight: Float, bitmap: Bitmap?): DisplayedImageRect {
    val safeWidth = viewWidth.coerceAtLeast(1f)
    val safeHeight = viewHeight.coerceAtLeast(1f)
    val bitmapAspect = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: safeWidth / safeHeight
    val boxAspect = safeWidth / safeHeight
    val displayedWidth = if (bitmapAspect > boxAspect) safeWidth else safeHeight * bitmapAspect
    val displayedHeight = if (bitmapAspect > boxAspect) safeWidth / bitmapAspect else safeHeight
    return DisplayedImageRect(
        left = (safeWidth - displayedWidth) / 2f,
        top = (safeHeight - displayedHeight) / 2f,
        width = displayedWidth,
        height = displayedHeight,
    )
}

internal enum class StylePreset(val labelRes: Int, val recipe: ColorRecipeParams?) {
    None(R.string.fossin_style_none, null),
    Drama(R.string.fossin_style_drama, ColorRecipeParams(contrast = 1.22f, saturation = 1.08f, clarity = 0.22f, vignette = -0.12f)),
    Vintage(R.string.fossin_style_vintage, ColorRecipeParams(contrast = 1.05f, saturation = 0.82f, temperature = 0.18f, fade = 0.18f, filmGrain = 0.12f, vignette = -0.16f)),
    Noir(R.string.fossin_style_noir, ColorRecipeParams(contrast = 1.28f, saturation = 0f, toneToe = -0.12f, toneShoulder = 0.1f, vignette = -0.2f)),
    BlackWhite(R.string.fossin_style_bw, ColorRecipeParams(contrast = 1.16f, saturation = 0f, clarity = 0.12f, filmGrain = 0.08f)),
    Grunge(R.string.fossin_style_grunge, ColorRecipeParams(contrast = 1.3f, saturation = 0.72f, clarity = 0.28f, filmGrain = 0.28f, vignette = -0.22f)),
    Retrolux(R.string.fossin_style_retrolux, ColorRecipeParams(contrast = 1.08f, saturation = 1.12f, temperature = 0.12f, fade = 0.22f, filmGrain = 0.2f, bloom = 0.08f)),
    Hdr(R.string.fossin_style_hdr, ColorRecipeParams(contrast = 1.3f, saturation = 1.12f, clarity = 0.36f, highlights = -0.12f, shadows = 0.18f, vignette = -0.08f));

    fun applyTo(base: ColorRecipeParams): ColorRecipeParams {
        val preset = recipe ?: return base
        return base.copy(
            exposure = base.exposure + preset.exposure,
            contrast = base.contrast * preset.contrast,
            saturation = (base.saturation * preset.saturation).coerceIn(0f, 2f),
            temperature = (base.temperature + preset.temperature).coerceIn(-1f, 1f),
            tint = (base.tint + preset.tint).coerceIn(-1f, 1f),
            fade = (base.fade + preset.fade).coerceIn(0f, 1f),
            color = (base.color + preset.color).coerceIn(-1f, 1f),
            highlights = (base.highlights + preset.highlights).coerceIn(-1f, 1f),
            shadows = (base.shadows + preset.shadows).coerceIn(-1f, 1f),
            toneToe = (base.toneToe + preset.toneToe).coerceIn(-1f, 1f),
            toneShoulder = (base.toneShoulder + preset.toneShoulder).coerceIn(-1f, 1f),
            clarity = (base.clarity + preset.clarity).coerceIn(-1f, 1f),
            vignette = (base.vignette + preset.vignette).coerceIn(-1f, 1f),
            filmGrain = (base.filmGrain + preset.filmGrain).coerceIn(0f, 1f),
            bloom = (base.bloom + preset.bloom).coerceIn(0f, 1f),
        )
    }
}

/** All editable values live in one immutable recipe. This keeps previews non-destructive and
 * makes undo/redo reliable: the source bitmap is never overwritten. */
private data class EditorState(
    val lut: LutChoice? = null,
    val lutName: String? = null,
    val lutUri: String? = null,
    val style: StylePreset = StylePreset.None,
    val intensity: Float = 1f,
    val exposure: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val ambiance: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val fade: Float = 0f,
    val vibrance: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val toneToe: Float = 0f,
    val toneShoulder: Float = 0f,
    val tonePivot: Float = 0f,
    val detail: Float = 0f,
    val sharpening: Float = 0f,
    val tonalContrastShadows: Float = 0f,
    val tonalContrastMidtones: Float = 0f,
    val tonalContrastHighlights: Float = 0f,
    val vignette: Float = 0f,
    val vignetteX: Float = 0.5f,
    val vignetteY: Float = 0.5f,
    val vignetteRadius: Float = 0.7f,
    val vignetteInner: Float = 0f,
    val grain: Float = 0f,
    val bloom: Float = 0f,
    val flash: Float = 0f,
    val bleachBypass: Float = 0f,
    val softLight: Float = 0f,
    val halation: Float = 0f,
    val chromaticAberration: Float = 0f,
    val noise: Float = 0f,
    val lowRes: Float = 0f,
    val hsl: Map<HslChannel, HslValue> = HslChannel.values().associateWith { HslValue() },
    val curves: Map<CurveChannel, List<CurvePoint>> = defaultCurves,
    val snapEffects: Map<SnapEffect, Float> = defaultSnapEffects,
    val portraitSpotlight: Float = 0f,
    val portraitSmoothing: Float = 0f,
    val portraitEyeClarity: Float = 0f,
    val frameWidth: Float = 0f,
    val frameStyle: FrameStyle = FrameStyle.White,
    val expandAmount: Float = 0f,
    val expandStyle: ExpandStyle = ExpandStyle.Stretch,
    val selectiveX: Float = 0.5f,
    val selectiveY: Float = 0.5f,
    val selectiveRadius: Float = 0.3f,
    val selectiveExposure: Float = 0f,
    val selectiveContrast: Float = 1f,
    val selectiveSaturation: Float = 1f,
    val selectiveStructure: Float = 0f,
    val selectivePoints: List<SelectivePoint> = emptyList(),
    val brushX: Float = 0.5f,
    val brushY: Float = 0.5f,
    val brushRadius: Float = 0.25f,
    val brushExposure: Float = 0f,
    val brushSaturation: Float = 1f,
    val brushWarmth: Float = 0f,
    val healingX: Float = 0.5f,
    val healingY: Float = 0.5f,
    val healingRadius: Float = 0.08f,
    val healingStrength: Float = 0f,
    val brushStrokes: List<BrushStroke> = emptyList(),
    val healingStrokes: List<HealingStroke> = emptyList(),
    val lensBlurX: Float = 0.5f,
    val lensBlurY: Float = 0.5f,
    val lensBlurRadius: Float = 0.25f,
    val lensBlurStrength: Float = 0f,
    val lensBlurTransition: Float = 0.35f,
    val lensBlurAngle: Float = 0f,
    val lensBlurShape: LensBlurShape = LensBlurShape.Radial,
    val overlayUri: String? = null,
    val overlayAlpha: Float = 0f,
    val overlayBlendMode: OverlayBlendMode = OverlayBlendMode.Normal,
    val text: String = "",
    val textSize: Float = 0.08f,
    val textOpacity: Float = 1f,
    val textRotation: Float = 0f,
    val textColor: TextColor = TextColor.White,
    val textStyle: TextStyle = TextStyle.Bold,
    val perspectiveHorizontal: Float = 0f,
    val perspectiveVertical: Float = 0f,
    val perspectiveRotate: Float = 0f,
    val perspectiveScale: Float = 0f,
    val headPoseHorizontal: Float = 0f,
    val headPoseVertical: Float = 0f,
    val headPoseTilt: Float = 0f,
    val rotation: Int = 0,
    val rotationFine: Float = 0f,
    val cropMode: CropMode = CropMode.Original,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
)

private val editorStateSaver = Saver<EditorState, Bundle>(
    save = { state ->
        Bundle().apply {
            putString("style", state.style.name)
            putString("lutName", state.lutName)
            putString("lutUri", state.lutUri)
            putFloat("intensity", state.intensity)
            putFloat("exposure", state.exposure)
            putFloat("contrast", state.contrast)
            putFloat("saturation", state.saturation)
            putFloat("ambiance", state.ambiance)
            putFloat("warmth", state.warmth)
            putFloat("tint", state.tint)
            putFloat("fade", state.fade)
            putFloat("vibrance", state.vibrance)
            putFloat("highlights", state.highlights)
            putFloat("shadows", state.shadows)
            putFloat("toneToe", state.toneToe)
            putFloat("toneShoulder", state.toneShoulder)
            putFloat("tonePivot", state.tonePivot)
            putFloat("detail", state.detail)
            putFloat("sharpening", state.sharpening)
            putFloat("tonalContrastShadows", state.tonalContrastShadows)
            putFloat("tonalContrastMidtones", state.tonalContrastMidtones)
            putFloat("tonalContrastHighlights", state.tonalContrastHighlights)
            putFloat("vignette", state.vignette)
            putFloat("vignetteX", state.vignetteX)
            putFloat("vignetteY", state.vignetteY)
            putFloat("vignetteRadius", state.vignetteRadius)
            putFloat("vignetteInner", state.vignetteInner)
            putFloat("grain", state.grain)
            putFloat("bloom", state.bloom)
            putFloat("flash", state.flash)
            putFloat("bleachBypass", state.bleachBypass)
            putFloat("softLight", state.softLight)
            putFloat("halation", state.halation)
            putFloat("chromaticAberration", state.chromaticAberration)
            putFloat("noise", state.noise)
            putFloat("lowRes", state.lowRes)
            putFloatArray("hsl", HslChannel.values().flatMap { channel ->
                val value = state.hsl[channel] ?: HslValue()
                listOf(value.hue, value.chroma, value.lightness)
            }.toFloatArray())
            putFloatArray("curves", CurveChannel.values().flatMap { channel ->
                (state.curves[channel] ?: defaultCurvePoints).flatMap { point -> listOf(point.x, point.y) }
            }.toFloatArray())
            putFloatArray("snapEffects", SnapEffect.values().map { state.snapEffects[it] ?: 0f }.toFloatArray())
            putFloat("portraitSpotlight", state.portraitSpotlight)
            putFloat("portraitSmoothing", state.portraitSmoothing)
            putFloat("portraitEyeClarity", state.portraitEyeClarity)
            putFloat("frameWidth", state.frameWidth)
            putString("frameStyle", state.frameStyle.name)
            putFloat("expandAmount", state.expandAmount)
            putString("expandStyle", state.expandStyle.name)
            putFloat("selectiveX", state.selectiveX)
            putFloat("selectiveY", state.selectiveY)
            putFloat("selectiveRadius", state.selectiveRadius)
            putFloat("selectiveExposure", state.selectiveExposure)
            putFloat("selectiveContrast", state.selectiveContrast)
            putFloat("selectiveSaturation", state.selectiveSaturation)
            putFloat("selectiveStructure", state.selectiveStructure)
            putString("selectivePoints", encodeSelectivePoints(state.selectivePoints))
            putFloat("brushX", state.brushX)
            putFloat("brushY", state.brushY)
            putFloat("brushRadius", state.brushRadius)
            putFloat("brushExposure", state.brushExposure)
            putFloat("brushSaturation", state.brushSaturation)
            putFloat("brushWarmth", state.brushWarmth)
            putFloat("healingX", state.healingX)
            putFloat("healingY", state.healingY)
            putFloat("healingRadius", state.healingRadius)
            putFloat("healingStrength", state.healingStrength)
            putString("brushStrokes", encodeBrushStrokes(state.brushStrokes))
            putString("healingStrokes", encodeHealingStrokes(state.healingStrokes))
            putFloat("lensBlurX", state.lensBlurX)
            putFloat("lensBlurY", state.lensBlurY)
            putFloat("lensBlurRadius", state.lensBlurRadius)
            putFloat("lensBlurStrength", state.lensBlurStrength)
            putFloat("lensBlurTransition", state.lensBlurTransition)
            putFloat("lensBlurAngle", state.lensBlurAngle)
            putString("lensBlurShape", state.lensBlurShape.name)
            putString("overlayUri", state.overlayUri)
            putFloat("overlayAlpha", state.overlayAlpha)
            putString("overlayBlendMode", state.overlayBlendMode.name)
            putString("text", state.text)
            putFloat("textSize", state.textSize)
            putFloat("textOpacity", state.textOpacity)
            putFloat("textRotation", state.textRotation)
            putString("textColor", state.textColor.name)
            putString("textStyle", state.textStyle.name)
            putFloat("perspectiveHorizontal", state.perspectiveHorizontal)
            putFloat("perspectiveVertical", state.perspectiveVertical)
            putFloat("perspectiveRotate", state.perspectiveRotate)
            putFloat("perspectiveScale", state.perspectiveScale)
            putFloat("headPoseHorizontal", state.headPoseHorizontal)
            putFloat("headPoseVertical", state.headPoseVertical)
            putFloat("headPoseTilt", state.headPoseTilt)
            putInt("rotation", state.rotation)
            putFloat("rotationFine", state.rotationFine)
            putString("cropMode", state.cropMode.name)
            putFloat("cropLeft", state.cropLeft)
            putFloat("cropTop", state.cropTop)
            putFloat("cropRight", state.cropRight)
            putFloat("cropBottom", state.cropBottom)
        }
    },
    restore = { bundle ->
        val hslValues = bundle.getFloatArray("hsl") ?: FloatArray(HslChannel.values().size * 3)
        val hsl = HslChannel.values().mapIndexed { index, channel ->
            channel to HslValue(
                hslValues.getOrElse(index * 3) { 0f },
                hslValues.getOrElse(index * 3 + 1) { 0f },
                hslValues.getOrElse(index * 3 + 2) { 0f },
            )
        }.toMap()
        val curveValues = bundle.getFloatArray("curves")
        val curves = if (curveValues == null) {
            defaultCurves
        } else {
            CurveChannel.values().mapIndexed { channelIndex, channel ->
                val offset = channelIndex * defaultCurvePoints.size * 2
                channel to defaultCurvePoints.indices.map { pointIndex ->
                    val pointOffset = offset + pointIndex * 2
                    CurvePoint(
                        curveValues.getOrElse(pointOffset) { defaultCurvePoints[pointIndex].x },
                        curveValues.getOrElse(pointOffset + 1) { defaultCurvePoints[pointIndex].y },
                    )
                }
            }.toMap()
        }
        val snapEffectValues = bundle.getFloatArray("snapEffects") ?: FloatArray(SnapEffect.values().size)
        val snapEffects = SnapEffect.values().mapIndexed { index, effect ->
            effect to snapEffectValues.getOrElse(index) { 0f }
        }.toMap()
        EditorState(
            lutName = bundle.getString("lutName"),
            lutUri = bundle.getString("lutUri"),
            style = enumOrDefault(bundle.getString("style"), StylePreset.None),
            intensity = bundle.getFloat("intensity", 1f),
            exposure = bundle.getFloat("exposure", 0f),
            contrast = bundle.getFloat("contrast", 1f),
            saturation = bundle.getFloat("saturation", 1f),
            ambiance = bundle.getFloat("ambiance", 0f),
            warmth = bundle.getFloat("warmth", 0f),
            tint = bundle.getFloat("tint", 0f),
            fade = bundle.getFloat("fade", 0f),
            vibrance = bundle.getFloat("vibrance", 0f),
            highlights = bundle.getFloat("highlights", 0f),
            shadows = bundle.getFloat("shadows", 0f),
            toneToe = bundle.getFloat("toneToe", 0f),
            toneShoulder = bundle.getFloat("toneShoulder", 0f),
            tonePivot = bundle.getFloat("tonePivot", 0f),
            detail = bundle.getFloat("detail", 0f),
            sharpening = bundle.getFloat("sharpening", 0f),
            tonalContrastShadows = bundle.getFloat("tonalContrastShadows", 0f),
            tonalContrastMidtones = bundle.getFloat("tonalContrastMidtones", 0f),
            tonalContrastHighlights = bundle.getFloat("tonalContrastHighlights", 0f),
            vignette = bundle.getFloat("vignette", 0f),
            vignetteX = bundle.getFloat("vignetteX", 0.5f),
            vignetteY = bundle.getFloat("vignetteY", 0.5f),
            vignetteRadius = bundle.getFloat("vignetteRadius", 0.7f),
            vignetteInner = bundle.getFloat("vignetteInner", 0f),
            grain = bundle.getFloat("grain", 0f),
            bloom = bundle.getFloat("bloom", 0f),
            flash = bundle.getFloat("flash", 0f),
            bleachBypass = bundle.getFloat("bleachBypass", 0f),
            softLight = bundle.getFloat("softLight", 0f),
            halation = bundle.getFloat("halation", 0f),
            chromaticAberration = bundle.getFloat("chromaticAberration", 0f),
            noise = bundle.getFloat("noise", 0f),
            lowRes = bundle.getFloat("lowRes", 0f),
            hsl = hsl,
            curves = curves,
            snapEffects = snapEffects,
            portraitSpotlight = bundle.getFloat("portraitSpotlight", 0f),
            portraitSmoothing = bundle.getFloat("portraitSmoothing", 0f),
            portraitEyeClarity = bundle.getFloat("portraitEyeClarity", 0f),
            frameWidth = bundle.getFloat("frameWidth", 0f),
            frameStyle = enumOrDefault(bundle.getString("frameStyle"), FrameStyle.White),
            expandAmount = bundle.getFloat("expandAmount", 0f),
            expandStyle = enumOrDefault(bundle.getString("expandStyle"), ExpandStyle.Stretch),
            selectiveX = bundle.getFloat("selectiveX", 0.5f),
            selectiveY = bundle.getFloat("selectiveY", 0.5f),
            selectiveRadius = bundle.getFloat("selectiveRadius", 0.3f),
            selectiveExposure = bundle.getFloat("selectiveExposure", 0f),
            selectiveContrast = bundle.getFloat("selectiveContrast", 1f),
            selectiveSaturation = bundle.getFloat("selectiveSaturation", 1f),
            selectiveStructure = bundle.getFloat("selectiveStructure", 0f),
            selectivePoints = decodeSelectivePoints(bundle.getString("selectivePoints")),
            brushX = bundle.getFloat("brushX", 0.5f),
            brushY = bundle.getFloat("brushY", 0.5f),
            brushRadius = bundle.getFloat("brushRadius", 0.25f),
            brushExposure = bundle.getFloat("brushExposure", 0f),
            brushSaturation = bundle.getFloat("brushSaturation", 1f),
            brushWarmth = bundle.getFloat("brushWarmth", 0f),
            healingX = bundle.getFloat("healingX", 0.5f),
            healingY = bundle.getFloat("healingY", 0.5f),
            healingRadius = bundle.getFloat("healingRadius", 0.08f),
            healingStrength = bundle.getFloat("healingStrength", 0f),
            brushStrokes = decodeBrushStrokes(bundle.getString("brushStrokes")),
            healingStrokes = decodeHealingStrokes(bundle.getString("healingStrokes")),
            lensBlurX = bundle.getFloat("lensBlurX", 0.5f),
            lensBlurY = bundle.getFloat("lensBlurY", 0.5f),
            lensBlurRadius = bundle.getFloat("lensBlurRadius", 0.25f),
            lensBlurStrength = bundle.getFloat("lensBlurStrength", 0f),
            lensBlurTransition = bundle.getFloat("lensBlurTransition", 0.35f),
            lensBlurAngle = bundle.getFloat("lensBlurAngle", 0f),
            lensBlurShape = enumOrDefault(bundle.getString("lensBlurShape"), LensBlurShape.Radial),
            overlayUri = bundle.getString("overlayUri"),
            overlayAlpha = bundle.getFloat("overlayAlpha", 0f),
            overlayBlendMode = enumOrDefault(bundle.getString("overlayBlendMode"), OverlayBlendMode.Normal),
            text = bundle.getString("text").orEmpty(),
            textSize = bundle.getFloat("textSize", 0.08f),
            textOpacity = bundle.getFloat("textOpacity", 1f),
            textRotation = bundle.getFloat("textRotation", 0f),
            textColor = enumOrDefault(bundle.getString("textColor"), TextColor.White),
            textStyle = enumOrDefault(bundle.getString("textStyle"), TextStyle.Bold),
            perspectiveHorizontal = bundle.getFloat("perspectiveHorizontal", 0f),
            perspectiveVertical = bundle.getFloat("perspectiveVertical", 0f),
            perspectiveRotate = bundle.getFloat("perspectiveRotate", 0f),
            perspectiveScale = bundle.getFloat("perspectiveScale", 0f),
            headPoseHorizontal = bundle.getFloat("headPoseHorizontal", 0f),
            headPoseVertical = bundle.getFloat("headPoseVertical", 0f),
            headPoseTilt = bundle.getFloat("headPoseTilt", 0f),
            rotation = bundle.getInt("rotation", 0),
            rotationFine = bundle.getFloat("rotationFine", 0f),
            cropMode = enumOrDefault(bundle.getString("cropMode"), CropMode.Original),
            cropLeft = bundle.getFloat("cropLeft", 0f),
            cropTop = bundle.getFloat("cropTop", 0f),
            cropRight = bundle.getFloat("cropRight", 1f),
            cropBottom = bundle.getFloat("cropBottom", 1f),
        )
    },
)

private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

internal fun encodeBrushStrokes(strokes: List<BrushStroke>): String = strokes.joinToString("|") { stroke ->
    listOf(
        stroke.radius,
        stroke.exposure,
        stroke.saturation,
        stroke.warmth,
        stroke.points.joinToString(",") { "${it.x}:${it.y}" },
    ).joinToString(";")
}

internal fun decodeBrushStrokes(value: String?): List<BrushStroke> = value.orEmpty().split('|').mapNotNull { encoded ->
    if (encoded.isBlank()) return@mapNotNull null
    val parts = encoded.split(';')
    val radius = parts.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
    val exposure = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
    val saturation = parts.getOrNull(2)?.toFloatOrNull() ?: return@mapNotNull null
    val warmth = parts.getOrNull(3)?.toFloatOrNull() ?: return@mapNotNull null
    val points = parts.getOrNull(4).orEmpty().split(',').mapNotNull { point ->
        val values = point.split(':')
        val x = values.getOrNull(0)?.toFloatOrNull()
        val y = values.getOrNull(1)?.toFloatOrNull()
        if (x == null || y == null) null else NormalizedPoint(x, y)
    }
    BrushStroke(points, radius, exposure, saturation, warmth).takeIf { points.isNotEmpty() }
}

internal fun encodeHealingStrokes(strokes: List<HealingStroke>): String = strokes.joinToString("|") { stroke ->
    listOf(
        stroke.radius,
        stroke.strength,
        stroke.points.joinToString(",") { "${it.x}:${it.y}" },
    ).joinToString(";")
}

internal fun decodeHealingStrokes(value: String?): List<HealingStroke> = value.orEmpty().split('|').mapNotNull { encoded ->
    if (encoded.isBlank()) return@mapNotNull null
    val parts = encoded.split(';')
    val radius = parts.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
    val strength = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
    val points = parts.getOrNull(2).orEmpty().split(',').mapNotNull { point ->
        val values = point.split(':')
        val x = values.getOrNull(0)?.toFloatOrNull()
        val y = values.getOrNull(1)?.toFloatOrNull()
        if (x == null || y == null) null else NormalizedPoint(x, y)
    }
    HealingStroke(points, radius, strength).takeIf { points.isNotEmpty() }
}

internal fun encodeSelectivePoints(points: List<SelectivePoint>): String = points.joinToString("|") { point ->
    listOf(point.x, point.y, point.radius, point.exposure, point.contrast, point.saturation, point.structure).joinToString(":")
}

internal fun decodeSelectivePoints(value: String?): List<SelectivePoint> = value.orEmpty().split('|').mapNotNull { encoded ->
    if (encoded.isBlank()) return@mapNotNull null
    val values = encoded.split(':').mapNotNull(String::toFloatOrNull)
    if (values.size < 7) return@mapNotNull null
    SelectivePoint(values[0].coerceIn(0f, 1f), values[1].coerceIn(0f, 1f), values[2].coerceIn(0.01f, 1f), values[3], values[4].coerceIn(0f, 2f), values[5].coerceIn(0f, 2f), values[6].coerceIn(-1f, 1f))
}

private fun EditorState.withSyncedPrimarySelective(): EditorState {
    if (selectivePoints.isEmpty()) return this
    val points = selectivePoints.toMutableList()
    points[0] = SelectivePoint(selectiveX, selectiveY, selectiveRadius, selectiveExposure, selectiveContrast, selectiveSaturation, selectiveStructure)
    return copy(selectivePoints = points)
}

private fun snapseedGestureParameters(
    tool: EditorTool,
    state: EditorState,
    selectedHslChannel: HslChannel,
    hasOverlay: Boolean,
): List<GestureParameter> {
    fun parameter(
        key: String,
        @StringRes labelRes: Int,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        update: (EditorState, Float) -> EditorState,
    ) = GestureParameter(key, labelRes, value, range, update)

    fun snapEffect(effect: SnapEffect, key: String, @StringRes labelRes: Int) = parameter(
        key = key,
        labelRes = labelRes,
        value = state.snapEffects[effect] ?: 0f,
        range = 0f..1f,
    ) { editor, value -> editor.copy(snapEffects = editor.snapEffects + (effect to value)) }

    return when (tool) {
        EditorTool.Looks -> if (state.lut == null) emptyList() else listOf(
            parameter("looks-intensity", R.string.fossin_strength, state.intensity, 0f..1f) { editor, value -> editor.copy(intensity = value) },
        )
        EditorTool.Tune -> listOf(
            parameter("tune-exposure", R.string.fossin_exposure, state.exposure, -2f..2f) { editor, value -> editor.copy(exposure = value) },
            parameter("tune-contrast", R.string.fossin_contrast, state.contrast, 0.5f..1.5f) { editor, value -> editor.copy(contrast = value) },
            parameter("tune-saturation", R.string.fossin_saturation, state.saturation, 0f..2f) { editor, value -> editor.copy(saturation = value) },
            parameter("tune-ambiance", R.string.fossin_ambiance, state.ambiance, -1f..1f) { editor, value -> editor.copy(ambiance = value) },
            parameter("tune-warmth", R.string.fossin_warmth, state.warmth, -1f..1f) { editor, value -> editor.copy(warmth = value) },
            parameter("tune-highlights", R.string.recipe_param_highlights, state.highlights, -1f..1f) { editor, value -> editor.copy(highlights = value) },
            parameter("tune-shadows", R.string.recipe_param_shadows, state.shadows, -1f..1f) { editor, value -> editor.copy(shadows = value) },
        )
        EditorTool.Details -> listOf(
            parameter("details-structure", R.string.fossin_structure, state.detail, -1f..1f) { editor, value -> editor.copy(detail = value) },
            parameter("details-sharpening", R.string.fossin_sharpening, state.sharpening, 0f..1f) { editor, value -> editor.copy(sharpening = value) },
        )
        EditorTool.TonalContrast -> listOf(
            parameter("tonal-shadows", R.string.fossin_tonal_shadows, state.tonalContrastShadows, -1f..1f) { editor, value -> editor.copy(tonalContrastShadows = value) },
            parameter("tonal-midtones", R.string.fossin_tonal_midtones, state.tonalContrastMidtones, -1f..1f) { editor, value -> editor.copy(tonalContrastMidtones = value) },
            parameter("tonal-highlights", R.string.fossin_tonal_highlights, state.tonalContrastHighlights, -1f..1f) { editor, value -> editor.copy(tonalContrastHighlights = value) },
        )
        EditorTool.Curves -> listOf(
            parameter("curves-shadows", R.string.recipe_param_shadows, state.toneToe, -1f..1f) { editor, value -> editor.copy(toneToe = value) },
            parameter("curves-highlights", R.string.recipe_param_highlights, state.toneShoulder, -1f..1f) { editor, value -> editor.copy(toneShoulder = value) },
            parameter("curves-midpoint", R.string.fossin_midpoint, state.tonePivot, -1f..1f) { editor, value -> editor.copy(tonePivot = value) },
        )
        EditorTool.WhiteBalance -> listOf(
            parameter("white-balance-temperature", R.string.fossin_temperature, state.warmth, -1f..1f) { editor, value -> editor.copy(warmth = value) },
            parameter("white-balance-tint", R.string.fossin_tint, state.tint, -1f..1f) { editor, value -> editor.copy(tint = value) },
        )
        EditorTool.Crop -> emptyList()
        EditorTool.Expand -> listOf(
            parameter("expand-amount", R.string.fossin_expand_amount, state.expandAmount, 0f..1f) { editor, value -> editor.copy(expandAmount = value) },
        )
        EditorTool.Perspective -> listOf(
            parameter("perspective-horizontal", R.string.fossin_horizontal, state.perspectiveHorizontal, -1f..1f) { editor, value -> editor.copy(perspectiveHorizontal = value) },
            parameter("perspective-vertical", R.string.fossin_vertical, state.perspectiveVertical, -1f..1f) { editor, value -> editor.copy(perspectiveVertical = value) },
            parameter("perspective-rotate", R.string.fossin_rotate, state.perspectiveRotate, -1f..1f) { editor, value -> editor.copy(perspectiveRotate = value) },
            parameter("perspective-scale", R.string.fossin_scale, state.perspectiveScale, -1f..1f) { editor, value -> editor.copy(perspectiveScale = value) },
        )
        EditorTool.Rotate -> listOf(
            parameter("rotate-straighten", R.string.fossin_straighten, state.rotationFine, -1f..1f) { editor, value -> editor.copy(rotationFine = value) },
        )
        EditorTool.Color -> listOf(
            parameter("color-tint", R.string.recipe_param_tint, state.tint, -1f..1f) { editor, value -> editor.copy(tint = value) },
            parameter("color-fade", R.string.recipe_param_fade, state.fade, 0f..1f) { editor, value -> editor.copy(fade = value) },
            parameter("color-vibrance", R.string.recipe_param_color, state.vibrance, -1f..1f) { editor, value -> editor.copy(vibrance = value) },
        )
        EditorTool.Hsl -> {
            val hslValue = state.hsl[selectedHslChannel] ?: HslValue()
            listOf(
                parameter("hsl-${selectedHslChannel.name}-hue", R.string.fossin_hue, hslValue.hue, -1f..1f) { editor, value ->
                    val current = editor.hsl[selectedHslChannel] ?: HslValue()
                    editor.copy(hsl = editor.hsl + (selectedHslChannel to current.copy(hue = value)))
                },
                parameter("hsl-${selectedHslChannel.name}-chroma", R.string.fossin_chroma, hslValue.chroma, -1f..1f) { editor, value ->
                    val current = editor.hsl[selectedHslChannel] ?: HslValue()
                    editor.copy(hsl = editor.hsl + (selectedHslChannel to current.copy(chroma = value)))
                },
                parameter("hsl-${selectedHslChannel.name}-lightness", R.string.fossin_lightness, hslValue.lightness, -1f..1f) { editor, value ->
                    val current = editor.hsl[selectedHslChannel] ?: HslValue()
                    editor.copy(hsl = editor.hsl + (selectedHslChannel to current.copy(lightness = value)))
                },
            )
        }
        EditorTool.Selective -> listOf(
            parameter("selective-x", R.string.fossin_center_x, state.selectiveX, 0f..1f) { editor, value -> editor.copy(selectiveX = value).withSyncedPrimarySelective() },
            parameter("selective-y", R.string.fossin_center_y, state.selectiveY, 0f..1f) { editor, value -> editor.copy(selectiveY = value).withSyncedPrimarySelective() },
            parameter("selective-radius", R.string.fossin_radius, state.selectiveRadius, 0.05f..1f) { editor, value -> editor.copy(selectiveRadius = value).withSyncedPrimarySelective() },
            parameter("selective-exposure", R.string.fossin_exposure, state.selectiveExposure, -2f..2f) { editor, value -> editor.copy(selectiveExposure = value).withSyncedPrimarySelective() },
            parameter("selective-contrast", R.string.fossin_contrast, state.selectiveContrast, 0f..2f) { editor, value -> editor.copy(selectiveContrast = value).withSyncedPrimarySelective() },
            parameter("selective-saturation", R.string.fossin_saturation, state.selectiveSaturation, 0f..2f) { editor, value -> editor.copy(selectiveSaturation = value).withSyncedPrimarySelective() },
            parameter("selective-structure", R.string.fossin_structure, state.selectiveStructure, -1f..1f) { editor, value -> editor.copy(selectiveStructure = value).withSyncedPrimarySelective() },
        )
        EditorTool.Brush -> listOf(
            parameter("brush-x", R.string.fossin_center_x, state.brushX, 0f..1f) { editor, value -> editor.copy(brushX = value) },
            parameter("brush-y", R.string.fossin_center_y, state.brushY, 0f..1f) { editor, value -> editor.copy(brushY = value) },
            parameter("brush-radius", R.string.fossin_radius, state.brushRadius, 0.03f..1f) { editor, value -> editor.copy(brushRadius = value) },
            parameter("brush-exposure", R.string.fossin_exposure, state.brushExposure, -2f..2f) { editor, value -> editor.copy(brushExposure = value) },
            parameter("brush-saturation", R.string.fossin_saturation, state.brushSaturation, 0f..2f) { editor, value -> editor.copy(brushSaturation = value) },
            parameter("brush-warmth", R.string.fossin_warmth, state.brushWarmth, -1f..1f) { editor, value -> editor.copy(brushWarmth = value) },
        )
        EditorTool.Healing -> listOf(
            parameter("healing-x", R.string.fossin_center_x, state.healingX, 0f..1f) { editor, value -> editor.copy(healingX = value) },
            parameter("healing-y", R.string.fossin_center_y, state.healingY, 0f..1f) { editor, value -> editor.copy(healingY = value) },
            parameter("healing-radius", R.string.fossin_radius, state.healingRadius, 0.01f..0.3f) { editor, value -> editor.copy(healingRadius = value) },
            parameter("healing-strength", R.string.fossin_strength, state.healingStrength, 0f..1f) { editor, value -> editor.copy(healingStrength = value) },
        )
        EditorTool.LensBlur -> buildList {
            add(parameter("lens-blur-x", R.string.fossin_center_x, state.lensBlurX, 0f..1f) { editor, value -> editor.copy(lensBlurX = value) })
            add(parameter("lens-blur-y", R.string.fossin_center_y, state.lensBlurY, 0f..1f) { editor, value -> editor.copy(lensBlurY = value) })
            add(parameter("lens-blur-radius", R.string.fossin_radius, state.lensBlurRadius, 0.03f..1f) { editor, value -> editor.copy(lensBlurRadius = value) })
            add(parameter("lens-blur-transition", R.string.fossin_transition, state.lensBlurTransition, 0.03f..1f) { editor, value -> editor.copy(lensBlurTransition = value) })
            if (state.lensBlurShape == LensBlurShape.Linear) add(parameter("lens-blur-angle", R.string.fossin_angle, state.lensBlurAngle, -1f..1f) { editor, value -> editor.copy(lensBlurAngle = value) })
            add(parameter("lens-blur-strength", R.string.fossin_strength, state.lensBlurStrength, 0f..1f) { editor, value -> editor.copy(lensBlurStrength = value) })
        }
        EditorTool.Vignette -> listOf(
            parameter("vignette-outer", R.string.fossin_outer_brightness, state.vignette, -1f..1f) { editor, value -> editor.copy(vignette = value) },
            parameter("vignette-inner", R.string.fossin_inner_brightness, state.vignetteInner, 0f..1f) { editor, value -> editor.copy(vignetteInner = value) },
            parameter("vignette-x", R.string.fossin_center_x, state.vignetteX, 0f..1f) { editor, value -> editor.copy(vignetteX = value) },
            parameter("vignette-y", R.string.fossin_center_y, state.vignetteY, 0f..1f) { editor, value -> editor.copy(vignetteY = value) },
            parameter("vignette-radius", R.string.fossin_radius, state.vignetteRadius, 0.1f..1.4f) { editor, value -> editor.copy(vignetteRadius = value) },
        )
        EditorTool.Grain -> listOf(parameter("grain", R.string.recipe_param_film_grain, state.grain, 0f..1f) { editor, value -> editor.copy(grain = value) })
        EditorTool.Bloom -> listOf(parameter("bloom", R.string.recipe_param_bloom, state.bloom, 0f..1f) { editor, value -> editor.copy(bloom = value) })
        EditorTool.Effects -> listOf(
            parameter("effects-flash", R.string.recipe_param_flash, state.flash, 0f..1f) { editor, value -> editor.copy(flash = value) },
            parameter("effects-bleach", R.string.recipe_param_bleach_bypass, state.bleachBypass, 0f..1f) { editor, value -> editor.copy(bleachBypass = value) },
            parameter("effects-soft-light", R.string.recipe_param_soft_light, state.softLight, 0f..1f) { editor, value -> editor.copy(softLight = value) },
            parameter("effects-halation", R.string.recipe_param_hdf, state.halation, 0f..1f) { editor, value -> editor.copy(halation = value) },
            parameter("effects-chromatic", R.string.recipe_param_chromatic_aberration, state.chromaticAberration, 0f..1f) { editor, value -> editor.copy(chromaticAberration = value) },
            parameter("effects-noise", R.string.recipe_param_noise, state.noise, 0f..1f) { editor, value -> editor.copy(noise = value) },
            parameter("effects-low-res", R.string.recipe_param_low_res, state.lowRes, 0f..1f) { editor, value -> editor.copy(lowRes = value) },
        )
        EditorTool.HdrScape -> listOf(snapEffect(SnapEffect.HdrScape, "hdr-scape", R.string.fossin_hdr_scape))
        EditorTool.GlamourGlow -> listOf(snapEffect(SnapEffect.GlamourGlow, "glamour-glow", R.string.fossin_glamour_glow))
        EditorTool.Drama -> listOf(snapEffect(SnapEffect.Drama, "drama", R.string.fossin_drama))
        EditorTool.Vintage -> listOf(snapEffect(SnapEffect.Vintage, "vintage", R.string.fossin_vintage))
        EditorTool.GrainyFilm -> listOf(snapEffect(SnapEffect.GrainyFilm, "grainy-film", R.string.fossin_grainy_film))
        EditorTool.Retrolux -> listOf(snapEffect(SnapEffect.Retrolux, "retrolux", R.string.fossin_retrolux))
        EditorTool.Grunge -> listOf(snapEffect(SnapEffect.Grunge, "grunge", R.string.fossin_grunge))
        EditorTool.BlackWhite -> listOf(snapEffect(SnapEffect.BlackWhite, "black-white", R.string.fossin_black_white))
        EditorTool.Noir -> listOf(snapEffect(SnapEffect.Noir, "noir", R.string.fossin_noir))
        EditorTool.Portrait -> listOf(snapEffect(SnapEffect.Portrait, "portrait", R.string.fossin_portrait))
        EditorTool.FaceEnhance -> listOf(
            parameter("face-spotlight", R.string.fossin_face_spotlight, state.portraitSpotlight, 0f..1f) { editor, value -> editor.copy(portraitSpotlight = value) },
            parameter("face-smoothing", R.string.fossin_skin_smoothing, state.portraitSmoothing, 0f..1f) { editor, value -> editor.copy(portraitSmoothing = value) },
            parameter("face-eye-clarity", R.string.fossin_eye_clarity, state.portraitEyeClarity, 0f..1f) { editor, value -> editor.copy(portraitEyeClarity = value) },
        )
        EditorTool.HeadPose -> listOf(
            parameter("head-pose-horizontal", R.string.fossin_horizontal, state.headPoseHorizontal, -1f..1f) { editor, value -> editor.copy(headPoseHorizontal = value) },
            parameter("head-pose-vertical", R.string.fossin_vertical, state.headPoseVertical, -1f..1f) { editor, value -> editor.copy(headPoseVertical = value) },
            parameter("head-pose-tilt", R.string.fossin_tilt, state.headPoseTilt, -1f..1f) { editor, value -> editor.copy(headPoseTilt = value) },
        )
        EditorTool.Frame -> listOf(parameter("frame-width", R.string.fossin_frame_width, state.frameWidth, 0f..1f) { editor, value -> editor.copy(frameWidth = value) })
        EditorTool.DoubleExposure -> if (!hasOverlay) emptyList() else listOf(
            parameter("double-exposure-opacity", R.string.fossin_opacity, state.overlayAlpha, 0f..1f) { editor, value -> editor.copy(overlayAlpha = value) },
        )
        EditorTool.Text -> listOf(
            parameter("text-size", R.string.fossin_text_size, state.textSize, 0.03f..0.2f) { editor, value -> editor.copy(textSize = value) },
            parameter("text-opacity", R.string.fossin_opacity, state.textOpacity, 0f..1f) { editor, value -> editor.copy(textOpacity = value) },
            parameter("text-rotation", R.string.fossin_rotate, state.textRotation, -1f..1f) { editor, value -> editor.copy(textRotation = value) },
        )
    }
}

@Composable
private fun FossinEditor(initialUri: Uri?, onOpenCamera: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var overlay by remember { mutableStateOf<Bitmap?>(null) }
    var sourceUri by rememberSaveable { mutableStateOf<Uri?>(initialUri) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var tool by remember { mutableStateOf(EditorTool.Looks) }
    var editState by rememberSaveable(stateSaver = editorStateSaver) { mutableStateOf(EditorState()) }
    val undoStack = remember { mutableStateListOf<EditorState>() }
    val redoStack = remember { mutableStateListOf<EditorState>() }
    var showOriginal by remember { mutableStateOf(false) }
    var isRendering by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var builtIns by remember { mutableStateOf<List<LutChoice>>(emptyList()) }
    var renderVersion by remember { mutableStateOf(0) }
    var gestureBase by remember { mutableStateOf<EditorState?>(null) }
    var cropDragStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var selectiveDragIndex by remember { mutableStateOf<Int?>(null) }
    val gesturePreferences = remember(context) {
        context.applicationContext.getSharedPreferences(FOSSIN_EDITOR_PREFERENCES, Context.MODE_PRIVATE)
    }
    var gestureModeEnabled by remember {
        mutableStateOf(gesturePreferences.getBoolean(FOSSIN_GESTURE_MODE_ENABLED, true))
    }
    var selectedGestureParameterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHslChannel by remember { mutableStateOf(HslChannel.Red) }
    var showGestureFeedback by remember { mutableStateOf(false) }
    var gestureFeedbackToken by remember { mutableStateOf(0) }
    val processor = remember { LutImageProcessor(context.applicationContext) }
    fun updateEdit(transform: (EditorState) -> EditorState) {
        val next = transform(editState)
        if (next == editState) return
        undoStack.add(editState)
        if (undoStack.size > 48) undoStack.removeAt(0)
        editState = next
        redoStack.clear()
        renderVersion += 1
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(editState)
        editState = undoStack.removeAt(undoStack.lastIndex)
        renderVersion += 1
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(editState)
        editState = redoStack.removeAt(redoStack.lastIndex)
        renderVersion += 1
    }
    fun resetEdit() {
        if (editState == EditorState()) return
        undoStack.add(editState)
        overlay = null
        editState = EditorState()
        redoStack.clear()
        renderVersion += 1
    }
    fun beginGesture() {
        if (gestureBase == null) gestureBase = editState
    }
    fun previewEdit(transform: (EditorState) -> EditorState) {
        val next = transform(editState)
        if (next == editState) return
        editState = next
        renderVersion += 1
    }
    fun finishGesture() {
        val base = gestureBase ?: return
        if (base != editState) {
            undoStack.add(base)
            if (undoStack.size > 48) undoStack.removeAt(0)
            redoStack.clear()
        }
        gestureBase = null
    }
    fun normalizedPoint(offset: Offset, width: Float, height: Float, bitmap: Bitmap?): NormalizedPoint {
        val rect = displayedImageRect(width, height, bitmap)
        return NormalizedPoint(
            ((offset.x - rect.left) / rect.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
            ((offset.y - rect.top) / rect.height.coerceAtLeast(1f)).coerceIn(0f, 1f),
        )
    }
    fun addBrushPoint(point: NormalizedPoint, start: Boolean = false) {
        beginGesture()
        previewEdit { state ->
            val strokes = if (start || state.brushStrokes.isEmpty()) {
                state.brushStrokes + BrushStroke(listOf(point), state.brushRadius, state.brushExposure, state.brushSaturation, state.brushWarmth)
            } else {
                state.brushStrokes.dropLast(1) + state.brushStrokes.last().copy(points = state.brushStrokes.last().points + point)
            }
            state.copy(brushStrokes = strokes)
        }
    }
    fun addHealingPoint(point: NormalizedPoint, start: Boolean = false) {
        beginGesture()
        previewEdit { state ->
            val strokes = if (start || state.healingStrokes.isEmpty()) {
                state.healingStrokes + HealingStroke(listOf(point), state.healingRadius, state.healingStrength.coerceAtLeast(0.75f))
            } else {
                state.healingStrokes.dropLast(1) + state.healingStrokes.last().copy(points = state.healingStrokes.last().points + point)
            }
            state.copy(healingStrokes = strokes)
        }
    }
    fun acceptImage(bitmap: Bitmap, resetEdits: Boolean = true) {
        source = bitmap
        rendered = null
        if (resetEdits) {
            overlay = null
            editState = EditorState()
            undoStack.clear()
            redoStack.clear()
        }
        gestureBase = null
        showOriginal = false
        tool = EditorTool.Looks
    }
    DisposableEffect(processor) {
        onDispose { processor.release() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            persistReadPermission(context, selectedUri)
            scope.launch {
                loadImage(context, selectedUri) { bitmap ->
                    sourceUri = selectedUri
                    acceptImage(bitmap)
                }
            }
        }
    }
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            persistReadPermission(context, selectedUri)
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(selectedUri)?.use {
                            LutParser.parse(it, lutDisplayName(context, selectedUri))
                        } ?: throw FileNotFoundException()
                    }
                }.onSuccess {
                    updateEdit { state ->
                        val name = lutDisplayName(context, selectedUri)
                        state.copy(lut = LutChoice(name, it), lutName = name, lutUri = selectedUri.toString())
                    }
                }.onFailure {
                    Toast.makeText(context, R.string.fossin_lut_import_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val overlayPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            persistReadPermission(context, selectedUri)
            scope.launch {
                loadImage(context, selectedUri) { bitmap ->
                    overlay = bitmap
                    updateEdit {
                        it.copy(
                            overlayUri = selectedUri.toString(),
                            overlayAlpha = 0.5f,
                        )
                    }
                }
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        uri?.let { target ->
            scope.launch {
                val input = source ?: return@launch
                isExporting = true
                var fullSizeInput: Bitmap? = null
                var output: Bitmap = input
                var saved = false
                try {
                    val exportInput = sourceUri?.let { uriToExport ->
                        runCatching { loadBitmap(context, uriToExport, maxEdge = 8192) }.getOrNull()
                    } ?: input
                    fullSizeInput = exportInput
                    output = withContext(Dispatchers.Default) {
                        renderEditorBitmap(processor, exportInput, editState, overlay)
                    }
                    saved = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(target)?.use { output.compress(Bitmap.CompressFormat.JPEG, 96, it) } == true
                    }
                } catch (_: Throwable) {
                    saved = false
                } finally {
                    fullSizeInput?.let { if (it !== input && it !== output && !it.isRecycled) it.recycle() }
                    if (output !== rendered && output !== input && !output.isRecycled) output.recycle()
                    isExporting = false
                }
                Toast.makeText(context, if (saved) R.string.fossin_export_done else R.string.fossin_export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    fun shareEditedImage() {
        val input = source ?: return
        scope.launch {
            isExporting = true
            var fullSizeInput: Bitmap? = null
            var output: Bitmap = input
            var sharedFile: File? = null
            try {
                val exportInput = sourceUri?.let { uriToExport ->
                    runCatching { loadBitmap(context, uriToExport, maxEdge = 8192) }.getOrNull()
                } ?: input
                fullSizeInput = exportInput
                output = withContext(Dispatchers.Default) {
                    renderEditorBitmap(processor, exportInput, editState, overlay)
                }
                val shareDirectory = File(context.cacheDir, "shared").apply { mkdirs() }
                val shareFile = File(shareDirectory, "photo-editor-${System.currentTimeMillis()}.jpg")
                sharedFile = shareFile
                withContext(Dispatchers.IO) {
                    FileOutputStream(shareFile).use { stream ->
                        check(output.compress(Bitmap.CompressFormat.JPEG, 96, stream)) { "JPEG encoding failed" }
                    }
                }
                val shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, null))
            } catch (_: Throwable) {
                sharedFile?.delete()
                Toast.makeText(context, R.string.fossin_share_failed, Toast.LENGTH_LONG).show()
            } finally {
                fullSizeInput?.let { if (it !== input && it !== output && !it.isRecycled) it.recycle() }
                if (output !== rendered && output !== input && !output.isRecycled) output.recycle()
                isExporting = false
            }
        }
    }

    LaunchedEffect(Unit) {
        builtIns = withContext(Dispatchers.IO) {
            // The FOSS editor exposes every bundled preset, including the upstream entries
            // marked as VIP; there is no purchase gate in this offline distribution.
            LutParser.listAvailableLuts(context).mapNotNull { info ->
                runCatching { LutChoice(info.getName(), LutParser.parseFromAssets(context, info.fileName)) }.getOrNull()
            }
        }
    }
    LaunchedEffect(sourceUri, initialUri) {
        val uri = sourceUri ?: initialUri ?: return@LaunchedEffect
        if (source != null) return@LaunchedEffect
        loadImage(context, uri) { bitmap ->
            sourceUri = uri
            acceptImage(bitmap, resetEdits = editState == EditorState())
        }
    }
    LaunchedEffect(builtIns) {
        val savedName = editState.lutName
        if (editState.lut == null && savedName != null) {
            builtIns.firstOrNull { it.name == savedName }?.let { restored ->
                editState = editState.copy(lut = restored, lutUri = null)
                renderVersion += 1
            }
        }
    }
    LaunchedEffect(editState.lutUri, editState.lutName) {
        val uriText = editState.lutUri ?: return@LaunchedEffect
        if (editState.lut != null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(Uri.parse(uriText))?.use {
                    LutParser.parse(it, editState.lutName ?: context.getString(R.string.fossin_imported_lut))
                } ?: throw FileNotFoundException()
            }
        }.onSuccess { config ->
            editState = editState.copy(lut = LutChoice(editState.lutName ?: context.getString(R.string.fossin_imported_lut), config))
            renderVersion += 1
        }
    }
    LaunchedEffect(editState.overlayUri) {
        val uriText = editState.overlayUri ?: return@LaunchedEffect
        if (overlay != null) return@LaunchedEffect
        val restored = runCatching {
            loadBitmap(context, Uri.parse(uriText), maxEdge = 4096)
        }.getOrNull() ?: return@LaunchedEffect
        if (overlay == null && editState.overlayUri == uriText) {
            overlay = restored
            renderVersion += 1
        } else if (!restored.isRecycled) {
            restored.recycle()
        }
    }
    LaunchedEffect(source, editState, overlay, renderVersion) {
        val input = source ?: return@LaunchedEffect
        isRendering = true
        rendered = withContext(Dispatchers.Default) {
            try {
                renderEditorBitmap(processor, input, editState, overlay)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                input
            }
        }
        isRendering = false
    }

    BackHandler { onFinish() }
    val currentEditState by rememberUpdatedState(editState)
    val gestureParameters = snapseedGestureParameters(tool, editState, selectedHslChannel, overlay != null)
    val activeGestureParameter = gestureParameters.firstOrNull { it.key == selectedGestureParameterKey }
        ?: gestureParameters.firstOrNull()
    val currentGestureParameters by rememberUpdatedState(gestureParameters)
    val currentSelectedGestureParameterKey by rememberUpdatedState(selectedGestureParameterKey)
    fun refreshGestureFeedback() {
        showGestureFeedback = true
        gestureFeedbackToken += 1
    }
    fun setGestureMode(enabled: Boolean) {
        gestureModeEnabled = enabled
        gesturePreferences.edit().putBoolean(FOSSIN_GESTURE_MODE_ENABLED, enabled).apply()
        if (!enabled) showGestureFeedback = false
    }
    LaunchedEffect(gestureFeedbackToken) {
        if (gestureFeedbackToken == 0) return@LaunchedEffect
        val token = gestureFeedbackToken
        delay(1500)
        if (gestureFeedbackToken == token) showGestureFeedback = false
    }
    val image = if (showOriginal || (tool == EditorTool.Crop && editState.cropMode == CropMode.Free)) source else rendered ?: source
    Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), color = Color(0xFF0B0B0C)) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                hasImage = source != null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                showingOriginal = showOriginal,
                onBack = onFinish,
                onImport = { imagePicker.launch(arrayOf("image/*")) },
                onExport = { exportPicker.launch("photo-editor-edit.jpg") },
                onShare = { shareEditedImage() },
                onUndo = ::undo,
                onRedo = ::redo,
                onReset = ::resetEdit,
                onToggleOriginal = { showOriginal = !showOriginal }
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .pointerInput(tool, editState.cropMode, gestureModeEnabled, source != null) {
                        val cropGesture = tool == EditorTool.Crop && editState.cropMode == CropMode.Free
                        val selectiveGesture = tool == EditorTool.Selective
                        val lensBlurGesture = tool == EditorTool.LensBlur
                        val supportsGestureMode = gestureModeEnabled && source != null && currentGestureParameters.isNotEmpty()
                        if (supportsGestureMode) {
                            var axis: SnapseedGestureAxis? = null
                            var totalHorizontal = 0f
                            var totalVertical = 0f
                            var horizontalStartValue = 0f
                            var horizontalParameter: GestureParameter? = null
                            detectDragGestures(
                                onDragStart = {
                                    axis = null
                                    totalHorizontal = 0f
                                    totalVertical = 0f
                                    horizontalParameter = null
                                },
                                onDrag = { change, dragAmount ->
                                    totalHorizontal += dragAmount.x
                                    totalVertical += dragAmount.y
                                    if (axis == null) {
                                        val touchSlop = GESTURE_TOUCH_SLOP_DP.dp.toPx()
                                        if (maxOf(kotlin.math.abs(totalHorizontal), kotlin.math.abs(totalVertical)) < touchSlop) {
                                            return@detectDragGestures
                                        }
                                        if (kotlin.math.abs(totalVertical) > kotlin.math.abs(totalHorizontal)) {
                                            axis = SnapseedGestureAxis.Vertical
                                            val parameters = currentGestureParameters
                                            val currentIndex = parameters.indexOfFirst { it.key == currentSelectedGestureParameterKey }
                                                .takeIf { it >= 0 } ?: 0
                                            val direction = if (totalVertical < 0f) {
                                                SnapseedParameterDirection.Next
                                            } else {
                                                SnapseedParameterDirection.Previous
                                            }
                                            selectedGestureParameterKey = parameters[
                                                snapseedParameterIndex(currentIndex, parameters.size, direction)
                                            ].key
                                            refreshGestureFeedback()
                                        } else {
                                            axis = SnapseedGestureAxis.Horizontal
                                            horizontalParameter = currentGestureParameters.firstOrNull {
                                                it.key == currentSelectedGestureParameterKey
                                            } ?: currentGestureParameters.first()
                                            horizontalStartValue = horizontalParameter?.value ?: 0f
                                            beginGesture()
                                        }
                                    }
                                    if (axis == SnapseedGestureAxis.Horizontal) {
                                        horizontalParameter?.let { parameter ->
                                            val value = snapseedAdjustedValue(
                                                startValue = horizontalStartValue,
                                                range = parameter.range,
                                                horizontalDistancePx = totalHorizontal,
                                                fullRangeDistancePx = GESTURE_FULL_RANGE_DP.dp.toPx(),
                                            )
                                            previewEdit { state -> parameter.update(state, value) }
                                            refreshGestureFeedback()
                                        }
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (axis == SnapseedGestureAxis.Horizontal) finishGesture()
                                },
                                onDragCancel = {
                                    if (axis == SnapseedGestureAxis.Horizontal) finishGesture()
                                },
                            )
                        } else {
                            if (tool != EditorTool.Brush && tool != EditorTool.Healing && !cropGesture && !selectiveGesture && !lensBlurGesture) return@pointerInput
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val point = normalizedPoint(offset, size.width.toFloat(), size.height.toFloat(), if (cropGesture) source else image)
                                    when {
                                        tool == EditorTool.Brush -> addBrushPoint(point, start = true)
                                        tool == EditorTool.Healing -> addHealingPoint(point, start = true)
                                        selectiveGesture -> {
                                            beginGesture()
                                            val existing = currentEditState.selectivePoints
                                            if (existing.isEmpty()) {
                                                selectiveDragIndex = 0
                                                previewEdit { state ->
                                                    state.copy(
                                                        selectiveX = point.x,
                                                        selectiveY = point.y,
                                                        selectivePoints = listOf(SelectivePoint(point.x, point.y, state.selectiveRadius, state.selectiveExposure, state.selectiveContrast, state.selectiveSaturation, state.selectiveStructure)),
                                                    )
                                                }
                                            } else {
                                                selectiveDragIndex = existing.indices.minByOrNull { index ->
                                                    val candidate = existing[index]
                                                    val dx = candidate.x - point.x
                                                    val dy = candidate.y - point.y
                                                    dx * dx + dy * dy
                                                }
                                            }
                                        }
                                        lensBlurGesture -> {
                                            beginGesture()
                                            previewEdit { state -> state.copy(lensBlurX = point.x, lensBlurY = point.y) }
                                        }
                                        cropGesture -> {
                                            beginGesture()
                                            cropDragStart = point
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val point = normalizedPoint(change.position, size.width.toFloat(), size.height.toFloat(), if (cropGesture) source else image)
                                    when {
                                        tool == EditorTool.Brush -> addBrushPoint(point)
                                        tool == EditorTool.Healing -> addHealingPoint(point)
                                        selectiveGesture -> selectiveDragIndex?.let { selectedIndex ->
                                            previewEdit { state ->
                                                val points = state.selectivePoints.toMutableList()
                                                if (selectedIndex in points.indices) {
                                                    points[selectedIndex] = points[selectedIndex].copy(x = point.x, y = point.y)
                                                    state.copy(selectiveX = point.x, selectiveY = point.y, selectivePoints = points)
                                                } else state.copy(selectiveX = point.x, selectiveY = point.y)
                                            }
                                        }
                                        lensBlurGesture -> previewEdit { state -> state.copy(lensBlurX = point.x, lensBlurY = point.y) }
                                        cropGesture -> cropDragStart?.let { start ->
                                            previewEdit { state ->
                                                state.copy(
                                                    cropMode = CropMode.Free,
                                                    cropLeft = minOf(start.x, point.x),
                                                    cropTop = minOf(start.y, point.y),
                                                    cropRight = maxOf(start.x, point.x),
                                                    cropBottom = maxOf(start.y, point.y),
                                                )
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    cropDragStart = null
                                    selectiveDragIndex = null
                                    finishGesture()
                                },
                                onDragCancel = {
                                    cropDragStart = null
                                    selectiveDragIndex = null
                                    finishGesture()
                                },
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (image == null) EmptyEditor({ imagePicker.launch(arrayOf("image/*")) }, onOpenCamera)
                else Image(image.asImageBitmap(), stringResource(R.string.fossin_preview), Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
                if (source != null && (tool == EditorTool.Brush || tool == EditorTool.Healing)) {
                    ComposeCanvas(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
                        val markerColor = if (tool == EditorTool.Brush) Color(0xFFFF8A5C) else Color(0xFF8CC8FF)
                        val points = if (tool == EditorTool.Brush) editState.brushStrokes.flatMap { it.points } else editState.healingStrokes.flatMap { it.points }
                        val rect = displayedImageRect(size.width, size.height, image)
                        points.forEach { point ->
                            drawCircle(
                                markerColor,
                                radius = 5f,
                                center = Offset(rect.left + point.x * rect.width, rect.top + point.y * rect.height),
                            )
                        }
                    }
                }
                if (source != null && tool == EditorTool.Selective) {
                    ComposeCanvas(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
                        val points = editState.selectivePoints.ifEmpty { listOf(SelectivePoint(editState.selectiveX, editState.selectiveY, editState.selectiveRadius, editState.selectiveExposure, editState.selectiveContrast, editState.selectiveSaturation, editState.selectiveStructure)) }
                        val rect = displayedImageRect(size.width, size.height, image)
                        points.forEach { point ->
                            val center = Offset(rect.left + point.x * rect.width, rect.top + point.y * rect.height)
                            drawCircle(Color(0xFFFFC857), radius = 8f, center = center)
                            drawCircle(Color.Black, radius = 12f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                        }
                    }
                }
                if (source != null && tool == EditorTool.LensBlur) {
                    ComposeCanvas(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
                        val rect = displayedImageRect(size.width, size.height, image)
                        val center = Offset(rect.left + editState.lensBlurX * rect.width, rect.top + editState.lensBlurY * rect.height)
                        if (editState.lensBlurShape == LensBlurShape.Radial) {
                            drawCircle(Color(0xFFFFC857), radius = editState.lensBlurRadius * minOf(rect.width, rect.height), center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                        } else {
                            val half = maxOf(rect.width, rect.height)
                            val angle = editState.lensBlurAngle * Math.PI.toFloat()
                            val direction = Offset(kotlin.math.cos(angle), kotlin.math.sin(angle))
                            drawLine(Color(0xFFFFC857), center - direction * half, center + direction * half, strokeWidth = 2f)
                        }
                    }
                }
                if (source != null && tool == EditorTool.Crop && editState.cropMode == CropMode.Free) {
                    ComposeCanvas(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
                        val rect = displayedImageRect(size.width, size.height, source)
                        val left = rect.left + editState.cropLeft * rect.width
                        val top = rect.top + editState.cropTop * rect.height
                        val right = rect.left + editState.cropRight * rect.width
                        val bottom = rect.top + editState.cropBottom * rect.height
                        val shade = Color.Black.copy(alpha = 0.5f)
                        drawRect(shade, topLeft = Offset(rect.left, rect.top), size = androidx.compose.ui.geometry.Size(rect.width, (top - rect.top).coerceAtLeast(0f)))
                        drawRect(shade, topLeft = Offset(rect.left, bottom), size = androidx.compose.ui.geometry.Size(rect.width, (rect.top + rect.height - bottom).coerceAtLeast(0f)))
                        drawRect(shade, topLeft = Offset(rect.left, top), size = androidx.compose.ui.geometry.Size((left - rect.left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)))
                        drawRect(shade, topLeft = Offset(right, top), size = androidx.compose.ui.geometry.Size((rect.left + rect.width - right).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)))
                        drawLine(Color.White, Offset(left, top), Offset(right, top), strokeWidth = 3f)
                        drawLine(Color.White, Offset(right, top), Offset(right, bottom), strokeWidth = 3f)
                        drawLine(Color.White, Offset(right, bottom), Offset(left, bottom), strokeWidth = 3f)
                        drawLine(Color.White, Offset(left, bottom), Offset(left, top), strokeWidth = 3f)
                    }
                }
                if (source != null) {
                    FilterChip(
                        selected = gestureModeEnabled,
                        onClick = { setGestureMode(!gestureModeEnabled) },
                        label = {
                            Text(
                                text = stringResource(
                                    if (gestureModeEnabled) R.string.fossin_gestures_on else R.string.fossin_gestures_off,
                                    ),
                                fontSize = 11.sp,
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                    )
                }
                if (showGestureFeedback && gestureModeEnabled && activeGestureParameter != null) {
                    GestureFeedback(
                        parameter = activeGestureParameter,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (isRendering || isExporting) LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.75f))
            }
            if (source != null) {
                ToolGrid(tool) { tool = it }
                when (tool) {
                EditorTool.Looks -> LooksPanel(editState.style, editState.lut, builtIns, editState.intensity, { value -> updateEdit { it.copy(style = value) } }, { value -> updateEdit { it.copy(lut = value, lutName = value?.name, lutUri = null) } }, { lutPicker.launch(arrayOf("application/*", "text/plain", "*/*")) }) { value -> updateEdit { it.copy(intensity = value) } }
                EditorTool.Tune -> TunePanel(editState.exposure, editState.contrast, editState.saturation, editState.ambiance, editState.warmth, editState.highlights, editState.shadows, { value -> updateEdit { it.copy(exposure = value) } }, { value -> updateEdit { it.copy(contrast = value) } }, { value -> updateEdit { it.copy(saturation = value) } }, { value -> updateEdit { it.copy(ambiance = value) } }, { value -> updateEdit { it.copy(warmth = value) } }, { value -> updateEdit { it.copy(highlights = value) } }, { value -> updateEdit { it.copy(shadows = value) } })
                EditorTool.Details -> DetailsPanel(editState.detail, editState.sharpening, { value -> updateEdit { it.copy(detail = value) } }, { value -> updateEdit { it.copy(sharpening = value) } })
                EditorTool.TonalContrast -> TonalContrastPanel(editState.tonalContrastShadows, editState.tonalContrastMidtones, editState.tonalContrastHighlights, { value -> updateEdit { it.copy(tonalContrastShadows = value) } }, { value -> updateEdit { it.copy(tonalContrastMidtones = value) } }, { value -> updateEdit { it.copy(tonalContrastHighlights = value) } })
                EditorTool.Curves -> CurvesPanel(editState.curves, editState.toneToe, editState.toneShoulder, editState.tonePivot, { value -> updateEdit { it.copy(curves = value) } }, { value -> updateEdit { it.copy(toneToe = value) } }, { value -> updateEdit { it.copy(toneShoulder = value) } }, { value -> updateEdit { it.copy(tonePivot = value) } })
                EditorTool.WhiteBalance -> WhiteBalancePanel(editState.warmth, editState.tint, { value -> updateEdit { it.copy(warmth = value) } }, { value -> updateEdit { it.copy(tint = value) } })
                EditorTool.Crop -> CropPanel(editState.cropMode) { value -> updateEdit { it.copy(cropMode = value) } }
                EditorTool.Expand -> ExpandPanel(editState.expandStyle, editState.expandAmount, { value -> updateEdit { it.copy(expandStyle = value) } }, { value -> updateEdit { it.copy(expandAmount = value) } })
                EditorTool.Perspective -> PerspectivePanel(editState.perspectiveHorizontal, editState.perspectiveVertical, editState.perspectiveRotate, editState.perspectiveScale, { value -> updateEdit { it.copy(perspectiveHorizontal = value) } }, { value -> updateEdit { it.copy(perspectiveVertical = value) } }, { value -> updateEdit { it.copy(perspectiveRotate = value) } }, { value -> updateEdit { it.copy(perspectiveScale = value) } })
                EditorTool.Rotate -> RotatePanel(editState.rotation, editState.rotationFine, { value -> updateEdit { it.copy(rotation = value) } }, { value -> updateEdit { it.copy(rotationFine = value) } })
                EditorTool.Color -> ColorPanel(editState.tint, editState.fade, editState.vibrance, { value -> updateEdit { it.copy(tint = value) } }, { value -> updateEdit { it.copy(fade = value) } }, { value -> updateEdit { it.copy(vibrance = value) } })
                EditorTool.Hsl -> HslPanel(editState.hsl, selectedHslChannel, { selectedHslChannel = it }) { value -> updateEdit { it.copy(hsl = value(it.hsl)) } }
                EditorTool.Selective -> SelectivePanel(editState, { updateEdit { state ->
                    val point = SelectivePoint(state.selectiveX, state.selectiveY, state.selectiveRadius, state.selectiveExposure, state.selectiveContrast, state.selectiveSaturation, state.selectiveStructure)
                    state.copy(selectivePoints = state.selectivePoints + point)
                } }) { value -> updateEdit { value(it).withSyncedPrimarySelective() } }
                EditorTool.Brush -> BrushPanel(editState) { value -> updateEdit { value(it) } }
                EditorTool.Healing -> HealingPanel(editState) { value -> updateEdit { value(it) } }
                EditorTool.LensBlur -> LensBlurPanel(editState) { value -> updateEdit { value(it) } }
                EditorTool.Vignette -> VignettePanel(editState, { value -> updateEdit { value(it) } })
                EditorTool.Grain -> SliderPanel(stringResource(R.string.recipe_param_film_grain), editState.grain, 0f..1f) { value -> updateEdit { it.copy(grain = value) } }
                EditorTool.Bloom -> SliderPanel(stringResource(R.string.recipe_param_bloom), editState.bloom, 0f..1f) { value -> updateEdit { it.copy(bloom = value) } }
                EditorTool.Effects -> EffectsPanel(editState, { value -> updateEdit { value(it) } })
                EditorTool.HdrScape -> CreativeEffectPanel(stringResource(R.string.fossin_hdr_scape), editState.snapEffects[SnapEffect.HdrScape] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.HdrScape to value)) } }
                EditorTool.GlamourGlow -> CreativeEffectPanel(stringResource(R.string.fossin_glamour_glow), editState.snapEffects[SnapEffect.GlamourGlow] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.GlamourGlow to value)) } }
                EditorTool.Drama -> CreativeEffectPanel(stringResource(R.string.fossin_drama), editState.snapEffects[SnapEffect.Drama] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.Drama to value)) } }
                EditorTool.Vintage -> CreativeEffectPanel(stringResource(R.string.fossin_vintage), editState.snapEffects[SnapEffect.Vintage] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.Vintage to value)) } }
                EditorTool.GrainyFilm -> CreativeEffectPanel(stringResource(R.string.fossin_grainy_film), editState.snapEffects[SnapEffect.GrainyFilm] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.GrainyFilm to value)) } }
                EditorTool.Retrolux -> CreativeEffectPanel(stringResource(R.string.fossin_retrolux), editState.snapEffects[SnapEffect.Retrolux] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.Retrolux to value)) } }
                EditorTool.Grunge -> CreativeEffectPanel(stringResource(R.string.fossin_grunge), editState.snapEffects[SnapEffect.Grunge] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.Grunge to value)) } }
                EditorTool.BlackWhite -> CreativeEffectPanel(stringResource(R.string.fossin_black_white), editState.snapEffects[SnapEffect.BlackWhite] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.BlackWhite to value)) } }
                EditorTool.Noir -> CreativeEffectPanel(stringResource(R.string.fossin_noir), editState.snapEffects[SnapEffect.Noir] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.Noir to value)) } }
                EditorTool.Portrait -> CreativeEffectPanel(stringResource(R.string.fossin_portrait), editState.snapEffects[SnapEffect.Portrait] ?: 0f) { value -> updateEdit { it.copy(snapEffects = it.snapEffects + (SnapEffect.Portrait to value)) } }
                EditorTool.FaceEnhance -> FaceEnhancePanel(editState.portraitSpotlight, editState.portraitSmoothing, editState.portraitEyeClarity, { value -> updateEdit { it.copy(portraitSpotlight = value) } }, { value -> updateEdit { it.copy(portraitSmoothing = value) } }, { value -> updateEdit { it.copy(portraitEyeClarity = value) } })
                EditorTool.HeadPose -> HeadPosePanel(editState.headPoseHorizontal, editState.headPoseVertical, editState.headPoseTilt, { value -> updateEdit { it.copy(headPoseHorizontal = value) } }, { value -> updateEdit { it.copy(headPoseVertical = value) } }, { value -> updateEdit { it.copy(headPoseTilt = value) } })
                EditorTool.Frame -> FramePanel(editState.frameStyle, editState.frameWidth, { value -> updateEdit { it.copy(frameStyle = value) } }, { value -> updateEdit { it.copy(frameWidth = value) } })
                EditorTool.DoubleExposure -> DoubleExposurePanel(overlay != null, editState.overlayAlpha, editState.overlayBlendMode, { overlayPicker.launch(arrayOf("image/*")) }, { value -> updateEdit { it.copy(overlayAlpha = value) } }, { value -> updateEdit { it.copy(overlayBlendMode = value) } })
                EditorTool.Text -> TextPanel(editState.text, editState.textSize, editState.textOpacity, editState.textRotation, editState.textColor, editState.textStyle, { value -> updateEdit { it.copy(text = value) } }, { value -> updateEdit { it.copy(textSize = value) } }, { value -> updateEdit { it.copy(textOpacity = value) } }, { value -> updateEdit { it.copy(textRotation = value) } }, { value -> updateEdit { it.copy(textColor = value) } }, { value -> updateEdit { it.copy(textStyle = value) } })
                }
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    hasImage: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    showingOriginal: Boolean,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onToggleOriginal: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.fossin_subtitle),
                    color = Color(0xFF9B9BA1),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Keep the action targets at a usable touch size. On narrow phones the
        // row can scroll instead of squeezing the title into one letter per line.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(AppIcons.AutoMirroredOutlinedUndo, stringResource(R.string.fossin_undo), tint = if (canUndo) Color.White else Color(0xFF55555A))
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(AppIcons.AutoMirroredOutlinedUndo, stringResource(R.string.fossin_redo), tint = if (canRedo) Color.White else Color(0xFF55555A), modifier = Modifier.rotate(180f))
            }
            IconButton(onClick = onToggleOriginal, enabled = hasImage) {
                Icon(if (showingOriginal) AppIcons.VisibilityOff else AppIcons.Visibility, stringResource(R.string.fossin_compare), tint = if (hasImage) Color.White else Color(0xFF55555A))
            }
            IconButton(onClick = onReset, enabled = hasImage) {
                Icon(AppIcons.RestartAlt, stringResource(R.string.fossin_reset), tint = if (hasImage) Color.White else Color(0xFF55555A))
            }
            IconButton(onClick = onImport) {
                Icon(AppIcons.AddPhotoAlternate, stringResource(R.string.fossin_import), tint = Color.White)
            }
            IconButton(onClick = onShare, enabled = hasImage) {
                Icon(Icons.Default.Share, stringResource(R.string.share), tint = if (hasImage) Color.White else Color(0xFF55555A))
            }
            IconButton(onClick = onExport, enabled = hasImage) {
                Icon(AppIcons.Download, stringResource(R.string.fossin_export), tint = if (hasImage) Color.White else Color(0xFF55555A))
            }
        }
    }
}

@Composable
private fun EmptyEditor(onImport: () -> Unit, onOpenCamera: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(AppIcons.AddPhotoAlternate, null, tint = Color(0xFFFF7A45), modifier = Modifier.size(52.dp))
        Text(text = stringResource(R.string.fossin_empty_title), color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.fossin_empty_message), color = Color(0xFF9B9BA1))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onImport, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))) { Text(stringResource(R.string.fossin_import)) }
            TextButton(onClick = onOpenCamera) { Text(stringResource(R.string.fossin_open_camera), color = Color.White) }
        }
    }
}

@Composable
private fun GestureFeedback(parameter: GestureParameter, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE61B1B20))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(parameter.labelRes),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.fossin_gesture_percent, snapseedValuePercent(parameter.value, parameter.range)),
            color = Color(0xFFFFB08E),
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun ToolGrid(selected: EditorTool, onSelect: (EditorTool) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        gridItems(EditorTool.values().toList()) { tool ->
            val (label, icon) = when (tool) {
                EditorTool.Looks -> stringResource(R.string.fossin_looks) to AppIcons.AutoAwesome
                EditorTool.Tune -> stringResource(R.string.fossin_tune) to AppIcons.Tune
                EditorTool.Details -> stringResource(R.string.fossin_details) to AppIcons.Contrast
                EditorTool.TonalContrast -> stringResource(R.string.fossin_tonal_contrast) to AppIcons.Contrast
                EditorTool.Curves -> stringResource(R.string.recipe_tab_curve) to AppIcons.Contrast
                EditorTool.WhiteBalance -> stringResource(R.string.fossin_white_balance) to AppIcons.Tune
                EditorTool.Crop -> stringResource(R.string.crop) to AppIcons.Crop169
                EditorTool.Expand -> stringResource(R.string.fossin_expand) to AppIcons.Crop169
                EditorTool.Perspective -> stringResource(R.string.fossin_perspective) to AppIcons.Crop169
                EditorTool.Rotate -> stringResource(R.string.rotate) to AppIcons.ScreenRotation
                EditorTool.Color -> stringResource(R.string.recipe_tab_color) to AppIcons.Palette
                EditorTool.Hsl -> stringResource(R.string.fossin_hsl) to AppIcons.Palette
                EditorTool.Selective -> stringResource(R.string.fossin_selective) to AppIcons.Tune
                EditorTool.Brush -> stringResource(R.string.fossin_brush) to AppIcons.Tune
                EditorTool.Healing -> stringResource(R.string.fossin_healing) to AppIcons.AutoAwesome
                EditorTool.LensBlur -> stringResource(R.string.fossin_lens_blur) to AppIcons.AutoAwesome
                EditorTool.Vignette -> stringResource(R.string.recipe_param_vignette) to AppIcons.FilterVintage
                EditorTool.Grain -> stringResource(R.string.recipe_param_film_grain) to AppIcons.Grain
                EditorTool.Bloom -> stringResource(R.string.recipe_param_bloom) to AppIcons.AutoAwesome
                EditorTool.Effects -> stringResource(R.string.fossin_effects) to AppIcons.FilterVintage
                EditorTool.HdrScape -> stringResource(R.string.fossin_hdr_scape) to AppIcons.AutoAwesome
                EditorTool.GlamourGlow -> stringResource(R.string.fossin_glamour_glow) to AppIcons.AutoAwesome
                EditorTool.Drama -> stringResource(R.string.fossin_drama) to AppIcons.AutoAwesome
                EditorTool.Vintage -> stringResource(R.string.fossin_vintage) to AppIcons.FilterVintage
                EditorTool.GrainyFilm -> stringResource(R.string.fossin_grainy_film) to AppIcons.Grain
                EditorTool.Retrolux -> stringResource(R.string.fossin_retrolux) to AppIcons.FilterVintage
                EditorTool.Grunge -> stringResource(R.string.fossin_grunge) to AppIcons.FilterVintage
                EditorTool.BlackWhite -> stringResource(R.string.fossin_black_white) to AppIcons.Contrast
                EditorTool.Noir -> stringResource(R.string.fossin_noir) to AppIcons.Contrast
                EditorTool.Portrait -> stringResource(R.string.fossin_portrait) to AppIcons.AutoAwesome
                EditorTool.FaceEnhance -> stringResource(R.string.fossin_face_enhance) to AppIcons.AutoAwesome
                EditorTool.HeadPose -> stringResource(R.string.fossin_head_pose) to AppIcons.AutoAwesome
                EditorTool.Frame -> stringResource(R.string.fossin_frame) to AppIcons.Crop169
                EditorTool.DoubleExposure -> stringResource(R.string.fossin_double_exposure) to AppIcons.AddPhotoAlternate
                EditorTool.Text -> stringResource(R.string.fossin_text) to AppIcons.Article
            }
            FilterChip(
                selected = selected == tool,
                onClick = { onSelect(tool) },
                label = { Text(text = label, maxLines = 1, fontSize = 10.sp) },
                leadingIcon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LooksPanel(style: StylePreset, selected: LutChoice?, builtIns: List<LutChoice>, intensity: Float, onStyle: (StylePreset) -> Unit, onSelect: (LutChoice?) -> Unit, onImport: () -> Unit, onIntensityChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(text = stringResource(R.string.fossin_styles), color = Color.White, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(StylePreset.values().toList()) { preset ->
                FilterChip(
                    selected = style == preset,
                    onClick = { onStyle(preset) },
                    label = { Text(stringResource(preset.labelRes), fontSize = 11.sp) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.fossin_luts), color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onImport) { Text(text = stringResource(R.string.fossin_import_lut), color = Color(0xFFFF8A5C)) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { LutTile(stringResource(R.string.fossin_original), selected == null, null) { onSelect(null) } }
            items(builtIns) { choice -> LutTile(choice.name, selected?.name == choice.name, choice) { onSelect(choice) } }
        }
        if (selected != null) {
            Spacer(Modifier.height(4.dp))
            Slider(intensity, onIntensityChange, valueRange = 0f..1f)
            Text(text = stringResource(R.string.fossin_lut_strength, (intensity * 100).roundToInt()), color = Color(0xFF9B9BA1), fontSize = 12.sp)
        }
    }
}

@Composable
private fun LutTile(name: String, selected: Boolean, choice: LutChoice?, onClick: () -> Unit) {
    Column(modifier = Modifier.width(74.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(if (selected) Color(0xFFFF6B35) else Color(0xFF29292E)), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Default.Check, null, tint = Color.White) else Text(if (choice == null) "•" else name.take(2).uppercase(), color = Color(0xFFFFB08E), fontWeight = FontWeight.Bold)
        }
        Text(text = name, color = Color(0xFFD8D8DC), maxLines = 1, fontSize = 11.sp)
    }
}

@Composable
private fun TunePanel(exposure: Float, contrast: Float, saturation: Float, ambiance: Float, warmth: Float, highlights: Float, shadows: Float, onExposure: (Float) -> Unit, onContrast: (Float) -> Unit, onSaturation: (Float) -> Unit, onAmbiance: (Float) -> Unit, onWarmth: (Float) -> Unit, onHighlights: (Float) -> Unit, onShadows: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        SliderPanel(stringResource(R.string.fossin_exposure), exposure, -2f..2f, onExposure)
        SliderPanel(stringResource(R.string.fossin_contrast), contrast, 0.5f..1.5f, onContrast)
        SliderPanel(stringResource(R.string.fossin_saturation), saturation, 0f..2f, onSaturation)
        SliderPanel(stringResource(R.string.fossin_ambiance), ambiance, -1f..1f, onAmbiance)
        SliderPanel(stringResource(R.string.fossin_warmth), warmth, -1f..1f, onWarmth)
        SliderPanel(stringResource(R.string.recipe_param_highlights), highlights, -1f..1f, onHighlights)
        SliderPanel(stringResource(R.string.recipe_param_shadows), shadows, -1f..1f, onShadows)
    }
}

@Composable
private fun DetailsPanel(structure: Float, sharpening: Float, onStructure: (Float) -> Unit, onSharpening: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_details), color = Color.White, fontWeight = FontWeight.SemiBold)
        SliderPanel(stringResource(R.string.fossin_structure), structure, -1f..1f, onStructure)
        SliderPanel(stringResource(R.string.fossin_sharpening), sharpening, 0f..1f, onSharpening)
    }
}

@Composable
private fun TonalContrastPanel(
    shadows: Float,
    midtones: Float,
    highlights: Float,
    onShadows: (Float) -> Unit,
    onMidtones: (Float) -> Unit,
    onHighlights: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_tonal_contrast), color = Color.White, fontWeight = FontWeight.SemiBold)
        SliderPanel(stringResource(R.string.fossin_tonal_shadows), shadows, -1f..1f, onShadows)
        SliderPanel(stringResource(R.string.fossin_tonal_midtones), midtones, -1f..1f, onMidtones)
        SliderPanel(stringResource(R.string.fossin_tonal_highlights), highlights, -1f..1f, onHighlights)
    }
}

@Composable
private fun WhiteBalancePanel(
    temperature: Float,
    tint: Float,
    onTemperature: (Float) -> Unit,
    onTint: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_white_balance), color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(text = stringResource(R.string.fossin_white_balance_hint), color = Color(0xFF9B9BA1), fontSize = 12.sp)
        SliderPanel(stringResource(R.string.fossin_temperature), temperature, -1f..1f, onTemperature)
        SliderPanel(stringResource(R.string.fossin_tint), tint, -1f..1f, onTint)
    }
}

@Composable
private fun CreativeEffectPanel(title: String, strength: Float, onStrength: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold)
        SliderPanel(stringResource(R.string.fossin_strength), strength, 0f..1f, onStrength)
    }
}

@Composable
private fun FaceEnhancePanel(
    spotlight: Float,
    smoothing: Float,
    eyeClarity: Float,
    onSpotlight: (Float) -> Unit,
    onSmoothing: (Float) -> Unit,
    onEyeClarity: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_face_enhance), color = Color.White, fontWeight = FontWeight.SemiBold)
        SliderPanel(stringResource(R.string.fossin_face_spotlight), spotlight, 0f..1f, onSpotlight)
        SliderPanel(stringResource(R.string.fossin_skin_smoothing), smoothing, 0f..1f, onSmoothing)
        SliderPanel(stringResource(R.string.fossin_eye_clarity), eyeClarity, 0f..1f, onEyeClarity)
    }
}

@Composable
private fun CurvesPanel(
    curves: Map<CurveChannel, List<CurvePoint>>,
    toe: Float,
    shoulder: Float,
    pivot: Float,
    onCurves: (Map<CurveChannel, List<CurvePoint>>) -> Unit,
    onToe: (Float) -> Unit,
    onShoulder: (Float) -> Unit,
    onPivot: (Float) -> Unit,
) {
    var selectedChannel by remember { mutableStateOf(CurveChannel.Master) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.recipe_tab_curve), color = Color.White, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(CurveChannel.values().toList()) { channel ->
                FilterChip(
                    selected = selectedChannel == channel,
                    onClick = { selectedChannel = channel },
                    label = { Text(text = when (channel) {
                        CurveChannel.Master -> stringResource(R.string.fossin_curve_rgb)
                        CurveChannel.Red -> stringResource(R.string.fossin_curve_red)
                        CurveChannel.Green -> stringResource(R.string.fossin_curve_green)
                        CurveChannel.Blue -> stringResource(R.string.fossin_curve_blue)
                    }) }
                )
            }
        }
        CurveGraph(curves[selectedChannel] ?: defaultCurvePoints) { points -> onCurves(curves + (selectedChannel to points)) }
        SliderPanel(stringResource(R.string.recipe_param_shadows), toe, -1f..1f, onToe)
        SliderPanel(stringResource(R.string.recipe_param_highlights), shoulder, -1f..1f, onShoulder)
        SliderPanel(stringResource(R.string.fossin_midpoint), pivot, -1f..1f, onPivot)
    }
}

@Composable
private fun CurveGraph(points: List<CurvePoint>, onPoints: (List<CurvePoint>) -> Unit) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val currentPoints by rememberUpdatedState(points)
    ComposeCanvas(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF19191D))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        selectedIndex = currentPoints.indices.minByOrNull { index ->
                            val point = currentPoints[index]
                            val px = point.x * size.width
                            val py = (1f - point.y) * size.height
                            (offset.x - px) * (offset.x - px) + (offset.y - py) * (offset.y - py)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val index = selectedIndex ?: return@detectDragGestures
                        val activePoints = currentPoints
                        val previous = activePoints.getOrNull(index) ?: return@detectDragGestures
                        val x = if (index == 0) 0f else if (index == activePoints.lastIndex) 1f else (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                        val minX = activePoints.getOrNull(index - 1)?.x?.plus(0.02f) ?: 0f
                        val maxX = activePoints.getOrNull(index + 1)?.x?.minus(0.02f) ?: 1f
                        val next = previous.copy(x = x.coerceIn(minX, maxX), y = (1f - change.position.y / size.height).coerceIn(0f, 1f))
                        onPoints(activePoints.toMutableList().also { it[index] = next })
                    },
                    onDragEnd = { selectedIndex = null },
                    onDragCancel = { selectedIndex = null },
                )
            }
    ) {
        repeat(5) { index ->
            val fraction = index / 4f
            drawLine(Color(0xFF3B3B42), Offset(fraction * size.width, 0f), Offset(fraction * size.width, size.height), strokeWidth = 1f)
            drawLine(Color(0xFF3B3B42), Offset(0f, fraction * size.height), Offset(size.width, fraction * size.height), strokeWidth = 1f)
        }
        if (points.size > 1) {
            for (index in 0 until points.lastIndex) {
                val a = points[index]
                val b = points[index + 1]
                drawLine(Color(0xFFFF8A5C), Offset(a.x * size.width, (1f - a.y) * size.height), Offset(b.x * size.width, (1f - b.y) * size.height), strokeWidth = 4f)
            }
        }
        points.forEach { point ->
            drawCircle(Color.White, radius = 7f, center = Offset(point.x * size.width, (1f - point.y) * size.height))
            drawCircle(Color(0xFFFF8A5C), radius = 4f, center = Offset(point.x * size.width, (1f - point.y) * size.height))
        }
    }
}

@Composable
private fun ColorPanel(tint: Float, fade: Float, vibrance: Float, onTint: (Float) -> Unit, onFade: (Float) -> Unit, onVibrance: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        SliderPanel(stringResource(R.string.recipe_param_tint), tint, -1f..1f, onTint)
        SliderPanel(stringResource(R.string.recipe_param_fade), fade, 0f..1f, onFade)
        SliderPanel(stringResource(R.string.recipe_param_color), vibrance, -1f..1f, onVibrance)
    }
}

@Composable
private fun HslPanel(
    values: Map<HslChannel, HslValue>,
    selected: HslChannel,
    onSelected: (HslChannel) -> Unit,
    onChange: (((Map<HslChannel, HslValue>) -> Map<HslChannel, HslValue>)) -> Unit,
) {
    val value = values[selected] ?: HslValue()
    val label = when (selected) {
        HslChannel.Red -> stringResource(R.string.recipe_param_red_hue).substringBeforeLast(' ')
        HslChannel.Orange -> stringResource(R.string.recipe_param_orange_hue).substringBeforeLast(' ')
        HslChannel.Yellow -> stringResource(R.string.recipe_param_yellow_hue).substringBeforeLast(' ')
        HslChannel.Green -> stringResource(R.string.recipe_param_green_hue).substringBeforeLast(' ')
        HslChannel.Cyan -> stringResource(R.string.recipe_param_cyan_hue).substringBeforeLast(' ')
        HslChannel.Blue -> stringResource(R.string.recipe_param_blue_hue).substringBeforeLast(' ')
        HslChannel.Purple -> stringResource(R.string.recipe_param_purple_hue).substringBeforeLast(' ')
        HslChannel.Magenta -> stringResource(R.string.recipe_param_magenta_hue).substringBeforeLast(' ')
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_hsl), color = Color.White, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(HslChannel.values().toList()) { channel ->
                val channelLabel = when (channel) {
                    HslChannel.Red -> stringResource(R.string.recipe_param_red_hue).substringBeforeLast(' ')
                    HslChannel.Orange -> stringResource(R.string.recipe_param_orange_hue).substringBeforeLast(' ')
                    HslChannel.Yellow -> stringResource(R.string.recipe_param_yellow_hue).substringBeforeLast(' ')
                    HslChannel.Green -> stringResource(R.string.recipe_param_green_hue).substringBeforeLast(' ')
                    HslChannel.Cyan -> stringResource(R.string.recipe_param_cyan_hue).substringBeforeLast(' ')
                    HslChannel.Blue -> stringResource(R.string.recipe_param_blue_hue).substringBeforeLast(' ')
                    HslChannel.Purple -> stringResource(R.string.recipe_param_purple_hue).substringBeforeLast(' ')
                    HslChannel.Magenta -> stringResource(R.string.recipe_param_magenta_hue).substringBeforeLast(' ')
                }
                FilterChip(selected = selected == channel, onClick = { onSelected(channel) }, label = { Text(channelLabel, fontSize = 11.sp) })
            }
        }
        Text(text = label, color = Color(0xFF9B9BA1), fontSize = 12.sp)
        SliderPanel(stringResource(R.string.fossin_hue), value.hue, -1f..1f) { newValue -> onChange { it + (selected to value.copy(hue = newValue)) } }
        SliderPanel(stringResource(R.string.fossin_chroma), value.chroma, -1f..1f) { newValue -> onChange { it + (selected to value.copy(chroma = newValue)) } }
        SliderPanel(stringResource(R.string.fossin_lightness), value.lightness, -1f..1f) { newValue -> onChange { it + (selected to value.copy(lightness = newValue)) } }
    }
}

@Composable
private fun SelectivePanel(state: EditorState, onAddPoint: () -> Unit, onUpdate: (((EditorState) -> EditorState)) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.fossin_selective), color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onAddPoint) { Text(stringResource(R.string.fossin_add_point), color = Color(0xFFFF8A5C)) }
        }
        SliderPanel(stringResource(R.string.fossin_center_x), state.selectiveX, 0f..1f) { value -> onUpdate { it.copy(selectiveX = value) } }
        SliderPanel(stringResource(R.string.fossin_center_y), state.selectiveY, 0f..1f) { value -> onUpdate { it.copy(selectiveY = value) } }
        SliderPanel(stringResource(R.string.fossin_radius), state.selectiveRadius, 0.05f..1f) { value -> onUpdate { it.copy(selectiveRadius = value) } }
        SliderPanel(stringResource(R.string.fossin_exposure), state.selectiveExposure, -2f..2f) { value -> onUpdate { it.copy(selectiveExposure = value) } }
        SliderPanel(stringResource(R.string.fossin_contrast), state.selectiveContrast, 0f..2f) { value -> onUpdate { it.copy(selectiveContrast = value) } }
        SliderPanel(stringResource(R.string.fossin_saturation), state.selectiveSaturation, 0f..2f) { value -> onUpdate { it.copy(selectiveSaturation = value) } }
        SliderPanel(stringResource(R.string.fossin_structure), state.selectiveStructure, -1f..1f) { value -> onUpdate { it.copy(selectiveStructure = value) } }
    }
}

@Composable
private fun BrushPanel(state: EditorState, onUpdate: (((EditorState) -> EditorState)) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_brush), color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(text = stringResource(R.string.fossin_brush_hint), color = Color(0xFF9B9BA1), fontSize = 12.sp)
        SliderPanel(stringResource(R.string.fossin_center_x), state.brushX, 0f..1f) { value -> onUpdate { it.copy(brushX = value) } }
        SliderPanel(stringResource(R.string.fossin_center_y), state.brushY, 0f..1f) { value -> onUpdate { it.copy(brushY = value) } }
        SliderPanel(stringResource(R.string.fossin_radius), state.brushRadius, 0.03f..1f) { value -> onUpdate { it.copy(brushRadius = value) } }
        SliderPanel(stringResource(R.string.fossin_exposure), state.brushExposure, -2f..2f) { value -> onUpdate { it.copy(brushExposure = value) } }
        SliderPanel(stringResource(R.string.fossin_saturation), state.brushSaturation, 0f..2f) { value -> onUpdate { it.copy(brushSaturation = value) } }
        SliderPanel(stringResource(R.string.fossin_warmth), state.brushWarmth, -1f..1f) { value -> onUpdate { it.copy(brushWarmth = value) } }
    }
}

@Composable
private fun HealingPanel(state: EditorState, onUpdate: (((EditorState) -> EditorState)) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_healing), color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(text = stringResource(R.string.fossin_healing_hint), color = Color(0xFF9B9BA1), fontSize = 12.sp)
        SliderPanel(stringResource(R.string.fossin_center_x), state.healingX, 0f..1f) { value -> onUpdate { it.copy(healingX = value) } }
        SliderPanel(stringResource(R.string.fossin_center_y), state.healingY, 0f..1f) { value -> onUpdate { it.copy(healingY = value) } }
        SliderPanel(stringResource(R.string.fossin_radius), state.healingRadius, 0.01f..0.3f) { value -> onUpdate { it.copy(healingRadius = value) } }
        SliderPanel(stringResource(R.string.fossin_strength), state.healingStrength, 0f..1f) { value -> onUpdate { it.copy(healingStrength = value) } }
    }
}

@Composable
private fun LensBlurPanel(state: EditorState, onUpdate: (((EditorState) -> EditorState)) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_lens_blur), color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            LensBlurShape.values().forEach { shape ->
                FilterChip(
                    selected = state.lensBlurShape == shape,
                    onClick = { onUpdate { it.copy(lensBlurShape = shape) } },
                    label = { Text(text = when (shape) {
                        LensBlurShape.Radial -> stringResource(R.string.fossin_radial)
                        LensBlurShape.Linear -> stringResource(R.string.fossin_linear)
                    }) }
                )
            }
        }
        SliderPanel(stringResource(R.string.fossin_center_x), state.lensBlurX, 0f..1f) { value -> onUpdate { it.copy(lensBlurX = value) } }
        SliderPanel(stringResource(R.string.fossin_center_y), state.lensBlurY, 0f..1f) { value -> onUpdate { it.copy(lensBlurY = value) } }
        SliderPanel(stringResource(R.string.fossin_radius), state.lensBlurRadius, 0.03f..1f) { value -> onUpdate { it.copy(lensBlurRadius = value) } }
        SliderPanel(stringResource(R.string.fossin_transition), state.lensBlurTransition, 0.03f..1f) { value -> onUpdate { it.copy(lensBlurTransition = value) } }
        if (state.lensBlurShape == LensBlurShape.Linear) SliderPanel(stringResource(R.string.fossin_angle), state.lensBlurAngle, -1f..1f) { value -> onUpdate { it.copy(lensBlurAngle = value) } }
        SliderPanel(stringResource(R.string.fossin_strength), state.lensBlurStrength, 0f..1f) { value -> onUpdate { it.copy(lensBlurStrength = value) } }
    }
}

@Composable
private fun EffectsPanel(state: EditorState, onUpdate: (((EditorState) -> EditorState)) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_effects), color = Color.White, fontWeight = FontWeight.SemiBold)
        SliderPanel(stringResource(R.string.recipe_param_flash), state.flash, 0f..1f) { value -> onUpdate { it.copy(flash = value) } }
        SliderPanel(stringResource(R.string.recipe_param_bleach_bypass), state.bleachBypass, 0f..1f) { value -> onUpdate { it.copy(bleachBypass = value) } }
        SliderPanel(stringResource(R.string.recipe_param_soft_light), state.softLight, 0f..1f) { value -> onUpdate { it.copy(softLight = value) } }
        SliderPanel(stringResource(R.string.recipe_param_hdf), state.halation, 0f..1f) { value -> onUpdate { it.copy(halation = value) } }
        SliderPanel(stringResource(R.string.recipe_param_chromatic_aberration), state.chromaticAberration, 0f..1f) { value -> onUpdate { it.copy(chromaticAberration = value) } }
        SliderPanel(stringResource(R.string.recipe_param_noise), state.noise, 0f..1f) { value -> onUpdate { it.copy(noise = value) } }
        SliderPanel(stringResource(R.string.recipe_param_low_res), state.lowRes, 0f..1f) { value -> onUpdate { it.copy(lowRes = value) } }
    }
}

@Composable
private fun VignettePanel(state: EditorState, onUpdate: (((EditorState) -> EditorState)) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.recipe_param_vignette), color = Color.White, fontWeight = FontWeight.SemiBold)
        SliderPanel(stringResource(R.string.fossin_outer_brightness), state.vignette, -1f..1f) { value -> onUpdate { it.copy(vignette = value) } }
        SliderPanel(stringResource(R.string.fossin_inner_brightness), state.vignetteInner, 0f..1f) { value -> onUpdate { it.copy(vignetteInner = value) } }
        SliderPanel(stringResource(R.string.fossin_center_x), state.vignetteX, 0f..1f) { value -> onUpdate { it.copy(vignetteX = value) } }
        SliderPanel(stringResource(R.string.fossin_center_y), state.vignetteY, 0f..1f) { value -> onUpdate { it.copy(vignetteY = value) } }
        SliderPanel(stringResource(R.string.fossin_radius), state.vignetteRadius, 0.1f..1.4f) { value -> onUpdate { it.copy(vignetteRadius = value) } }
    }
}

@Composable
private fun FramePanel(
    style: FrameStyle,
    width: Float,
    onStyle: (FrameStyle) -> Unit,
    onWidth: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_frame), color = Color.White, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(FrameStyle.values().toList()) { frameStyle ->
                FilterChip(
                    selected = style == frameStyle,
                    onClick = { onStyle(frameStyle) },
                    label = { Text(text = when (frameStyle) {
                        FrameStyle.White -> stringResource(R.string.fossin_frame_white)
                        FrameStyle.Black -> stringResource(R.string.fossin_frame_black)
                        FrameStyle.Warm -> stringResource(R.string.fossin_frame_warm)
                        FrameStyle.Gray -> stringResource(R.string.fossin_frame_gray)
                        FrameStyle.Cream -> stringResource(R.string.fossin_frame_cream)
                    }) }
                )
            }
        }
        SliderPanel(stringResource(R.string.fossin_frame_width), width, 0f..1f, onWidth)
    }
}

@Composable
private fun DoubleExposurePanel(
    hasOverlay: Boolean,
    alpha: Float,
    blendMode: OverlayBlendMode,
    onImport: () -> Unit,
    onAlpha: (Float) -> Unit,
    onBlendMode: (OverlayBlendMode) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.fossin_double_exposure), color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onImport) { Text(stringResource(R.string.fossin_import_overlay), color = Color(0xFFFF8A5C)) }
        }
        if (hasOverlay) SliderPanel(stringResource(R.string.fossin_opacity), alpha, 0f..1f, onAlpha)
        else Text(text = stringResource(R.string.fossin_overlay_hint), color = Color(0xFF9B9BA1), fontSize = 12.sp)
        if (hasOverlay) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(OverlayBlendMode.values().toList()) { mode ->
                    FilterChip(
                        selected = mode == blendMode,
                        onClick = { onBlendMode(mode) },
                        label = { Text(text = when (mode) {
                            OverlayBlendMode.Normal -> stringResource(R.string.fossin_blend_normal)
                            OverlayBlendMode.Lighten -> stringResource(R.string.fossin_blend_lighten)
                            OverlayBlendMode.Darken -> stringResource(R.string.fossin_blend_darken)
                            OverlayBlendMode.Multiply -> stringResource(R.string.fossin_blend_multiply)
                            OverlayBlendMode.Screen -> stringResource(R.string.fossin_blend_screen)
                            OverlayBlendMode.Overlay -> stringResource(R.string.fossin_blend_overlay)
                        }) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TextPanel(
    text: String,
    size: Float,
    opacity: Float,
    rotation: Float,
    color: TextColor,
    style: TextStyle,
    onText: (String) -> Unit,
    onSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onRotation: (Float) -> Unit,
    onColor: (TextColor) -> Unit,
    onStyle: (TextStyle) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_text), color = Color.White, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = text,
            onValueChange = onText,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().background(Color(0xFF242429), RoundedCornerShape(8.dp)).padding(12.dp)) {
                    if (text.isEmpty()) Text(stringResource(R.string.fossin_text_hint), color = Color(0xFF77777D))
                    inner()
                }
            }
        )
        SliderPanel(stringResource(R.string.fossin_text_size), size, 0.03f..0.2f, onSize)
        SliderPanel(stringResource(R.string.fossin_opacity), opacity, 0f..1f, onOpacity)
        SliderPanel(stringResource(R.string.fossin_rotate), rotation, -1f..1f, onRotation)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(TextColor.values().toList()) { textColor ->
                FilterChip(
                    selected = textColor == color,
                    onClick = { onColor(textColor) },
                    label = { Text(text = when (textColor) {
                        TextColor.White -> stringResource(R.string.fossin_text_white)
                        TextColor.Black -> stringResource(R.string.fossin_text_black)
                        TextColor.Warm -> stringResource(R.string.fossin_text_warm)
                        TextColor.Accent -> stringResource(R.string.fossin_text_accent)
                    }) }
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(TextStyle.values().toList()) { textStyle ->
                FilterChip(
                    selected = textStyle == style,
                    onClick = { onStyle(textStyle) },
                    label = { Text(text = when (textStyle) {
                        TextStyle.Plain -> stringResource(R.string.fossin_text_style_plain)
                        TextStyle.Bold -> stringResource(R.string.fossin_text_style_bold)
                        TextStyle.Outline -> stringResource(R.string.fossin_text_style_outline)
                        TextStyle.Neon -> stringResource(R.string.fossin_text_style_neon)
                        TextStyle.Stamp -> stringResource(R.string.fossin_text_style_stamp)
                        TextStyle.Typewriter -> stringResource(R.string.fossin_text_style_typewriter)
                    }) }
                )
            }
        }
    }
}

@Composable
private fun CropPanel(selected: CropMode, onSelected: (CropMode) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.crop), color = Color.White, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CropMode.values().toList()) { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    label = { Text(text = when (mode) {
                        CropMode.Original -> stringResource(R.string.crop_original)
                        CropMode.Free -> stringResource(R.string.fossin_free)
                        CropMode.Square -> stringResource(R.string.fossin_square)
                        CropMode.ThreeTwo -> stringResource(R.string.fossin_three_two)
                        CropMode.FourThree -> stringResource(R.string.fossin_four_three)
                        CropMode.FiveFour -> stringResource(R.string.fossin_five_four)
                        CropMode.SevenFive -> stringResource(R.string.fossin_seven_five)
                        CropMode.SixteenNine -> stringResource(R.string.fossin_sixteen_nine)
                    }) }
                )
            }
        }
    }
}

@Composable
private fun ExpandPanel(
    style: ExpandStyle,
    amount: Float,
    onStyle: (ExpandStyle) -> Unit,
    onAmount: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_expand), color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            ExpandStyle.values().forEach { expandStyle ->
                FilterChip(
                    selected = style == expandStyle,
                    onClick = { onStyle(expandStyle) },
                    label = { Text(text = when (expandStyle) {
                        ExpandStyle.Black -> stringResource(R.string.fossin_expand_black)
                        ExpandStyle.White -> stringResource(R.string.fossin_expand_white)
                        ExpandStyle.Warm -> stringResource(R.string.fossin_expand_warm)
                        ExpandStyle.Stretch -> stringResource(R.string.fossin_expand_stretch)
                    }) }
                )
            }
        }
        SliderPanel(stringResource(R.string.fossin_expand_amount), amount, 0f..1f, onAmount)
    }
}

@Composable
private fun PerspectivePanel(
    horizontal: Float,
    vertical: Float,
    rotate: Float,
    scale: Float,
    onHorizontal: (Float) -> Unit,
    onVertical: (Float) -> Unit,
    onRotate: (Float) -> Unit,
    onScale: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_perspective), color = Color.White, fontWeight = FontWeight.SemiBold)
        SliderPanel(stringResource(R.string.fossin_horizontal), horizontal, -1f..1f, onHorizontal)
        SliderPanel(stringResource(R.string.fossin_vertical), vertical, -1f..1f, onVertical)
        SliderPanel(stringResource(R.string.fossin_rotate), rotate, -1f..1f, onRotate)
        SliderPanel(stringResource(R.string.fossin_scale), scale, -1f..1f, onScale)
    }
}

@Composable
private fun HeadPosePanel(
    horizontal: Float,
    vertical: Float,
    tilt: Float,
    onHorizontal: (Float) -> Unit,
    onVertical: (Float) -> Unit,
    onTilt: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text = stringResource(R.string.fossin_head_pose), color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(text = stringResource(R.string.fossin_head_pose_hint), color = Color(0xFF9B9BA1), fontSize = 12.sp)
        SliderPanel(stringResource(R.string.fossin_horizontal), horizontal, -1f..1f, onHorizontal)
        SliderPanel(stringResource(R.string.fossin_vertical), vertical, -1f..1f, onVertical)
        SliderPanel(stringResource(R.string.fossin_tilt), tilt, -1f..1f, onTilt)
    }
}

@Composable
private fun RotatePanel(rotation: Int, fine: Float, onRotation: (Int) -> Unit, onFine: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(R.string.rotate), color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onRotation((rotation - 90 + 360) % 360) }) { Text(stringResource(R.string.fossin_rotate_left)) }
            Text(text = stringResource(R.string.fossin_rotation, rotation), color = Color.White, modifier = Modifier.padding(top = 12.dp))
            Button(onClick = { onRotation((rotation + 90) % 360) }) { Text(stringResource(R.string.fossin_rotate_right)) }
        }
        SliderPanel(stringResource(R.string.fossin_straighten), fine, -1f..1f, onFine)
    }
}

@Composable
private fun SliderPanel(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, modifier = Modifier.width(88.dp), color = Color(0xFFD8D8DC), fontSize = 12.sp)
        Slider(value, onValueChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(text = "${((value - range.start) / (range.endInclusive - range.start) * 100).roundToInt()}%", color = Color(0xFF9B9BA1), fontSize = 11.sp, modifier = Modifier.width(38.dp))
    }
}

private fun prepareBitmap(
    bitmap: Bitmap,
    rotation: Int,
    rotationFine: Float = 0f,
    cropMode: CropMode,
    cropLeft: Float = 0f,
    cropTop: Float = 0f,
    cropRight: Float = 1f,
    cropBottom: Float = 1f,
    perspectiveHorizontal: Float = 0f,
    perspectiveVertical: Float = 0f,
    perspectiveRotate: Float = 0f,
    perspectiveScale: Float = 0f,
    headPoseHorizontal: Float = 0f,
    headPoseVertical: Float = 0f,
    headPoseTilt: Float = 0f,
): Bitmap {
    val rotationDegrees = rotation.toFloat() + rotationFine.coerceIn(-1f, 1f) * 15f
    val rotated = if (kotlin.math.abs(rotationDegrees) < 0.001f) bitmap else {
        android.graphics.Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            android.graphics.Matrix().apply { postRotate(rotationDegrees) },
            true
        )
    }
    val transformed = if (
        kotlin.math.abs(perspectiveHorizontal) > 0.001f ||
        kotlin.math.abs(perspectiveVertical) > 0.001f ||
        kotlin.math.abs(perspectiveRotate) > 0.001f ||
        kotlin.math.abs(perspectiveScale) > 0.001f ||
        kotlin.math.abs(headPoseHorizontal) > 0.001f ||
        kotlin.math.abs(headPoseVertical) > 0.001f ||
        kotlin.math.abs(headPoseTilt) > 0.001f
    ) {
        android.graphics.Bitmap.createBitmap(
            rotated,
            0,
            0,
            rotated.width,
            rotated.height,
            android.graphics.Matrix().apply {
                val horizontalInset = perspectiveHorizontal.coerceIn(-1f, 1f) * rotated.width * 0.18f
                val verticalInset = perspectiveVertical.coerceIn(-1f, 1f) * rotated.height * 0.18f
                setPolyToPoly(
                    floatArrayOf(0f, 0f, rotated.width.toFloat(), 0f, rotated.width.toFloat(), rotated.height.toFloat(), 0f, rotated.height.toFloat()),
                    0,
                    floatArrayOf(horizontalInset, verticalInset, rotated.width - horizontalInset, -verticalInset, rotated.width + horizontalInset, rotated.height + verticalInset, -horizontalInset, rotated.height - verticalInset),
                    0,
                    4,
                )
                postRotate((perspectiveRotate.coerceIn(-1f, 1f) * 18f) + (headPoseTilt.coerceIn(-1f, 1f) * 8f), rotated.width / 2f, rotated.height / 2f)
                val scale = (1f + perspectiveScale.coerceIn(-1f, 1f) * 0.25f).coerceAtLeast(0.5f)
                postScale(scale, scale, rotated.width / 2f, rotated.height / 2f)
                postTranslate(
                    headPoseHorizontal.coerceIn(-1f, 1f) * rotated.width * 0.08f,
                    headPoseVertical.coerceIn(-1f, 1f) * rotated.height * 0.08f,
                )
            },
            true
        )
    } else rotated
    if (cropMode == CropMode.Free) {
        val left = (cropLeft.coerceIn(0f, 1f) * transformed.width).roundToInt().coerceIn(0, transformed.width - 1)
        val top = (cropTop.coerceIn(0f, 1f) * transformed.height).roundToInt().coerceIn(0, transformed.height - 1)
        val right = (cropRight.coerceIn(0f, 1f) * transformed.width).roundToInt().coerceIn(left + 1, transformed.width)
        val bottom = (cropBottom.coerceIn(0f, 1f) * transformed.height).roundToInt().coerceIn(top + 1, transformed.height)
        if (right - left > 1 && bottom - top > 1) return android.graphics.Bitmap.createBitmap(transformed, left, top, right - left, bottom - top)
    }
    val ratio = cropMode.ratio ?: return transformed
    val currentRatio = transformed.width.toFloat() / transformed.height
    val (width, height) = if (currentRatio > ratio) {
        (transformed.height * ratio).roundToInt() to transformed.height
    } else {
        transformed.width to (transformed.width / ratio).roundToInt()
    }
    val left = ((transformed.width - width) / 2).coerceAtLeast(0)
    val top = ((transformed.height - height) / 2).coerceAtLeast(0)
    return android.graphics.Bitmap.createBitmap(transformed, left, top, width, height)
}

internal fun applySnapEffectRecipe(base: ColorRecipeParams, effects: Map<SnapEffect, Float>): ColorRecipeParams {
    val hdr = (effects[SnapEffect.HdrScape] ?: 0f).coerceIn(0f, 1f)
    val glow = (effects[SnapEffect.GlamourGlow] ?: 0f).coerceIn(0f, 1f)
    val drama = (effects[SnapEffect.Drama] ?: 0f).coerceIn(0f, 1f)
    val vintage = (effects[SnapEffect.Vintage] ?: 0f).coerceIn(0f, 1f)
    val film = (effects[SnapEffect.GrainyFilm] ?: 0f).coerceIn(0f, 1f)
    val retrolux = (effects[SnapEffect.Retrolux] ?: 0f).coerceIn(0f, 1f)
    val grunge = (effects[SnapEffect.Grunge] ?: 0f).coerceIn(0f, 1f)
    val blackWhite = (effects[SnapEffect.BlackWhite] ?: 0f).coerceIn(0f, 1f)
    val noir = (effects[SnapEffect.Noir] ?: 0f).coerceIn(0f, 1f)
    val portrait = (effects[SnapEffect.Portrait] ?: 0f).coerceIn(0f, 1f)
    return base.copy(
        contrast = (base.contrast * (1f + hdr * 0.32f + drama * 0.28f + grunge * 0.34f + noir * 0.38f)).coerceIn(0.2f, 3f),
        saturation = (base.saturation * (1f + hdr * 0.16f + drama * 0.18f + grunge * 0.08f + retrolux * 0.15f) * (1f - blackWhite * 0.98f - noir * 0.88f)).coerceIn(0f, 2f),
        temperature = (base.temperature + vintage * 0.22f + retrolux * 0.15f).coerceIn(-1f, 1f),
        fade = (base.fade + vintage * 0.2f + film * 0.1f + retrolux * 0.16f).coerceIn(0f, 1f),
        highlights = (base.highlights - hdr * 0.18f - portrait * 0.05f).coerceIn(-1f, 1f),
        shadows = (base.shadows + hdr * 0.2f + portrait * 0.12f).coerceIn(-1f, 1f),
        toneToe = (base.toneToe - noir * 0.12f).coerceIn(-1f, 1f),
        toneShoulder = (base.toneShoulder + hdr * 0.08f).coerceIn(-1f, 1f),
        clarity = (base.clarity + hdr * 0.42f + drama * 0.2f + grunge * 0.24f - glow * 0.22f - portrait * 0.16f).coerceIn(-1f, 1f),
        vignette = (base.vignette - vintage * 0.16f - grunge * 0.18f - noir * 0.24f).coerceIn(-1f, 1f),
        filmGrain = (base.filmGrain + film * 0.5f + retrolux * 0.22f + grunge * 0.3f).coerceIn(0f, 1f),
        bloom = (base.bloom + glow * 0.18f + retrolux * 0.1f).coerceIn(0f, 1f),
        softLight = (base.softLight + glow * 0.45f).coerceIn(0f, 1f),
        chromaticAberration = (base.chromaticAberration + retrolux * 0.18f).coerceIn(0f, 1f),
        noise = (base.noise + retrolux * 0.12f + grunge * 0.12f).coerceIn(0f, 1f),
    )
}

internal fun curveArray(points: List<CurvePoint>?): FloatArray? {
    if (points == null || points == defaultCurvePoints || points == defaultCurvePoints.toList()) return null
    return points.flatMap { listOf(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }.toFloatArray()
}

private suspend fun renderEditorBitmap(
    processor: LutImageProcessor,
    bitmap: Bitmap,
    state: EditorState,
    overlay: Bitmap? = null,
): Bitmap {
    val prepared = prepareBitmap(
        bitmap,
        state.rotation,
        state.rotationFine,
        state.cropMode,
        state.cropLeft,
        state.cropTop,
        state.cropRight,
        state.cropBottom,
        state.perspectiveHorizontal,
        state.perspectiveVertical,
        state.perspectiveRotate,
        state.perspectiveScale,
        state.headPoseHorizontal,
        state.headPoseVertical,
        state.headPoseTilt,
    )
    var output: Bitmap? = null
    val red = state.hsl[HslChannel.Red] ?: HslValue()
    val orange = state.hsl[HslChannel.Orange] ?: HslValue()
    val yellow = state.hsl[HslChannel.Yellow] ?: HslValue()
    val green = state.hsl[HslChannel.Green] ?: HslValue()
    val cyan = state.hsl[HslChannel.Cyan] ?: HslValue()
    val blue = state.hsl[HslChannel.Blue] ?: HslValue()
    val purple = state.hsl[HslChannel.Purple] ?: HslValue()
    val magenta = state.hsl[HslChannel.Magenta] ?: HslValue()
    return try {
        val processed = processor.applyLut(
            bitmap = prepared,
            lutConfig = state.lut?.config,
            colorRecipeParams = ColorRecipeParams(
                exposure = state.exposure,
                contrast = state.contrast,
                saturation = state.saturation,
                temperature = state.warmth,
                tint = state.tint,
                fade = state.fade,
                color = state.vibrance,
                highlights = (state.highlights - state.ambiance * 0.16f).coerceIn(-1f, 1f),
                shadows = (state.shadows + state.ambiance * 0.25f).coerceIn(-1f, 1f),
                toneToe = state.toneToe,
                toneShoulder = state.toneShoulder,
                tonePivot = state.tonePivot,
                clarity = (state.detail + state.ambiance * 0.18f).coerceIn(-1f, 1f),
                vignette = 0f,
                filmGrain = state.grain,
                bloom = state.bloom,
                flash = state.flash,
                bleachBypass = state.bleachBypass,
                softLight = state.softLight,
                halation = state.halation,
                chromaticAberration = state.chromaticAberration,
                noise = state.noise,
                lowRes = state.lowRes,
                redHue = red.hue,
                redChroma = red.chroma,
                redLightness = red.lightness,
                orangeHue = orange.hue,
                orangeChroma = orange.chroma,
                orangeLightness = orange.lightness,
                yellowHue = yellow.hue,
                yellowChroma = yellow.chroma,
                yellowLightness = yellow.lightness,
                greenHue = green.hue,
                greenChroma = green.chroma,
                greenLightness = green.lightness,
                cyanHue = cyan.hue,
                cyanChroma = cyan.chroma,
                cyanLightness = cyan.lightness,
                blueHue = blue.hue,
                blueChroma = blue.chroma,
                blueLightness = blue.lightness,
                purpleHue = purple.hue,
                purpleChroma = purple.chroma,
                purpleLightness = purple.lightness,
                magentaHue = magenta.hue,
                magentaChroma = magenta.chroma,
                magentaLightness = magenta.lightness,
                lutIntensity = state.intensity,
                masterCurvePoints = curveArray(state.curves[CurveChannel.Master]),
                redCurvePoints = curveArray(state.curves[CurveChannel.Red]),
                greenCurvePoints = curveArray(state.curves[CurveChannel.Green]),
                blueCurvePoints = curveArray(state.curves[CurveChannel.Blue]),
            ).let(state.style::applyTo).let { applySnapEffectRecipe(it, state.snapEffects) }
        )
        var current = processed
        output = current
        if (state.tonalContrastShadows != 0f || state.tonalContrastMidtones != 0f || state.tonalContrastHighlights != 0f) {
            val tonal = applyTonalContrast(current, state)
            if (tonal !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = tonal
            output = current
        }
        if (state.vignette != 0f || state.vignetteX != 0.5f || state.vignetteY != 0.5f || state.vignetteRadius != 0.7f || state.vignetteInner != 0f) {
            val vignetted = applyVignette(current, state)
            if (vignetted !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = vignetted
            output = current
        }
        if (state.sharpening > 0.001f) {
            val sharpened = applySharpen(current, state.sharpening)
            if (sharpened !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = sharpened
            output = current
        }
        val portraitStrength = (state.snapEffects[SnapEffect.Portrait] ?: 0f).coerceIn(0f, 1f)
        val portraitSpotlight = maxOf(state.portraitSpotlight.coerceIn(0f, 1f), portraitStrength * 0.35f)
        val portraitSmoothing = maxOf(state.portraitSmoothing.coerceIn(0f, 1f), portraitStrength)
        val portraitEyeClarity = maxOf(state.portraitEyeClarity.coerceIn(0f, 1f), portraitStrength * 0.45f)
        if (portraitSpotlight > 0.001f || portraitSmoothing > 0.001f || portraitEyeClarity > 0.001f) {
            val portrait = applyPortrait(current, portraitSpotlight, portraitSmoothing, portraitEyeClarity)
            if (portrait !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = portrait
            output = current
        }
        if (state.expandAmount > 0.001f) {
            val expanded = applyExpand(current, state.expandAmount, state.expandStyle)
            if (expanded !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = expanded
            output = current
        }
        if (state.selectivePoints.isNotEmpty() || state.selectiveExposure != 0f || state.selectiveContrast != 1f || state.selectiveSaturation != 1f || state.selectiveStructure != 0f) {
            val selective = applySelective(current, state)
            if (selective !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = selective
            output = current
        }
        if (state.brushStrokes.isNotEmpty()) {
            state.brushStrokes.forEach { stroke ->
                val brushed = applyBrushStroke(current, stroke)
                if (brushed !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
                current = brushed
            }
            output = current
        } else if (state.brushExposure != 0f || state.brushSaturation != 1f || state.brushWarmth != 0f) {
            val brushed = applyBrush(current, state)
            if (brushed !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = brushed
            output = current
        }
        if (state.healingStrokes.isNotEmpty()) {
            state.healingStrokes.forEach { stroke ->
                val healed = applyHealingStroke(current, stroke)
                if (healed !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
                current = healed
            }
            output = current
        } else if (state.healingStrength > 0.001f) {
            val healed = applyHealing(current, state)
            if (healed !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = healed
            output = current
        }
        if (state.lensBlurStrength > 0.001f) {
            val blurred = applyLensBlur(current, state)
            if (blurred !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = blurred
            output = current
        }
        if (overlay != null && state.overlayAlpha > 0.001f) {
            val blended = applyDoubleExposure(current, overlay, state.overlayAlpha, state.overlayBlendMode)
            if (blended !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = blended
            output = current
        }
        if (state.text.isNotBlank()) {
            val captioned = applyText(current, state.text, state.textSize, state.textOpacity, state.textRotation, state.textColor, state.textStyle)
            if (captioned !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
            current = captioned
            output = current
        }
        if (state.frameWidth <= 0.001f) current else applyFrame(current, state.frameWidth, state.frameStyle).also { framed ->
            if (framed !== current && current !== prepared && current !== bitmap && !current.isRecycled) current.recycle()
        }
    } finally {
        if (prepared !== bitmap && prepared !== output && !prepared.isRecycled) prepared.recycle()
    }
}

private fun applyFrame(bitmap: Bitmap, widthFraction: Float, style: FrameStyle): Bitmap {
    val border = (minOf(bitmap.width, bitmap.height) * widthFraction * 0.12f).roundToInt().coerceAtLeast(1)
    val framed = Bitmap.createBitmap(bitmap.width + border * 2, bitmap.height + border * 2, Bitmap.Config.ARGB_8888)
    Canvas(framed).apply {
        drawColor(style.color)
        drawBitmap(bitmap, border.toFloat(), border.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
    }
    return framed
}

/** Applies Snapseed's Tonal Contrast controls without changing hue or alpha. */
private fun applyTonalContrast(bitmap: Bitmap, state: EditorState): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val output = pixels.copyOf()
    val shadowAmount = state.tonalContrastShadows.coerceIn(-1f, 1f)
    val midtoneAmount = state.tonalContrastMidtones.coerceIn(-1f, 1f)
    val highlightAmount = state.tonalContrastHighlights.coerceIn(-1f, 1f)
    for (index in pixels.indices) {
        val color = pixels[index]
        val alpha = android.graphics.Color.alpha(color)
        val red = android.graphics.Color.red(color) / 255f
        val green = android.graphics.Color.green(color) / 255f
        val blue = android.graphics.Color.blue(color) / 255f
        val luminance = (red * 0.2126f + green * 0.7152f + blue * 0.0722f).coerceIn(0f, 1f)
        val curve = tonalContrastValue(luminance, shadowAmount, midtoneAmount, highlightAmount)
        val scale = if (luminance > 0.0001f) curve / luminance else 1f
        output[index] = android.graphics.Color.argb(
            alpha,
            (red * scale * 255f).roundToInt().coerceIn(0, 255),
            (green * scale * 255f).roundToInt().coerceIn(0, 255),
            (blue * scale * 255f).roundToInt().coerceIn(0, 255),
        )
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(output, 0, width, 0, 0, width, height)
    }
}

internal fun tonalContrastValue(
    luminance: Float,
    shadows: Float,
    midtones: Float,
    highlights: Float,
): Float {
    val value = luminance.coerceIn(0f, 1f)
    val shadowWeight = (1f - value).let { it * it }
    val highlightWeight = value * value
    val midtoneWeight = (1f - kotlin.math.abs(value * 2f - 1f)).coerceIn(0f, 1f)
    val amount = shadows.coerceIn(-1f, 1f) * shadowWeight +
        midtones.coerceIn(-1f, 1f) * midtoneWeight +
        highlights.coerceIn(-1f, 1f) * highlightWeight
    return ((value - 0.5f) * (1f + amount * 1.35f) + 0.5f).coerceIn(0f, 1f)
}

private fun applyExpand(bitmap: Bitmap, amount: Float, style: ExpandStyle): Bitmap {
    val border = (minOf(bitmap.width, bitmap.height) * amount.coerceIn(0f, 1f) * 0.28f).roundToInt()
    if (border <= 0) return bitmap
    val expanded = Bitmap.createBitmap(bitmap.width + border * 2, bitmap.height + border * 2, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(expanded)
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    if (style == ExpandStyle.Stretch) {
        imagePaint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, expanded.width.toFloat(), expanded.height.toFloat(), imagePaint)
        imagePaint.shader = null
    } else {
        val color = when (style) {
            ExpandStyle.Black -> android.graphics.Color.BLACK
            ExpandStyle.White -> android.graphics.Color.WHITE
            ExpandStyle.Warm -> FrameStyle.Warm.color
            ExpandStyle.Stretch -> android.graphics.Color.TRANSPARENT
        }
        canvas.drawColor(color)
    }
    canvas.drawBitmap(bitmap, border.toFloat(), border.toFloat(), imagePaint)
    return expanded
}

private fun applyVignette(bitmap: Bitmap, state: EditorState): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val source = IntArray(width * height)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    val output = source.copyOf()
    val radius = state.vignetteRadius.coerceAtLeast(0.05f)
    val inner = state.vignetteInner.coerceIn(0f, 0.95f) * radius
    for (y in 0 until height) {
        val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
        for (x in 0 until width) {
            val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
            val distance = kotlin.math.sqrt(
                (nx - state.vignetteX) * (nx - state.vignetteX) +
                    (ny - state.vignetteY) * (ny - state.vignetteY)
            )
            val edge = ((distance - inner) / (radius - inner).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            val smooth = edge * edge * (3f - 2f * edge)
            val factor = (1f + state.vignette.coerceIn(-1f, 1f) * smooth * 0.82f + state.vignetteInner.coerceIn(0f, 1f) * (1f - smooth) * 0.35f).coerceIn(0.05f, 2f)
            if (kotlin.math.abs(factor - 1f) < 0.001f) continue
            val index = y * width + x
            val color = source[index]
            output[index] = android.graphics.Color.argb(
                android.graphics.Color.alpha(color),
                (android.graphics.Color.red(color) * factor).roundToInt().coerceIn(0, 255),
                (android.graphics.Color.green(color) * factor).roundToInt().coerceIn(0, 255),
                (android.graphics.Color.blue(color) * factor).roundToInt().coerceIn(0, 255),
            )
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(output, 0, width, 0, 0, width, height)
    }
}

private fun applySharpen(bitmap: Bitmap, strength: Float): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val small = Bitmap.createScaledBitmap(bitmap, (width / 6).coerceAtLeast(1), (height / 6).coerceAtLeast(1), true)
    val blurred = Bitmap.createScaledBitmap(small, width, height, true)
    if (small !== bitmap && small !== blurred && !small.isRecycled) small.recycle()
    val source = IntArray(width * height)
    val blurPixels = IntArray(width * height)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    blurred.getPixels(blurPixels, 0, width, 0, 0, width, height)
    val output = IntArray(width * height)
    val amount = strength.coerceIn(0f, 1f) * 1.7f
    for (index in source.indices) {
        val base = source[index]
        val blur = blurPixels[index]
        val r = (android.graphics.Color.red(base) + (android.graphics.Color.red(base) - android.graphics.Color.red(blur)) * amount).roundToInt().coerceIn(0, 255)
        val g = (android.graphics.Color.green(base) + (android.graphics.Color.green(base) - android.graphics.Color.green(blur)) * amount).roundToInt().coerceIn(0, 255)
        val b = (android.graphics.Color.blue(base) + (android.graphics.Color.blue(base) - android.graphics.Color.blue(blur)) * amount).roundToInt().coerceIn(0, 255)
        output[index] = android.graphics.Color.argb(android.graphics.Color.alpha(base), r, g, b)
    }
    if (blurred !== bitmap && !blurred.isRecycled) blurred.recycle()
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(output, 0, width, 0, 0, width, height)
    }
}

/** Face-aware portrait approximation that remains fully offline. It uses skin chroma and a
 * soft face-centre prior, so no network model or cloud service is needed for Face Enhance. */
private fun applyPortrait(
    bitmap: Bitmap,
    spotlight: Float,
    smoothing: Float,
    eyeClarity: Float,
): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val smallWidth = (width / 8).coerceAtLeast(1)
    val smallHeight = (height / 8).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(bitmap, smallWidth, smallHeight, true)
    val blurred = Bitmap.createScaledBitmap(small, width, height, true)
    if (small !== bitmap && small !== blurred && !small.isRecycled) small.recycle()
    val source = IntArray(width * height)
    val blurPixels = IntArray(width * height)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    blurred.getPixels(blurPixels, 0, width, 0, 0, width, height)
    val output = source.copyOf()
    val hsv = FloatArray(3)
    val spotlightAmount = spotlight.coerceIn(0f, 1f)
    val smoothingAmount = smoothing.coerceIn(0f, 1f)
    val eyeClarityAmount = eyeClarity.coerceIn(0f, 1f)
    for (y in 0 until height) {
        val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
        for (x in 0 until width) {
            val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
            val index = y * width + x
            val color = source[index]
            android.graphics.Color.colorToHSV(color, hsv)
            val hue = hsv[0]
            val skinHue = (hue <= 55f || hue >= 330f) && hsv[1] in 0.12f..0.9f && hsv[2] > 0.12f
            val centre = (1f - kotlin.math.sqrt((nx - 0.5f) * (nx - 0.5f) + (ny - 0.42f) * (ny - 0.42f)) / 0.72f).coerceIn(0f, 1f)
            val faceWeight = if (skinHue) 0.85f else 0.12f * centre
            val smoothingWeight = smoothingAmount * faceWeight
            val clarityWeight = eyeClarityAmount * (if (skinHue) 0.85f else 0.16f * centre)
            val liftWeight = spotlightAmount * (if (skinHue) 0.9f else centre * 0.18f)
            if (smoothingWeight <= 0.001f && clarityWeight <= 0.001f && liftWeight <= 0.001f) continue
            val blur = blurPixels[index]
            val alpha = android.graphics.Color.alpha(color)
            val sourceRed = android.graphics.Color.red(color)
            val sourceGreen = android.graphics.Color.green(color)
            val sourceBlue = android.graphics.Color.blue(color)
            val blurRed = android.graphics.Color.red(blur)
            val blurGreen = android.graphics.Color.green(blur)
            val blurBlue = android.graphics.Color.blue(blur)
            val red = sourceRed * (1f - smoothingWeight) + blurRed * smoothingWeight + (sourceRed - blurRed) * clarityWeight * 0.75f
            val green = sourceGreen * (1f - smoothingWeight) + blurGreen * smoothingWeight + (sourceGreen - blurGreen) * clarityWeight * 0.75f
            val blue = sourceBlue * (1f - smoothingWeight) + blurBlue * smoothingWeight + (sourceBlue - blurBlue) * clarityWeight * 0.75f
            val lift = 1f + liftWeight * 0.10f
            output[index] = android.graphics.Color.argb(
                alpha,
                (red * lift).roundToInt().coerceIn(0, 255),
                (green * lift).roundToInt().coerceIn(0, 255),
                (blue * lift).roundToInt().coerceIn(0, 255),
            )
        }
    }
    if (blurred !== bitmap && !blurred.isRecycled) blurred.recycle()
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(output, 0, width, 0, 0, width, height)
    }
}

private fun applySelective(bitmap: Bitmap, state: EditorState): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val points = if (state.selectivePoints.isEmpty()) {
        listOf(SelectivePoint(state.selectiveX, state.selectiveY, state.selectiveRadius, state.selectiveExposure, state.selectiveContrast, state.selectiveSaturation, state.selectiveStructure))
    } else state.selectivePoints
    val hsv = FloatArray(3)
    for (y in 0 until height) {
        val normalizedY = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
        for (x in 0 until width) {
            val normalizedX = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
            val selected = points.maxBy { point ->
                val distance = kotlin.math.sqrt(
                    (normalizedX - point.x) * (normalizedX - point.x) +
                        (normalizedY - point.y) * (normalizedY - point.y)
                )
                (1f - distance / point.radius.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            }
            val distance = kotlin.math.sqrt(
                (normalizedX - selected.x) * (normalizedX - selected.x) +
                    (normalizedY - selected.y) * (normalizedY - selected.y)
            )
            val weight = (1f - distance / selected.radius.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            if (weight <= 0f) continue
            val index = y * width + x
            val color = pixels[index]
            android.graphics.Color.colorToHSV(color, hsv)
            val exposureMultiplier = Math.pow(2.0, selected.exposure.toDouble()).toFloat()
            hsv[2] = (hsv[2] * (1f + (exposureMultiplier - 1f) * weight)).coerceIn(0f, 1f)
            hsv[1] = (hsv[1] * (1f + (selected.saturation - 1f) * weight)).coerceIn(0f, 1f)
            val contrast = 1f + (selected.contrast - 1f) * weight
            hsv[2] = ((hsv[2] - 0.5f) * contrast + 0.5f + selected.structure * 0.12f * weight).coerceIn(0f, 1f)
            pixels[index] = android.graphics.Color.HSVToColor(android.graphics.Color.alpha(color), hsv)
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun applyBrush(bitmap: Bitmap, state: EditorState): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val radius = state.brushRadius.coerceAtLeast(0.01f)
    val hsv = FloatArray(3)
    val exposureMultiplier = Math.pow(2.0, state.brushExposure.toDouble()).toFloat()
    val warmth = state.brushWarmth * 22f
    for (y in 0 until height) {
        val normalizedY = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
        for (x in 0 until width) {
            val normalizedX = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
            val dx = normalizedX - state.brushX
            val dy = normalizedY - state.brushY
            val weight = (1f - kotlin.math.sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
            if (weight <= 0f) continue
            val index = y * width + x
            val color = pixels[index]
            android.graphics.Color.colorToHSV(color, hsv)
            hsv[0] = (hsv[0] + warmth * weight + 360f) % 360f
            hsv[1] = (hsv[1] * (1f + (state.brushSaturation - 1f) * weight)).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * (1f + (exposureMultiplier - 1f) * weight)).coerceIn(0f, 1f)
            pixels[index] = android.graphics.Color.HSVToColor(android.graphics.Color.alpha(color), hsv)
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun applyBrushStroke(bitmap: Bitmap, stroke: BrushStroke): Bitmap {
    if (stroke.points.isEmpty()) return bitmap
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val output = pixels.copyOf()
    val radiusPx = (minOf(width, height) * stroke.radius).coerceAtLeast(1f)
    val minX = ((stroke.points.minOf { it.x } * width) - radiusPx).toInt().coerceIn(0, width - 1)
    val maxX = ((stroke.points.maxOf { it.x } * width) + radiusPx).toInt().coerceIn(0, width - 1)
    val minY = ((stroke.points.minOf { it.y } * height) - radiusPx).toInt().coerceIn(0, height - 1)
    val maxY = ((stroke.points.maxOf { it.y } * height) + radiusPx).toInt().coerceIn(0, height - 1)
    val hsv = FloatArray(3)
    val exposureMultiplier = Math.pow(2.0, stroke.exposure.toDouble()).toFloat()
    for (y in minY..maxY) {
        for (x in minX..maxX) {
            val nearest = stroke.points.minOf { point ->
                kotlin.math.hypot(x - point.x * width, y - point.y * height)
            }
            val weight = (1f - nearest / radiusPx).coerceIn(0f, 1f)
            if (weight <= 0f) continue
            val index = y * width + x
            val color = pixels[index]
            android.graphics.Color.colorToHSV(color, hsv)
            hsv[0] = (hsv[0] + stroke.warmth * 22f * weight + 360f) % 360f
            hsv[1] = (hsv[1] * (1f + (stroke.saturation - 1f) * weight)).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * (1f + (exposureMultiplier - 1f) * weight)).coerceIn(0f, 1f)
            output[index] = android.graphics.Color.HSVToColor(android.graphics.Color.alpha(color), hsv)
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(output, 0, width, 0, 0, width, height)
    }
}

private fun applyHealing(bitmap: Bitmap, state: EditorState): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val source = IntArray(width * height)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    val output = source.copyOf()
    val radiusPx = (minOf(width, height) * state.healingRadius).coerceAtLeast(2f)
    val centerX = (state.healingX * (width - 1)).roundToInt()
    val centerY = (state.healingY * (height - 1)).roundToInt()
    val sampleOffset = (radiusPx * 2.2f).roundToInt().coerceAtLeast(2)
    for (y in (centerY - radiusPx.toInt() - 1).coerceAtLeast(0)..(centerY + radiusPx.toInt() + 1).coerceAtMost(height - 1)) {
        for (x in (centerX - radiusPx.toInt() - 1).coerceAtLeast(0)..(centerX + radiusPx.toInt() + 1).coerceAtMost(width - 1)) {
            val distance = kotlin.math.hypot((x - centerX).toFloat(), (y - centerY).toFloat())
            val mask = (1f - distance / radiusPx).coerceIn(0f, 1f) * state.healingStrength
            if (mask <= 0f) continue
            val sampleX = (x - sampleOffset).coerceIn(0, width - 1)
            val sampleY = y
            val index = y * width + x
            val sample = source[sampleY * width + sampleX]
            val base = source[index]
            val a = android.graphics.Color.alpha(base)
            val r = (android.graphics.Color.red(base) * (1f - mask) + android.graphics.Color.red(sample) * mask).roundToInt()
            val g = (android.graphics.Color.green(base) * (1f - mask) + android.graphics.Color.green(sample) * mask).roundToInt()
            val b = (android.graphics.Color.blue(base) * (1f - mask) + android.graphics.Color.blue(sample) * mask).roundToInt()
            output[index] = android.graphics.Color.argb(a, r, g, b)
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(output, 0, width, 0, 0, width, height)
    }
}

private fun applyHealingStroke(bitmap: Bitmap, stroke: HealingStroke): Bitmap {
    if (stroke.points.isEmpty()) return bitmap
    val width = bitmap.width
    val height = bitmap.height
    val source = IntArray(width * height)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    val output = source.copyOf()
    val radiusPx = (minOf(width, height) * stroke.radius).coerceAtLeast(2f)
    val minX = ((stroke.points.minOf { it.x } * width) - radiusPx).toInt().coerceIn(0, width - 1)
    val maxX = ((stroke.points.maxOf { it.x } * width) + radiusPx).toInt().coerceIn(0, width - 1)
    val minY = ((stroke.points.minOf { it.y } * height) - radiusPx).toInt().coerceIn(0, height - 1)
    val maxY = ((stroke.points.maxOf { it.y } * height) + radiusPx).toInt().coerceIn(0, height - 1)
    val sampleOffset = (radiusPx * 2.2f).roundToInt().coerceAtLeast(2)
    for (y in minY..maxY) {
        for (x in minX..maxX) {
            val nearest = stroke.points.minOf { point ->
                kotlin.math.hypot(x - point.x * width, y - point.y * height)
            }
            val mask = (1f - nearest / radiusPx).coerceIn(0f, 1f) * stroke.strength
            if (mask <= 0f) continue
            val sampleX = (x - sampleOffset).coerceIn(0, width - 1)
            val index = y * width + x
            val base = source[index]
            val sample = source[y * width + sampleX]
            val a = android.graphics.Color.alpha(base)
            val r = (android.graphics.Color.red(base) * (1f - mask) + android.graphics.Color.red(sample) * mask).roundToInt()
            val g = (android.graphics.Color.green(base) * (1f - mask) + android.graphics.Color.green(sample) * mask).roundToInt()
            val b = (android.graphics.Color.blue(base) * (1f - mask) + android.graphics.Color.blue(sample) * mask).roundToInt()
            output[index] = android.graphics.Color.argb(a, r, g, b)
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(output, 0, width, 0, 0, width, height)
    }
}

private fun applyLensBlur(bitmap: Bitmap, state: EditorState): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val smallWidth = (width / 8).coerceAtLeast(1)
    val smallHeight = (height / 8).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(bitmap, smallWidth, smallHeight, true)
    val blurred = Bitmap.createScaledBitmap(small, width, height, true)
    if (small !== bitmap && small !== blurred && !small.isRecycled) small.recycle()
    val source = IntArray(width * height)
    val blurPixels = IntArray(width * height)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    blurred.getPixels(blurPixels, 0, width, 0, 0, width, height)
    val result = source.copyOf()
    val radius = state.lensBlurRadius.coerceAtLeast(0.01f)
    val transition = state.lensBlurTransition.coerceAtLeast(0.03f)
    val angle = state.lensBlurAngle * Math.PI.toFloat()
    val sinAngle = kotlin.math.sin(angle)
    val cosAngle = kotlin.math.cos(angle)
    for (y in 0 until height) {
        val ny = if (height <= 1) 0.5f else y.toFloat() / (height - 1)
        for (x in 0 until width) {
            val nx = if (width <= 1) 0.5f else x.toFloat() / (width - 1)
            val distance = if (state.lensBlurShape == LensBlurShape.Radial) {
                kotlin.math.sqrt((nx - state.lensBlurX) * (nx - state.lensBlurX) + (ny - state.lensBlurY) * (ny - state.lensBlurY))
            } else {
                kotlin.math.abs((nx - state.lensBlurX) * sinAngle - (ny - state.lensBlurY) * cosAngle)
            }
            val weight = ((distance - radius) / transition).coerceIn(0f, 1f) * state.lensBlurStrength
            if (weight <= 0f) continue
            val index = y * width + x
            val base = source[index]
            val blur = blurPixels[index]
            val a = android.graphics.Color.alpha(base)
            val r = (android.graphics.Color.red(base) * (1f - weight) + android.graphics.Color.red(blur) * weight).roundToInt()
            val g = (android.graphics.Color.green(base) * (1f - weight) + android.graphics.Color.green(blur) * weight).roundToInt()
            val b = (android.graphics.Color.blue(base) * (1f - weight) + android.graphics.Color.blue(blur) * weight).roundToInt()
            result[index] = android.graphics.Color.argb(a, r, g, b)
        }
    }
    if (blurred !== bitmap && !blurred.isRecycled) blurred.recycle()
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(result, 0, width, 0, 0, width, height)
    }
}

private fun applyDoubleExposure(base: Bitmap, overlay: Bitmap, alpha: Float, blendMode: OverlayBlendMode): Bitmap {
    val result = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
    Canvas(result).apply {
        drawBitmap(base, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
            isFilterBitmap = true
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                this.blendMode = when (blendMode) {
                    OverlayBlendMode.Normal -> android.graphics.BlendMode.SRC_OVER
                    OverlayBlendMode.Lighten -> android.graphics.BlendMode.LIGHTEN
                    OverlayBlendMode.Darken -> android.graphics.BlendMode.DARKEN
                    OverlayBlendMode.Multiply -> android.graphics.BlendMode.MULTIPLY
                    OverlayBlendMode.Screen -> android.graphics.BlendMode.SCREEN
                    OverlayBlendMode.Overlay -> android.graphics.BlendMode.OVERLAY
                }
            }
        }
        drawBitmap(
            overlay,
            null,
            android.graphics.RectF(0f, 0f, base.width.toFloat(), base.height.toFloat()),
            overlayPaint
        )
    }
    return result
}

private fun applyText(
    bitmap: Bitmap,
    text: String,
    sizeFraction: Float,
    opacity: Float,
    rotation: Float,
    textColor: TextColor,
    textStyle: TextStyle,
): Bitmap {
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor.argb
        alpha = (opacity.coerceIn(0f, 1f) * 255).roundToInt()
        textAlign = Paint.Align.CENTER
        textSize = minOf(result.width, result.height) * sizeFraction.coerceIn(0.02f, 0.3f)
        typeface = when (textStyle) {
            TextStyle.Plain -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            TextStyle.Bold, TextStyle.Outline, TextStyle.Neon, TextStyle.Stamp -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            TextStyle.Typewriter -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        }
        when (textStyle) {
            TextStyle.Outline -> {
                style = Paint.Style.STROKE
                strokeWidth = textSize * 0.08f
                setShadowLayer(textSize * 0.04f, 0f, textSize * 0.02f, android.graphics.Color.BLACK)
            }
            TextStyle.Neon -> setShadowLayer(textSize * 0.2f, 0f, 0f, textColor.argb)
            TextStyle.Stamp -> {
                style = Paint.Style.FILL
                setShadowLayer(textSize * 0.05f, textSize * 0.03f, textSize * 0.04f, android.graphics.Color.BLACK)
            }
            else -> setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, android.graphics.Color.BLACK)
        }
    }
    val metrics = paint.fontMetrics
    val baseline = result.height * 0.88f - (metrics.ascent + metrics.descent) / 2f
    canvas.save()
    canvas.rotate(rotation.coerceIn(-1f, 1f) * 45f, result.width / 2f, result.height * 0.88f)
    canvas.drawText(text, result.width / 2f, baseline, paint)
    canvas.restore()
    return result
}

private suspend fun loadImage(context: android.content.Context, uri: Uri, onLoaded: (Bitmap) -> Unit) {
    val bitmap = runCatching { loadBitmap(context, uri, maxEdge = 2048) }.getOrNull()
    if (bitmap != null) onLoaded(bitmap)
}

private suspend fun loadBitmap(context: android.content.Context, uri: Uri, maxEdge: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                if (maxEdge > 0) {
                    val scale = minOf(1f, maxEdge.toFloat() / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
}

private fun lutDisplayName(context: android.content.Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.substringBeforeLast('.') else null
    } ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
        ?: context.getString(R.string.fossin_imported_lut)
}

private fun persistReadPermission(context: android.content.Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
