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
import android.os.Parcel
import android.provider.OpenableColumns
import android.util.Base64
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.SaverScope
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hinnka.mycamera.MainActivity
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.GalleryRepository
import com.hinnka.mycamera.lut.LutConfig
import com.hinnka.mycamera.lut.LutImageProcessor
import com.hinnka.mycamera.lut.LutParser
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.ui.theme.PhotonCameraTheme
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileNotFoundException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
                FossinStackEditor(
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

private data class EditorToolPresentation(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private fun editorToolPresentation(tool: EditorTool): EditorToolPresentation = when (tool) {
    EditorTool.Looks -> EditorToolPresentation(R.string.fossin_looks, AppIcons.AutoAwesome)
    EditorTool.Tune -> EditorToolPresentation(R.string.fossin_tune, AppIcons.Tune)
    EditorTool.Details -> EditorToolPresentation(R.string.fossin_details, AppIcons.Contrast)
    EditorTool.TonalContrast -> EditorToolPresentation(R.string.fossin_tonal_contrast, AppIcons.Contrast)
    EditorTool.Curves -> EditorToolPresentation(R.string.recipe_tab_curve, AppIcons.Contrast)
    EditorTool.WhiteBalance -> EditorToolPresentation(R.string.fossin_white_balance, AppIcons.Tune)
    EditorTool.Crop -> EditorToolPresentation(R.string.crop, AppIcons.Crop169)
    EditorTool.Expand -> EditorToolPresentation(R.string.fossin_expand, AppIcons.Crop169)
    EditorTool.Perspective -> EditorToolPresentation(R.string.fossin_perspective, AppIcons.Crop169)
    EditorTool.Rotate -> EditorToolPresentation(R.string.rotate, AppIcons.ScreenRotation)
    EditorTool.Color -> EditorToolPresentation(R.string.recipe_tab_color, AppIcons.Palette)
    EditorTool.Hsl -> EditorToolPresentation(R.string.fossin_hsl, AppIcons.Palette)
    EditorTool.Selective -> EditorToolPresentation(R.string.fossin_selective, AppIcons.Tune)
    EditorTool.Brush -> EditorToolPresentation(R.string.fossin_brush, AppIcons.Tune)
    EditorTool.Healing -> EditorToolPresentation(R.string.fossin_healing, AppIcons.AutoAwesome)
    EditorTool.LensBlur -> EditorToolPresentation(R.string.fossin_lens_blur, AppIcons.AutoAwesome)
    EditorTool.Vignette -> EditorToolPresentation(R.string.recipe_param_vignette, AppIcons.FilterVintage)
    EditorTool.Grain -> EditorToolPresentation(R.string.recipe_param_film_grain, AppIcons.Grain)
    EditorTool.Bloom -> EditorToolPresentation(R.string.recipe_param_bloom, AppIcons.AutoAwesome)
    EditorTool.Effects -> EditorToolPresentation(R.string.fossin_effects, AppIcons.FilterVintage)
    EditorTool.HdrScape -> EditorToolPresentation(R.string.fossin_hdr_scape, AppIcons.AutoAwesome)
    EditorTool.GlamourGlow -> EditorToolPresentation(R.string.fossin_glamour_glow, AppIcons.AutoAwesome)
    EditorTool.Drama -> EditorToolPresentation(R.string.fossin_drama, AppIcons.AutoAwesome)
    EditorTool.Vintage -> EditorToolPresentation(R.string.fossin_vintage, AppIcons.FilterVintage)
    EditorTool.GrainyFilm -> EditorToolPresentation(R.string.fossin_grainy_film, AppIcons.Grain)
    EditorTool.Retrolux -> EditorToolPresentation(R.string.fossin_retrolux, AppIcons.FilterVintage)
    EditorTool.Grunge -> EditorToolPresentation(R.string.fossin_grunge, AppIcons.FilterVintage)
    EditorTool.BlackWhite -> EditorToolPresentation(R.string.fossin_black_white, AppIcons.Contrast)
    EditorTool.Noir -> EditorToolPresentation(R.string.fossin_noir, AppIcons.Contrast)
    EditorTool.Portrait -> EditorToolPresentation(R.string.fossin_portrait, AppIcons.AutoAwesome)
    EditorTool.FaceEnhance -> EditorToolPresentation(R.string.fossin_face_enhance, AppIcons.AutoAwesome)
    EditorTool.HeadPose -> EditorToolPresentation(R.string.fossin_head_pose, AppIcons.AutoAwesome)
    EditorTool.Frame -> EditorToolPresentation(R.string.fossin_frame, AppIcons.Crop169)
    EditorTool.DoubleExposure -> EditorToolPresentation(R.string.fossin_double_exposure, AppIcons.AddPhotoAlternate)
    EditorTool.Text -> EditorToolPresentation(R.string.fossin_text, AppIcons.Article)
}

private fun EditorTool.usesDirectCanvas(): Boolean = when (this) {
    EditorTool.Crop,
    EditorTool.Selective,
    EditorTool.Brush,
    EditorTool.Healing,
    EditorTool.LensBlur,
    -> true
    else -> false
}

private fun EditorTool.needsGestureContextPanel(): Boolean = when (this) {
    EditorTool.Looks,
    EditorTool.Crop,
    EditorTool.Hsl,
    EditorTool.Selective,
    EditorTool.Brush,
    EditorTool.Healing,
    EditorTool.LensBlur,
    EditorTool.DoubleExposure,
    EditorTool.Text,
    -> true
    else -> false
}

private const val FOSSIN_EDITOR_PREFERENCES = "fossin_editor_preferences"
private const val FOSSIN_GESTURE_MODE_ENABLED = "gesture_mode_enabled"
private const val GESTURE_TOUCH_SLOP_DP = 14f
private const val GESTURE_DIRECTION_TURN_SLOP_DP = 12f
private const val GESTURE_FULL_RANGE_DP = 240f
private const val GESTURE_PARAMETER_STEP_DP = 52f
private const val GESTURE_PREVIEW_MAX_EDGE = 1024
private const val FOSSIN_LIBRARY_PREFERENCES = "fossin_library_preferences"
private const val FOSSIN_LIBRARY_ENTRIES = "recent_entries"
private const val FOSSIN_LIBRARY_MAX_ENTRIES = 72
private const val FOSSIN_PROJECTS_DIRECTORY = "fossin-projects"
private const val FOSSIN_PROJECT_MANIFEST_FILE = "photo-editor-sidecar.json"
private const val FOSSIN_PROJECT_SCHEMA_VERSION = 1

private enum class FossinLibraryKind(@StringRes val labelRes: Int) {
    Imported(R.string.fossin_library_imported),
    Edited(R.string.fossin_library_edited),
    Camera(R.string.fossin_library_camera_photo),
}

private data class FossinLibraryItem(
    val uri: Uri,
    val kind: FossinLibraryKind,
    val timestamp: Long,
    val name: String? = null,
    val projectId: String? = null,
)

private enum class FossinFlattenExportMode { KeepProject, PhotoOnly }

/**
 * Small local-only index for photos the editor has opened or exported. Camera photos are
 * intentionally read from the camera gallery instead, so this never tries to duplicate them.
 */
private object FossinLibraryStore {
    fun entries(context: Context): List<FossinLibraryItem> {
        val prefs = context.applicationContext.getSharedPreferences(
            FOSSIN_LIBRARY_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        return prefs.getStringSet(FOSSIN_LIBRARY_ENTRIES, emptySet())
            .orEmpty()
            .mapNotNull(::decode)
            .sortedByDescending(FossinLibraryItem::timestamp)
    }

    fun record(context: Context, uri: Uri, kind: FossinLibraryKind) {
        if (uri.scheme !in setOf("content", "file")) return
        val uniqueEntries = entries(context)
            .filterNot { it.uri == uri }
            .toMutableList()
        uniqueEntries += FossinLibraryItem(
            uri = uri,
            kind = kind,
            timestamp = System.currentTimeMillis(),
        )
        val encoded = uniqueEntries
            .sortedByDescending(FossinLibraryItem::timestamp)
            .take(FOSSIN_LIBRARY_MAX_ENTRIES)
            .map(::encode)
            .toSet()
        context.applicationContext
            .getSharedPreferences(FOSSIN_LIBRARY_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(FOSSIN_LIBRARY_ENTRIES, encoded)
            .apply()
    }

    private fun encode(entry: FossinLibraryItem): String = listOf(
        entry.timestamp.toString(),
        entry.kind.name,
        Uri.encode(entry.uri.toString()),
    ).joinToString("|")

    private fun decode(value: String): FossinLibraryItem? = runCatching {
        val parts = value.split('|', limit = 3)
        FossinLibraryItem(
            uri = Uri.parse(Uri.decode(parts[2])),
            kind = FossinLibraryKind.valueOf(parts[1]),
            timestamp = parts[0].toLong(),
        )
    }.getOrNull()
}

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

/** Maps a continuous vertical drag to an item in the visible parameter list. */
internal fun snapseedParameterIndexForDrag(
    startIndex: Int,
    parameterCount: Int,
    verticalDistancePx: Float,
    stepDistancePx: Float,
): Int {
    if (parameterCount <= 0) return 0
    val steps = (verticalDistancePx / stepDistancePx.coerceAtLeast(1f)).roundToInt()
    return (startIndex - steps).coerceIn(0, parameterCount - 1)
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

internal enum class SnapseedGesturePhase { Pending, Selecting, Adjusting }

/** Chooses the first gesture phase once the user has moved past touch slop. */
internal fun snapseedInitialGesturePhase(
    horizontalDistancePx: Float,
    verticalDistancePx: Float,
    touchSlopPx: Float,
): SnapseedGesturePhase {
    if (maxOf(kotlin.math.abs(horizontalDistancePx), kotlin.math.abs(verticalDistancePx)) < touchSlopPx) {
        return SnapseedGesturePhase.Pending
    }
    return if (kotlin.math.abs(verticalDistancePx) > kotlin.math.abs(horizontalDistancePx)) {
        SnapseedGesturePhase.Selecting
    } else {
        SnapseedGesturePhase.Adjusting
    }
}

/** Detects an intentional turn from a vertical parameter browse into horizontal adjustment. */
internal fun snapseedShouldBeginHorizontalAdjustment(
    horizontalSinceSelectionPx: Float,
    latestHorizontalDeltaPx: Float,
    latestVerticalDeltaPx: Float,
    turnSlopPx: Float,
): Boolean =
    kotlin.math.abs(horizontalSinceSelectionPx) >= turnSlopPx &&
        kotlin.math.abs(latestHorizontalDeltaPx) > kotlin.math.abs(latestVerticalDeltaPx)

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
    val gradingShadowHue: Float = 0f,
    val gradingShadowAmount: Float = 0f,
    val gradingShadowLuminance: Float = 0f,
    val gradingMidtoneHue: Float = 0f,
    val gradingMidtoneAmount: Float = 0f,
    val gradingMidtoneLuminance: Float = 0f,
    val gradingHighlightHue: Float = 0f,
    val gradingHighlightAmount: Float = 0f,
    val gradingHighlightLuminance: Float = 0f,
    val gradingBlending: Float = 0.5f,
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
            putFloat("gradingShadowHue", state.gradingShadowHue)
            putFloat("gradingShadowAmount", state.gradingShadowAmount)
            putFloat("gradingShadowLuminance", state.gradingShadowLuminance)
            putFloat("gradingMidtoneHue", state.gradingMidtoneHue)
            putFloat("gradingMidtoneAmount", state.gradingMidtoneAmount)
            putFloat("gradingMidtoneLuminance", state.gradingMidtoneLuminance)
            putFloat("gradingHighlightHue", state.gradingHighlightHue)
            putFloat("gradingHighlightAmount", state.gradingHighlightAmount)
            putFloat("gradingHighlightLuminance", state.gradingHighlightLuminance)
            putFloat("gradingBlending", state.gradingBlending)
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
            gradingShadowHue = bundle.getFloat("gradingShadowHue", 0f),
            gradingShadowAmount = bundle.getFloat("gradingShadowAmount", 0f),
            gradingShadowLuminance = bundle.getFloat("gradingShadowLuminance", 0f),
            gradingMidtoneHue = bundle.getFloat("gradingMidtoneHue", 0f),
            gradingMidtoneAmount = bundle.getFloat("gradingMidtoneAmount", 0f),
            gradingMidtoneLuminance = bundle.getFloat("gradingMidtoneLuminance", 0f),
            gradingHighlightHue = bundle.getFloat("gradingHighlightHue", 0f),
            gradingHighlightAmount = bundle.getFloat("gradingHighlightAmount", 0f),
            gradingHighlightLuminance = bundle.getFloat("gradingHighlightLuminance", 0f),
            gradingBlending = bundle.getFloat("gradingBlending", 0.5f),
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

private object FossinProjectSaverScope : SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
}

private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

private data class FossinProjectSummary(
    val id: String,
    val originalUri: Uri,
    val originalSourceUri: String?,
    val title: String,
    val updatedAt: Long,
)

private data class FossinEditableProject(
    val id: String,
    val originalUri: Uri,
    val state: EditorState,
    val title: String,
)

/**
 * Non-destructive project storage. Each project owns a local original and sidecar, so edits are
 * still recoverable when the source document URI or an imported LUT is no longer available.
 */
private object FossinProjectStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val writeMutex = Mutex()

    fun newProjectId(): String = UUID.randomUUID().toString()

    suspend fun save(
        context: Context,
        projectId: String,
        sourceUri: Uri,
        state: EditorState,
    ): Boolean = writeMutex.withLock {
        withContext(Dispatchers.IO) {
        runCatching {
            val directory = projectDirectory(context, projectId).apply { mkdirs() }
            val previous = readManifest(directory)
            val previousOriginalFile = previous?.stringOrNull("originalFile")
            val previousOriginalSource = previous?.stringOrNull("originalSourceUri")
            val originalExtension = extensionFor(context, sourceUri, "jpg")
            val originalFileName = previousOriginalFile ?: "original.$originalExtension"
            val originalFile = File(directory, originalFileName)
            val sourceIsStoredOriginal = sourceUri.isSameLocalFile(originalFile)
            if (!originalFile.isFile || (!sourceIsStoredOriginal && previousOriginalSource != sourceUri.toString())) {
                copyUriToFile(context, sourceUri, originalFile)
            }

            val overlayFileName = syncAsset(
                context = context,
                directory = directory,
                source = state.overlayUri,
                previousSource = previous?.stringOrNull("overlaySourceUri"),
                previousFile = previous?.stringOrNull("overlayFile"),
                baseName = "overlay",
                fallbackExtension = "jpg",
            )
            val lutFileName = syncAsset(
                context = context,
                directory = directory,
                source = state.lutUri,
                previousSource = previous?.stringOrNull("lutSourceUri"),
                previousFile = previous?.stringOrNull("lutFile"),
                baseName = "look",
                fallbackExtension = "cube",
            )
            val storedState = state.copy(
                lut = null,
                overlayUri = overlayFileName?.let { Uri.fromFile(File(directory, it)).toString() },
                lutUri = lutFileName?.let { Uri.fromFile(File(directory, it)).toString() },
            )
            val savedBundle = with(editorStateSaver) { FossinProjectSaverScope.save(storedState) } ?: Bundle()
            val sidecar = JsonObject().apply {
                addProperty("schemaVersion", FOSSIN_PROJECT_SCHEMA_VERSION)
                addProperty("id", projectId)
                addProperty("title", displayName(context, sourceUri))
                addProperty("updatedAt", System.currentTimeMillis())
                addProperty("originalFile", originalFileName)
                addProperty("originalSourceUri", if (sourceIsStoredOriginal) previousOriginalSource else sourceUri.toString())
                overlayFileName?.let { addProperty("overlayFile", it) }
                state.overlayUri?.let { addProperty("overlaySourceUri", it) }
                lutFileName?.let { addProperty("lutFile", it) }
                state.lutUri?.let { addProperty("lutSourceUri", it) }
                add("layers", bundleToJson(savedBundle))
                addProperty("editorStatePayload", encodeBundle(savedBundle))
            }
            writeAtomically(File(directory, FOSSIN_PROJECT_MANIFEST_FILE), gson.toJson(sidecar))
            true
        }.getOrDefault(false)
        }
    }

    suspend fun load(context: Context, projectId: String): FossinEditableProject? = withContext(Dispatchers.IO) {
        val directory = projectDirectory(context, projectId)
        val manifest = readManifest(directory) ?: return@withContext null
        val originalFile = manifest.stringOrNull("originalFile")?.let { File(directory, it) }
            ?.takeIf(File::isFile)
            ?: return@withContext null
        val bundle = manifest.stringOrNull("editorStatePayload")?.let(::decodeBundle)
            ?: return@withContext null
        val state = editorStateSaver.restore(bundle) ?: return@withContext null
        FossinEditableProject(
            id = projectId,
            originalUri = Uri.fromFile(originalFile),
            state = state,
            title = manifest.stringOrNull("title") ?: originalFile.name,
        )
    }

    suspend fun list(context: Context): List<FossinProjectSummary> = withContext(Dispatchers.IO) {
        projectsRoot(context).listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.mapNotNull { directory ->
                val manifest = readManifest(directory) ?: return@mapNotNull null
                val originalFile = manifest.stringOrNull("originalFile")?.let { File(directory, it) }
                    ?.takeIf(File::isFile)
                    ?: return@mapNotNull null
                FossinProjectSummary(
                    id = directory.name,
                    originalUri = Uri.fromFile(originalFile),
                    originalSourceUri = manifest.stringOrNull("originalSourceUri"),
                    title = manifest.stringOrNull("title") ?: originalFile.name,
                    updatedAt = manifest.longOrDefault("updatedAt", originalFile.lastModified()),
                )
            }
            ?.sortedByDescending(FossinProjectSummary::updatedAt)
            ?.toList()
            ?: emptyList()
    }

    suspend fun exportArchive(context: Context, projectId: String, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val directory = projectDirectory(context, projectId)
            val manifest = readManifest(directory) ?: return@runCatching false
            val originalFile = manifest.stringOrNull("originalFile")?.let { File(directory, it) }
                ?.takeIf(File::isFile)
                ?: return@runCatching false
            val archiveSidecar = manifest.deepCopy().apply {
                addProperty("originalFile", "original/${originalFile.name}")
                addProperty("archiveFormat", "photo-editor-editable-package")
                val assets = JsonObject().apply {
                    addProperty("original", "original/${originalFile.name}")
                    manifest.stringOrNull("overlayFile")?.let { addProperty("overlay", "assets/$it") }
                    manifest.stringOrNull("lutFile")?.let { addProperty("lut", "assets/$it") }
                }
                add("archiveAssets", assets)
            }
            context.contentResolver.openOutputStream(destination)?.use { output ->
                ZipOutputStream(output).use { archive ->
                    putFile(archive, originalFile, "original/${originalFile.name}")
                    putText(archive, gson.toJson(archiveSidecar), FOSSIN_PROJECT_MANIFEST_FILE)
                    manifest.stringOrNull("overlayFile")?.let { File(directory, it) }
                        ?.takeIf(File::isFile)
                        ?.let { putFile(archive, it, "assets/${it.name}") }
                    manifest.stringOrNull("lutFile")?.let { File(directory, it) }
                        ?.takeIf(File::isFile)
                        ?.let { putFile(archive, it, "assets/${it.name}") }
                }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    suspend fun delete(context: Context, projectId: String) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            if (runCatching { UUID.fromString(projectId) }.isFailure) return@withContext
            projectDirectory(context, projectId).deleteRecursively()
        }
    }

    private fun syncAsset(
        context: Context,
        directory: File,
        source: String?,
        previousSource: String?,
        previousFile: String?,
        baseName: String,
        fallbackExtension: String,
    ): String? {
        val sourceUri = source?.let(Uri::parse) ?: return null
        val existing = previousFile?.let { File(directory, it) }
        if (source == previousSource && existing?.isFile == true) return previousFile
        val fileName = "$baseName.${extensionFor(context, sourceUri, fallbackExtension)}"
        copyUriToFile(context, sourceUri, File(directory, fileName))
        return fileName
    }

    private fun projectsRoot(context: Context): File = File(context.filesDir, FOSSIN_PROJECTS_DIRECTORY)

    private fun projectDirectory(context: Context, projectId: String): File = File(projectsRoot(context), projectId)

    private fun readManifest(directory: File): JsonObject? = runCatching {
        val file = File(directory, FOSSIN_PROJECT_MANIFEST_FILE)
        JsonParser.parseString(file.readText()).asJsonObject
    }.getOrNull()

    private fun copyUriToFile(context: Context, uri: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        openUriStream(context, uri)?.use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException(uri.toString())
    }

    private fun writeAtomically(destination: File, contents: String) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeText(contents)
        if (!temporary.renameTo(destination)) {
            destination.delete()
            check(temporary.renameTo(destination)) { "Could not save ${destination.name}" }
        }
    }

    private fun putFile(archive: ZipOutputStream, file: File, path: String) {
        archive.putNextEntry(ZipEntry(path))
        FileInputStream(file).use { it.copyTo(archive) }
        archive.closeEntry()
    }

    private fun putText(archive: ZipOutputStream, contents: String, path: String) {
        archive.putNextEntry(ZipEntry(path))
        archive.write(contents.toByteArray(Charsets.UTF_8))
        archive.closeEntry()
    }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.longOrDefault(name: String, default: Long): Long =
        runCatching { get(name).asLong }.getOrDefault(default)
}

private fun Bundle.toSidecarJson(): JsonObject = JsonObject().also { output ->
    keySet().forEach { key ->
        when (val value = get(key)) {
            is String -> output.addProperty(key, value)
            is Float -> output.addProperty(key, value)
            is Int -> output.addProperty(key, value)
            is FloatArray -> output.add(key, JsonArray().apply { value.forEach(::add) })
        }
    }
}

private fun bundleToJson(bundle: Bundle): JsonObject = bundle.toSidecarJson()

private fun encodeBundle(bundle: Bundle): String {
    val parcel = Parcel.obtain()
    return try {
        bundle.writeToParcel(parcel, 0)
        Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
    } finally {
        parcel.recycle()
    }
}

private fun decodeBundle(payload: String): Bundle? = runCatching {
    val parcel = Parcel.obtain()
    try {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        Bundle.CREATOR.createFromParcel(parcel)
    } finally {
        parcel.recycle()
    }
}.getOrNull()

private fun Uri.isSameLocalFile(file: File): Boolean = scheme == "file" && runCatching {
    File(path.orEmpty()).canonicalFile == file.canonicalFile
}.getOrDefault(false)

private fun extensionFor(context: Context, uri: Uri, fallback: String): String {
    val name = displayName(context, uri)
    return name.substringAfterLast('.', missingDelimiterValue = fallback)
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: fallback
}

private fun displayName(context: Context, uri: Uri): String = when (uri.scheme) {
    "file" -> File(uri.path.orEmpty()).name
    else -> context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "photo"
}

private fun openUriStream(context: Context, uri: Uri) = when (uri.scheme) {
    "file" -> uri.path?.let(::FileInputStream)
    else -> context.contentResolver.openInputStream(uri)
}

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
    var showingHall by rememberSaveable { mutableStateOf(initialUri == null) }
    var overlay by remember { mutableStateOf<Bitmap?>(null) }
    var sourceUri by rememberSaveable { mutableStateOf<Uri?>(initialUri) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var tool by remember { mutableStateOf(EditorTool.Looks) }
    var editState by rememberSaveable(stateSaver = editorStateSaver) { mutableStateOf(EditorState()) }
    val undoStack = remember { mutableStateListOf<EditorState>() }
    val redoStack = remember { mutableStateListOf<EditorState>() }
    var showOriginal by remember { mutableStateOf(false) }
    var isRendering by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var flattenExportMode by remember { mutableStateOf(FossinFlattenExportMode.KeepProject) }
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
    var showGestureToolPanel by remember { mutableStateOf(false) }
    val processor = remember { LutImageProcessor(context.applicationContext) }
    val gesturePreview = remember(source) {
        source?.let { bitmap -> scaledPreviewBitmap(bitmap, GESTURE_PREVIEW_MAX_EDGE) }
    }
    DisposableEffect(gesturePreview, source) {
        onDispose {
            if (gesturePreview != null && gesturePreview !== source && !gesturePreview.isRecycled) {
                gesturePreview.recycle()
            }
        }
    }
    fun ensureProject() {
        if (projectId == null && sourceUri != null && source != null) {
            projectId = FossinProjectStore.newProjectId()
        }
    }
    fun updateEdit(transform: (EditorState) -> EditorState) {
        val next = transform(editState)
        if (next == editState) return
        ensureProject()
        undoStack.add(editState)
        if (undoStack.size > 48) undoStack.removeAt(0)
        editState = next
        redoStack.clear()
        renderVersion += 1
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        ensureProject()
        redoStack.add(editState)
        editState = undoStack.removeAt(undoStack.lastIndex)
        renderVersion += 1
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        ensureProject()
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
        ensureProject()
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
        if (projectId == null && sourceUri != null) {
            projectId = FossinProjectStore.newProjectId()
        }
        if (resetEdits) {
            overlay = null
            editState = EditorState()
            undoStack.clear()
            redoStack.clear()
        }
        gestureBase = null
        showOriginal = false
        showGestureToolPanel = false
        tool = EditorTool.Looks
    }
    DisposableEffect(processor) {
        onDispose { processor.release() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            persistReadPermission(context, selectedUri)
            projectId = null
            scope.launch {
                loadImage(context, selectedUri) { bitmap ->
                    sourceUri = selectedUri
                    FossinLibraryStore.record(context, selectedUri, FossinLibraryKind.Imported)
                    acceptImage(bitmap)
                    showingHall = false
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
                        openUriStream(context, selectedUri)?.use {
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
            val modeAtExport = flattenExportMode
            val projectAtExport = projectId
            val sourceUriAtExport = sourceUri
            val stateAtExport = editState
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
                    if (saved) {
                        persistReadPermission(context, target)
                        FossinLibraryStore.record(context, target, FossinLibraryKind.Edited)
                        if (projectAtExport != null && sourceUriAtExport != null) {
                            if (modeAtExport == FossinFlattenExportMode.KeepProject) {
                                FossinProjectStore.save(context, projectAtExport, sourceUriAtExport, stateAtExport)
                            } else {
                                if (projectId == projectAtExport) projectId = null
                                FossinProjectStore.delete(context, projectAtExport)
                            }
                        }
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
    val editablePackagePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { target ->
            val projectAtExport = projectId
            val sourceUriAtExport = sourceUri
            val stateAtExport = editState
            if (projectAtExport == null || sourceUriAtExport == null) return@let
            scope.launch {
                isExporting = true
                val saved = FossinProjectStore.save(context, projectAtExport, sourceUriAtExport, stateAtExport)
                val archived = saved && FossinProjectStore.exportArchive(context, projectAtExport, target)
                isExporting = false
                Toast.makeText(
                    context,
                    if (archived) R.string.fossin_export_package_done else R.string.fossin_export_package_failed,
                    Toast.LENGTH_LONG,
                ).show()
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
            if (uri == initialUri) {
                FossinLibraryStore.record(context, uri, FossinLibraryKind.Imported)
            }
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
                openUriStream(context, Uri.parse(uriText))?.use {
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
    LaunchedEffect(projectId, sourceUri, source, editState) {
        val id = projectId ?: return@LaunchedEffect
        val uri = sourceUri ?: return@LaunchedEffect
        if (source == null) return@LaunchedEffect
        delay(350)
        FossinProjectStore.save(context, id, uri, editState)
    }
    LaunchedEffect(source, editState, overlay, renderVersion, gestureBase != null) {
        val input = source ?: return@LaunchedEffect
        val useFastPreview = gestureBase != null && gesturePreview != null
        if (useFastPreview) delay(24)
        isRendering = true
        rendered = withContext(Dispatchers.Default) {
            try {
                renderEditorBitmap(processor, if (useFastPreview) gesturePreview!! else input, editState, overlay)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                input
            }
        }
        isRendering = false
    }

    fun leaveEditor() {
        if (initialUri != null) {
            onFinish()
            return
        }
        finishGesture()
        source = null
        sourceUri = null
        projectId = null
        rendered = null
        overlay = null
        editState = EditorState()
        undoStack.clear()
        redoStack.clear()
        showOriginal = false
        showGestureToolPanel = false
        showingHall = true
    }
    fun openFromHall(item: FossinLibraryItem) {
        scope.launch {
            val project = item.projectId?.let { FossinProjectStore.load(context, it) }
            finishGesture()
            source = null
            rendered = null
            overlay = null
            sourceUri = project?.originalUri ?: item.uri
            projectId = project?.id
                ?: FossinProjectStore.newProjectId()
            editState = project?.state ?: EditorState()
            undoStack.clear()
            redoStack.clear()
            showOriginal = false
            showGestureToolPanel = false
            showingHall = false
        }
    }
    if (showingHall) {
        BackHandler { onFinish() }
        FossinHall(
            onImport = { imagePicker.launch(arrayOf("image/*")) },
            onOpenCamera = onOpenCamera,
            onOpenPhoto = ::openFromHall,
        )
        return
    }

    if (showExportDialog) {
        FossinExportDialog(
            onKeepProject = {
                flattenExportMode = FossinFlattenExportMode.KeepProject
                showExportDialog = false
                exportPicker.launch("photo-editor-edit.jpg")
            },
            onPhotoOnly = {
                flattenExportMode = FossinFlattenExportMode.PhotoOnly
                showExportDialog = false
                exportPicker.launch("photo-editor-edit.jpg")
            },
            onExportPackage = {
                showExportDialog = false
                editablePackagePicker.launch("photo-editor-editable.zip")
            },
            onDismiss = { showExportDialog = false },
        )
    }

    BackHandler { leaveEditor() }
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
        if (!enabled) {
            showGestureFeedback = false
            showGestureToolPanel = false
        }
    }
    LaunchedEffect(gestureFeedbackToken) {
        if (gestureFeedbackToken == 0) return@LaunchedEffect
        val token = gestureFeedbackToken
        delay(1500)
        if (gestureFeedbackToken == token) showGestureFeedback = false
    }
    fun selectGestureTool(nextTool: EditorTool) {
        if (nextTool == tool) {
            if (nextTool.needsGestureContextPanel()) showGestureToolPanel = true
            return
        }
        finishGesture()
        tool = nextTool
        showGestureToolPanel = false
        selectedGestureParameterKey = snapseedGestureParameters(
            nextTool,
            editState,
            selectedHslChannel,
            overlay != null,
        ).firstOrNull()?.key
        refreshGestureFeedback()
    }
    val image = if (showOriginal || (tool == EditorTool.Crop && editState.cropMode == CropMode.Free)) source else rendered ?: source
    val immersiveGestureMode = gestureModeEnabled && source != null
    val imageContentModifier = Modifier
        .fillMaxSize()
        .then(if (immersiveGestureMode) Modifier else Modifier.clip(RoundedCornerShape(18.dp)))
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0B0C)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (immersiveGestureMode) Modifier
                        else Modifier.statusBarsPadding().navigationBarsPadding(),
                    ),
            ) {
                if (!immersiveGestureMode) {
                    EditorTopBar(
                        hasImage = source != null,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        showingOriginal = showOriginal,
                        onBack = ::leaveEditor,
                        onImport = { imagePicker.launch(arrayOf("image/*")) },
                        onExport = { showExportDialog = true },
                        onShare = { shareEditedImage() },
                        onUndo = ::undo,
                        onRedo = ::redo,
                        onReset = ::resetEdit,
                        onToggleOriginal = { showOriginal = !showOriginal },
                    )
                }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (immersiveGestureMode) 0.dp else 12.dp)
                    .pointerInput(tool, editState.cropMode, gestureModeEnabled, source != null) {
                        val cropGesture = tool == EditorTool.Crop && editState.cropMode == CropMode.Free
                        val selectiveGesture = tool == EditorTool.Selective
                        val lensBlurGesture = tool == EditorTool.LensBlur
                        val supportsGestureMode = gestureModeEnabled &&
                            source != null &&
                            !tool.usesDirectCanvas() &&
                            currentGestureParameters.isNotEmpty()
                        if (supportsGestureMode) {
                            var phase = SnapseedGesturePhase.Pending
                            var totalHorizontal = 0f
                            var totalVertical = 0f
                            var horizontalSinceSelection = 0f
                            var adjustmentHorizontal = 0f
                            var horizontalStartValue = 0f
                            var horizontalParameter: GestureParameter? = null
                            var verticalStartIndex = 0
                            detectDragGestures(
                                onDragStart = {
                                    phase = SnapseedGesturePhase.Pending
                                    totalHorizontal = 0f
                                    totalVertical = 0f
                                    horizontalSinceSelection = 0f
                                    adjustmentHorizontal = 0f
                                    horizontalParameter = null
                                },
                                onDrag = { change, dragAmount ->
                                    totalHorizontal += dragAmount.x
                                    totalVertical += dragAmount.y
                                    if (phase == SnapseedGesturePhase.Pending) {
                                        phase = snapseedInitialGesturePhase(
                                            horizontalDistancePx = totalHorizontal,
                                            verticalDistancePx = totalVertical,
                                            touchSlopPx = GESTURE_TOUCH_SLOP_DP.dp.toPx(),
                                        )
                                        if (phase == SnapseedGesturePhase.Selecting) {
                                            val parameters = currentGestureParameters
                                            verticalStartIndex = parameters.indexOfFirst { it.key == currentSelectedGestureParameterKey }
                                                .takeIf { it >= 0 } ?: 0
                                            selectedGestureParameterKey = parameters[verticalStartIndex].key
                                            refreshGestureFeedback()
                                        } else if (phase == SnapseedGesturePhase.Adjusting) {
                                            horizontalParameter = currentGestureParameters.firstOrNull {
                                                it.key == currentSelectedGestureParameterKey
                                            } ?: currentGestureParameters.first()
                                            horizontalStartValue = horizontalParameter?.value ?: 0f
                                            adjustmentHorizontal = totalHorizontal - dragAmount.x
                                            beginGesture()
                                        }
                                    }
                                    if (phase == SnapseedGesturePhase.Selecting) {
                                        val parameters = currentGestureParameters
                                        if (parameters.isNotEmpty()) {
                                            val index = snapseedParameterIndexForDrag(
                                                verticalStartIndex,
                                                parameters.size,
                                                totalVertical,
                                                GESTURE_PARAMETER_STEP_DP.dp.toPx(),
                                            )
                                            selectedGestureParameterKey = parameters[index].key
                                            refreshGestureFeedback()
                                        }
                                        horizontalSinceSelection += dragAmount.x
                                        if (snapseedShouldBeginHorizontalAdjustment(
                                                horizontalSinceSelectionPx = horizontalSinceSelection,
                                                latestHorizontalDeltaPx = dragAmount.x,
                                                latestVerticalDeltaPx = dragAmount.y,
                                                turnSlopPx = GESTURE_DIRECTION_TURN_SLOP_DP.dp.toPx(),
                                            )
                                        ) {
                                            horizontalParameter = parameters.firstOrNull {
                                                it.key == selectedGestureParameterKey
                                            } ?: parameters.firstOrNull()
                                            horizontalStartValue = horizontalParameter?.value ?: 0f
                                            adjustmentHorizontal = 0f
                                            phase = SnapseedGesturePhase.Adjusting
                                            beginGesture()
                                        }
                                    }
                                    if (phase == SnapseedGesturePhase.Adjusting) {
                                        adjustmentHorizontal += dragAmount.x
                                        horizontalParameter?.let { parameter ->
                                            val value = snapseedAdjustedValue(
                                                startValue = horizontalStartValue,
                                                range = parameter.range,
                                                horizontalDistancePx = adjustmentHorizontal,
                                                fullRangeDistancePx = GESTURE_FULL_RANGE_DP.dp.toPx(),
                                            )
                                            previewEdit { state -> parameter.update(state, value) }
                                            refreshGestureFeedback()
                                        }
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (phase == SnapseedGesturePhase.Adjusting) finishGesture()
                                },
                                onDragCancel = {
                                    if (phase == SnapseedGesturePhase.Adjusting) finishGesture()
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
                else Image(
                    image.asImageBitmap(),
                    stringResource(R.string.fossin_preview),
                    imageContentModifier,
                    contentScale = ContentScale.Fit,
                )
                if (source != null && (tool == EditorTool.Brush || tool == EditorTool.Healing)) {
                    ComposeCanvas(imageContentModifier) {
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
                    ComposeCanvas(imageContentModifier) {
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
                    ComposeCanvas(imageContentModifier) {
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
                    ComposeCanvas(imageContentModifier) {
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
                if (source != null && !immersiveGestureMode) {
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
                if (isRendering || isExporting) LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.75f))
            }
            if (source != null && !immersiveGestureMode) {
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
            if (immersiveGestureMode) {
                GestureModeTopBar(
                    hasImage = true,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    showingOriginal = showOriginal,
                    gestureModeEnabled = gestureModeEnabled,
                    onBack = ::leaveEditor,
                    onImport = { imagePicker.launch(arrayOf("image/*")) },
                    onExport = { showExportDialog = true },
                    onShare = { shareEditedImage() },
                    onUndo = ::undo,
                    onRedo = ::redo,
                    onReset = ::resetEdit,
                    onToggleOriginal = { showOriginal = !showOriginal },
                    onToggleGestureMode = { setGestureMode(false) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding(),
                )
                GestureDock(
                    selectedTool = tool,
                    parameters = gestureParameters,
                    selectedParameterKey = activeGestureParameter?.key,
                    showParameters = showGestureFeedback && !tool.usesDirectCanvas(),
                    onToolSelect = ::selectGestureTool,
                    onParameterSelect = { key ->
                        selectedGestureParameterKey = key
                        refreshGestureFeedback()
                    },
                    onOpenContext = { showGestureToolPanel = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                )
                if (showGestureToolPanel) {
                    GestureContextSheet(
                        title = stringResource(editorToolPresentation(tool).labelRes),
                        onDismiss = { showGestureToolPanel = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    ) {
                        when (tool) {
                            EditorTool.Looks -> LooksPanel(editState.style, editState.lut, builtIns, editState.intensity, { value -> updateEdit { it.copy(style = value) } }, { value -> updateEdit { it.copy(lut = value, lutName = value?.name, lutUri = null) } }, { lutPicker.launch(arrayOf("application/*", "text/plain", "*/*")) }) { value -> updateEdit { it.copy(intensity = value) } }
                            EditorTool.Crop -> CropPanel(editState.cropMode) { value -> updateEdit { it.copy(cropMode = value) } }
                            EditorTool.Hsl -> HslPanel(editState.hsl, selectedHslChannel, { selectedHslChannel = it }) { value -> updateEdit { it.copy(hsl = value(it.hsl)) } }
                            EditorTool.Selective -> SelectivePanel(editState, { updateEdit { state ->
                                val point = SelectivePoint(state.selectiveX, state.selectiveY, state.selectiveRadius, state.selectiveExposure, state.selectiveContrast, state.selectiveSaturation, state.selectiveStructure)
                                state.copy(selectivePoints = state.selectivePoints + point)
                            } }) { value -> updateEdit { value(it).withSyncedPrimarySelective() } }
                            EditorTool.Brush -> BrushPanel(editState) { value -> updateEdit { value(it) } }
                            EditorTool.Healing -> HealingPanel(editState) { value -> updateEdit { value(it) } }
                            EditorTool.LensBlur -> LensBlurPanel(editState) { value -> updateEdit { value(it) } }
                            EditorTool.DoubleExposure -> DoubleExposurePanel(overlay != null, editState.overlayAlpha, editState.overlayBlendMode, { overlayPicker.launch(arrayOf("image/*")) }, { value -> updateEdit { it.copy(overlayAlpha = value) } }, { value -> updateEdit { it.copy(overlayBlendMode = value) } })
                            EditorTool.Text -> TextPanel(editState.text, editState.textSize, editState.textOpacity, editState.textRotation, editState.textColor, editState.textStyle, { value -> updateEdit { it.copy(text = value) } }, { value -> updateEdit { it.copy(textSize = value) } }, { value -> updateEdit { it.copy(textOpacity = value) } }, { value -> updateEdit { it.copy(textRotation = value) } }, { value -> updateEdit { it.copy(textColor = value) } }, { value -> updateEdit { it.copy(textStyle = value) } })
                            else -> Unit
                        }
                    }
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
private fun FossinExportDialog(
    onKeepProject: () -> Unit,
    onPhotoOnly: () -> Unit,
    onExportPackage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fossin_export_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.fossin_export_choice_message))
                Text(
                    text = stringResource(R.string.fossin_export_package_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                TextButton(onClick = onExportPackage) {
                    Text(stringResource(R.string.fossin_export_editable_package))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKeepProject) {
                Text(stringResource(R.string.fossin_export_keep_project))
            }
        },
        dismissButton = {
            TextButton(onClick = onPhotoOnly) {
                Text(stringResource(R.string.fossin_export_photo_only))
            }
        },
    )
}

@Composable
private fun FossinHall(
    onImport: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenPhoto: (FossinLibraryItem) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var importedAndEdited by remember { mutableStateOf<List<FossinLibraryItem>>(emptyList()) }
    var cameraPhotos by remember { mutableStateOf<List<FossinLibraryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableStateOf(0) }
    fun refreshLibrary() {
        refreshToken += 1
    }

    LaunchedEffect(refreshToken) {
        isLoading = true
        val library = withContext(Dispatchers.IO) {
            val projects = FossinProjectStore.list(context)
            val projectItems = projects.map {
                FossinLibraryItem(
                    uri = it.originalUri,
                    kind = FossinLibraryKind.Edited,
                    timestamp = it.updatedAt,
                    name = it.title,
                    projectId = it.id,
                )
            }
            val projectOriginals = projects.mapNotNull(FossinProjectSummary::originalSourceUri).toSet()
            val recent = FossinLibraryStore.entries(context)
                .filterNot { it.uri.toString() in projectOriginals }
            val camera = runCatching {
                GalleryRepository(context.applicationContext)
                    .getPhotosSync()
                    .asSequence()
                    .filter { it.isImage }
                    .map {
                        FossinLibraryItem(
                            uri = it.uri,
                            kind = FossinLibraryKind.Camera,
                            timestamp = it.dateAdded,
                            name = it.displayName,
                        )
                    }
                    .take(36)
                    .toList()
            }.getOrDefault(emptyList())
            (projectItems + recent)
                .sortedByDescending(FossinLibraryItem::timestamp)
                .take(FOSSIN_LIBRARY_MAX_ENTRIES) to camera
        }
        importedAndEdited = library.first
        cameraPhotos = library.second
        isLoading = false
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshLibrary()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0B0C)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.fossin_library_subtitle),
                        color = Color(0xFFAAAAB0),
                        fontSize = 14.sp,
                    )
                }
                IconButton(onClick = ::refreshLibrary) {
                    Icon(
                        imageVector = AppIcons.RestartAlt,
                        contentDescription = stringResource(R.string.fossin_library_refresh),
                        tint = Color.White,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FossinHallAction(
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.CameraAlt,
                    title = stringResource(R.string.fossin_open_camera),
                    subtitle = stringResource(R.string.fossin_library_camera_action),
                    onClick = onOpenCamera,
                )
                FossinHallAction(
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.AddPhotoAlternate,
                    title = stringResource(R.string.fossin_import),
                    subtitle = stringResource(R.string.fossin_library_import_action),
                    onClick = onImport,
                )
            }

            FossinHallSection(
                title = stringResource(R.string.fossin_library_imported_edited),
                emptyMessage = stringResource(R.string.fossin_library_imported_empty),
                photos = importedAndEdited,
                isLoading = isLoading,
                onOpenPhoto = onOpenPhoto,
            )
            FossinHallSection(
                title = stringResource(R.string.fossin_library_camera_photos),
                emptyMessage = stringResource(R.string.fossin_library_camera_empty),
                photos = cameraPhotos,
                isLoading = isLoading,
                onOpenPhoto = onOpenPhoto,
            )
        }
    }
}

@Composable
private fun FossinHallAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(124.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF1A1A1E),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = Color(0xFFFF7A45), modifier = Modifier.size(28.dp))
            Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color(0xFFA9A9AE), fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FossinHallSection(
    title: String,
    emptyMessage: String,
    photos: List<FossinLibraryItem>,
    isLoading: Boolean,
    onOpenPhoto: (FossinLibraryItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            photos.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(
                    items = photos,
                    key = { it.projectId ?: "${it.kind.name}:${it.uri}" },
                ) { item ->
                    FossinHallPhotoCard(item = item, onClick = { onOpenPhoto(item) })
                }
            }
            else -> Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
                color = Color(0xFF151518),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = if (isLoading) AppIcons.AutoAwesome else AppIcons.PhotoLibrary,
                        contentDescription = null,
                        tint = Color(0xFF87878E),
                    )
                    Text(
                        text = if (isLoading) stringResource(R.string.fossin_library_loading) else emptyMessage,
                        color = Color(0xFFB5B5BB),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FossinHallPhotoCard(item: FossinLibraryItem, onClick: () -> Unit) {
    val context = LocalContext.current
    var preview by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        preview = loadBitmap(context, item.uri, maxEdge = 384)
    }
    Surface(
        modifier = Modifier
            .width(140.dp)
            .height(176.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF1A1A1E),
        shape = RoundedCornerShape(18.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            preview?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = item.name ?: stringResource(item.kind.labelRes),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } ?: Icon(
                imageVector = AppIcons.Image,
                contentDescription = null,
                tint = Color(0xFF77777E),
                modifier = Modifier.align(Alignment.Center).size(34.dp),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xB8000000))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item.name ?: stringResource(item.kind.labelRes),
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(item.kind.labelRes),
                    color = Color(0xFFD0D0D5),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GestureModeTopBar(
    hasImage: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    showingOriginal: Boolean,
    gestureModeEnabled: Boolean,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onToggleOriginal: () -> Unit,
    onToggleGestureMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x780B0B0C))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(AppIcons.AutoMirroredOutlinedUndo, stringResource(R.string.fossin_undo), tint = if (canUndo) Color.White else Color(0xFF77777D))
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(AppIcons.AutoMirroredOutlinedUndo, stringResource(R.string.fossin_redo), tint = if (canRedo) Color.White else Color(0xFF77777D), modifier = Modifier.rotate(180f))
        }
        IconButton(onClick = onToggleOriginal, enabled = hasImage) {
            Icon(if (showingOriginal) AppIcons.VisibilityOff else AppIcons.Visibility, stringResource(R.string.fossin_compare), tint = Color.White)
        }
        IconButton(onClick = onReset, enabled = hasImage) {
            Icon(AppIcons.RestartAlt, stringResource(R.string.fossin_reset), tint = Color.White)
        }
        IconButton(onClick = onImport) {
            Icon(AppIcons.AddPhotoAlternate, stringResource(R.string.fossin_import), tint = Color.White)
        }
        IconButton(onClick = onShare, enabled = hasImage) {
            Icon(Icons.Default.Share, stringResource(R.string.share), tint = Color.White)
        }
        IconButton(onClick = onExport, enabled = hasImage) {
            Icon(AppIcons.Download, stringResource(R.string.fossin_export), tint = Color.White)
        }
        TextButton(onClick = onToggleGestureMode) {
            Text(
                stringResource(if (gestureModeEnabled) R.string.fossin_gestures_on else R.string.fossin_gestures_off),
                color = Color.White,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun GestureDock(
    selectedTool: EditorTool,
    parameters: List<GestureParameter>,
    selectedParameterKey: String?,
    showParameters: Boolean,
    onToolSelect: (EditorTool) -> Unit,
    onParameterSelect: (String) -> Unit,
    onOpenContext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GestureToolCarousel(
            selectedTool = selectedTool,
            onToolSelect = onToolSelect,
            onOpenContext = onOpenContext,
        )
        if (showParameters && parameters.isNotEmpty()) {
            GestureParameterMenu(
                parameters = parameters,
                selectedKey = selectedParameterKey,
                onSelect = onParameterSelect,
            )
        }
    }
}

@Composable
private fun GestureToolCarousel(
    selectedTool: EditorTool,
    onToolSelect: (EditorTool) -> Unit,
    onOpenContext: () -> Unit,
) {
    val tools = remember { EditorTool.values().toList() }
    val selectedIndex = tools.indexOf(selectedTool).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { tools.size },
    )
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    LaunchedEffect(pagerState.currentPage) {
        tools.getOrNull(pagerState.currentPage)?.let { visibleTool ->
            if (visibleTool != selectedTool) onToolSelect(visibleTool)
        }
    }
    val presentation = editorToolPresentation(selectedTool)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x780B0B0C))
            .padding(vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(presentation.labelRes),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (selectedTool.needsGestureContextPanel()) {
                TextButton(onClick = onOpenContext) {
                    Text(stringResource(R.string.fossin_gesture_options), color = Color(0xFFFFC857), fontSize = 11.sp)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sidePadding = maxOf(0.dp, (maxWidth - 56.dp) / 2)
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSize = PageSize.Fixed(56.dp),
                pageSpacing = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val tool = tools[page]
                val item = editorToolPresentation(tool)
                val selected = tool == selectedTool
                IconButton(
                    onClick = {
                        if (selected && tool.needsGestureContextPanel()) onOpenContext()
                        else onToolSelect(tool)
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color(0xFFFFB000) else Color.Transparent),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.labelRes),
                        tint = if (selected) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureParameterMenu(
    parameters: List<GestureParameter>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 216.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x8F0B0B0C))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        parameters.forEach { parameter ->
            val selected = parameter.key == selectedKey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(parameter.key) }
                    .background(if (selected) Color(0xFFFFB000) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(parameter.labelRes), color = if (selected) Color.Black else Color.White, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                Text(text = "${snapseedValuePercent(parameter.value, parameter.range)}%", color = if (selected) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun GestureContextSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(max = 360.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xE80B0B0C),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close), color = Color(0xFFFFC857))
                }
            }
            content()
        }
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
            val presentation = editorToolPresentation(tool)
            FilterChip(
                selected = selected == tool,
                onClick = { onSelect(tool) },
                label = { Text(text = stringResource(presentation.labelRes), maxLines = 1, fontSize = 10.sp) },
                leadingIcon = { Icon(imageVector = presentation.icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
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
                gradingShadowHue = state.gradingShadowHue,
                gradingShadowAmount = state.gradingShadowAmount,
                gradingShadowLuminance = state.gradingShadowLuminance,
                gradingMidtoneHue = state.gradingMidtoneHue,
                gradingMidtoneAmount = state.gradingMidtoneAmount,
                gradingMidtoneLuminance = state.gradingMidtoneLuminance,
                gradingHighlightHue = state.gradingHighlightHue,
                gradingHighlightAmount = state.gradingHighlightAmount,
                gradingHighlightLuminance = state.gradingHighlightLuminance,
                gradingBlending = state.gradingBlending,
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

private fun scaledPreviewBitmap(bitmap: Bitmap, maxEdge: Int): Bitmap {
    val largest = maxOf(bitmap.width, bitmap.height)
    if (largest <= maxEdge) return bitmap
    val scale = maxEdge.toFloat() / largest
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
}

private suspend fun loadBitmap(context: android.content.Context, uri: Uri, maxEdge: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            uri.path?.let(BitmapFactory::decodeFile)?.let { bitmap ->
                if (maxEdge > 0) scaledPreviewBitmap(bitmap, maxEdge) else bitmap
            }
        } else if (android.os.Build.VERSION.SDK_INT >= 28) {
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

/*
 * Stack editor -------------------------------------------------------------------------------
 *
 * The older editor above intentionally stays in this source file to keep historical project
 * data readable by old builds. New documents use the operation stack below and never mutate the
 * source bitmap. Keeping the stack implementation beside the existing filters lets every effect
 * use the same colour pipeline without flattening previous operations into one recipe.
 */

private const val FOSSIN_STACK_PROJECTS_DIRECTORY = "fossin-stack-projects"
private const val FOSSIN_STACK_SCHEMA_VERSION = 2
private const val STACK_MASK_EDGE = 512

private enum class StackEditorTab { Styles, Tools, Export }
private enum class StackExportMode { KeepProject, PhotoOnly }

private enum class StackToolCategory(@StringRes val labelRes: Int) {
    All(R.string.fossin_tool_category_all),
    Enhance(R.string.fossin_tool_category_enhance),
    Correct(R.string.fossin_tool_category_correct),
    Style(R.string.fossin_tool_category_style),
}

private enum class StackTool(
    @StringRes val labelRes: Int,
    val category: StackToolCategory,
    val isGlobalGeometry: Boolean = false,
    val isNew: Boolean = false,
) {
    Looks(R.string.fossin_looks, StackToolCategory.Style),
    Tune(R.string.fossin_tune, StackToolCategory.Enhance),
    Details(R.string.fossin_details, StackToolCategory.Enhance),
    Dehaze(R.string.fossin_dehaze, StackToolCategory.Enhance, isNew = true),
    TonalContrast(R.string.fossin_tonal_contrast, StackToolCategory.Enhance),
    Curves(R.string.recipe_tab_curve, StackToolCategory.Correct),
    WhiteBalance(R.string.fossin_white_balance, StackToolCategory.Correct),
    Color(R.string.recipe_tab_color, StackToolCategory.Correct),
    ColorGrading(R.string.fossin_color_grading, StackToolCategory.Correct, isNew = true),
    LensBlur(R.string.fossin_lens_blur, StackToolCategory.Enhance),
    Vignette(R.string.recipe_param_vignette, StackToolCategory.Enhance),
    Selective(R.string.fossin_selective, StackToolCategory.Correct),
    Brush(R.string.fossin_brush, StackToolCategory.Correct),
    Healing(R.string.fossin_healing, StackToolCategory.Correct),
    Perspective(R.string.fossin_perspective, StackToolCategory.Correct, isGlobalGeometry = true),
    Crop(R.string.crop, StackToolCategory.Correct, isGlobalGeometry = true),
    Expand(R.string.fossin_expand, StackToolCategory.Correct, isGlobalGeometry = true),
    HeadPose(R.string.fossin_head_pose, StackToolCategory.Correct, isGlobalGeometry = true),
    Grain(R.string.recipe_param_film_grain, StackToolCategory.Style),
    Bloom(R.string.recipe_param_bloom, StackToolCategory.Style),
    Hdr(R.string.fossin_hdr_scape, StackToolCategory.Enhance),
    ChromaticAberration(R.string.fossin_chromatic_aberration, StackToolCategory.Correct),
    Halation(R.string.fossin_halation, StackToolCategory.Style),
    Vintage(R.string.fossin_vintage, StackToolCategory.Style),
    BlackWhite(R.string.fossin_black_white, StackToolCategory.Style),
    Drama(R.string.fossin_drama, StackToolCategory.Style),
    Noir(R.string.fossin_noir, StackToolCategory.Style),
    Grunge(R.string.fossin_grunge, StackToolCategory.Style),
    DoubleExposure(R.string.fossin_double_exposure, StackToolCategory.Style),
    Frame(R.string.fossin_frame, StackToolCategory.Style, isGlobalGeometry = true),
    Text(R.string.fossin_text, StackToolCategory.Style, isGlobalGeometry = true),
}

private data class StackMask(
    val alpha: ByteArray,
    val width: Int,
    val height: Int,
    val inverted: Boolean = false,
    val feather: Float = 0.08f,
) {
    init {
        require(alpha.size == width * height)
    }

    val signature: Int get() = 31 * alpha.contentHashCode() + if (inverted) 1 else 0
}

private data class StackOperation(
    val id: String = UUID.randomUUID().toString(),
    val tool: StackTool,
    val state: EditorState = EditorState(),
    val preset: String? = null,
    val mask: StackMask? = null,
    val enabled: Boolean = true,
)

private data class StackProjectSummary(
    val id: String,
    val uri: Uri,
    val title: String,
    val updatedAt: Long,
)

private data class StackProject(
    val id: String,
    val uri: Uri,
    val title: String,
    val operations: List<StackOperation>,
)

private data class StackGestureParameter(
    val key: String,
    @StringRes val labelRes: Int,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val update: (EditorState, Float) -> EditorState,
)

private object StackProjectStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val writeMutex = Mutex()

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun save(
        context: Context,
        projectId: String,
        sourceUri: Uri,
        operations: List<StackOperation>,
    ): Boolean = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val directory = projectDirectory(context, projectId).apply { mkdirs() }
                val existing = readManifest(directory)
                val extension = extensionFor(context, sourceUri, "jpg")
                val originalName = existing?.string("originalFile") ?: "original.$extension"
                val original = File(directory, originalName)
                if (!original.isFile) copyStackUri(context, sourceUri, original)
                val serialized = JsonArray()
                operations.forEach { operation ->
                    val operationDir = File(directory, "assets").apply { mkdirs() }
                    val lutName = copyStackAsset(context, operationDir, operation.state.lutUri, "${operation.id}-look", "cube")
                    val overlayName = copyStackAsset(context, operationDir, operation.state.overlayUri, "${operation.id}-overlay", "jpg")
                    val storedState = operation.state.copy(
                        lut = null,
                        lutUri = lutName?.let { Uri.fromFile(File(operationDir, it)).toString() },
                        overlayUri = overlayName?.let { Uri.fromFile(File(operationDir, it)).toString() },
                    )
                    val bundle = with(editorStateSaver) { FossinProjectSaverScope.save(storedState) } ?: Bundle()
                    val item = JsonObject().apply {
                        addProperty("id", operation.id)
                        addProperty("tool", operation.tool.name)
                        addProperty("enabled", operation.enabled)
                        operation.preset?.let { addProperty("preset", it) }
                        addProperty("state", encodeBundle(bundle))
                        operation.mask?.let { mask ->
                            val file = File(directory, "masks/${operation.id}.png")
                            writeStackMask(file, mask)
                            add("mask", JsonObject().apply {
                                addProperty("file", "masks/${operation.id}.png")
                                addProperty("inverted", mask.inverted)
                                addProperty("feather", mask.feather)
                            })
                        }
                    }
                    serialized.add(item)
                }
                val manifest = JsonObject().apply {
                    addProperty("schemaVersion", FOSSIN_STACK_SCHEMA_VERSION)
                    addProperty("id", projectId)
                    addProperty("title", displayName(context, sourceUri))
                    addProperty("updatedAt", System.currentTimeMillis())
                    addProperty("originalFile", originalName)
                    add("operations", serialized)
                }
                writeStackText(File(directory, FOSSIN_PROJECT_MANIFEST_FILE), gson.toJson(manifest))
                true
            }.getOrDefault(false)
        }
    }

    suspend fun load(context: Context, projectId: String): StackProject? = withContext(Dispatchers.IO) {
        val directory = projectDirectory(context, projectId)
        val manifest = readManifest(directory) ?: return@withContext null
        if (manifest.int("schemaVersion") != FOSSIN_STACK_SCHEMA_VERSION) return@withContext null
        val original = manifest.string("originalFile")?.let { File(directory, it) }?.takeIf(File::isFile)
            ?: return@withContext null
        val operations = manifest.getAsJsonArray("operations")?.mapNotNull { value ->
            runCatching {
                val item = value.asJsonObject
                val state = item.string("state")?.let(::decodeBundle)?.let(editorStateSaver::restore) ?: return@runCatching null
                val mask = item.getAsJsonObject("mask")?.let { maskJson ->
                    maskJson.string("file")?.let {
                        readStackMask(File(directory, it), maskJson.bool("inverted", false), maskJson.float("feather", 0.08f))
                    }
                }
                StackOperation(
                    id = item.string("id") ?: return@runCatching null,
                    tool = item.string("tool")?.let { enumValueOf<StackTool>(it) } ?: return@runCatching null,
                    state = state,
                    preset = item.string("preset"),
                    enabled = item.bool("enabled", true),
                    mask = mask,
                )
            }.getOrNull()
        }.orEmpty()
        StackProject(projectId, Uri.fromFile(original), manifest.string("title") ?: original.name, operations)
    }

    suspend fun list(context: Context): List<StackProjectSummary> = withContext(Dispatchers.IO) {
        root(context).listFiles()?.asSequence()?.filter(File::isDirectory)?.mapNotNull { directory ->
            val manifest = readManifest(directory) ?: return@mapNotNull null
            if (manifest.int("schemaVersion") != FOSSIN_STACK_SCHEMA_VERSION) return@mapNotNull null
            val original = manifest.string("originalFile")?.let { File(directory, it) }?.takeIf(File::isFile)
                ?: return@mapNotNull null
            StackProjectSummary(
                id = directory.name,
                uri = Uri.fromFile(original),
                title = manifest.string("title") ?: original.name,
                updatedAt = manifest.long("updatedAt", original.lastModified()),
            )
        }?.sortedByDescending(StackProjectSummary::updatedAt)?.toList().orEmpty()
    }

    /**
     * Writes a portable, editable package. The rendered JPEG is deliberately not included here:
     * the package is the immutable original plus the complete operation stack, masks and the
     * local LUT/overlay assets required to render it again.
     */
    suspend fun exportArchive(context: Context, projectId: String, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val directory = projectDirectory(context, projectId)
            val manifest = readManifest(directory) ?: return@runCatching false
            val original = manifest.string("originalFile")?.let { File(directory, it) }?.takeIf(File::isFile)
                ?: return@runCatching false
            val portableManifest = manifest.deepCopy().apply {
                addProperty("archiveFormat", "photo-editor-stack-package")
                addProperty("originalFile", "original/${original.name}")
                add("archiveAssets", JsonObject().apply {
                    addProperty("original", "original/${original.name}")
                    addProperty("operations", "${FOSSIN_PROJECT_MANIFEST_FILE}")
                    addProperty("masks", "masks/")
                    addProperty("assets", "assets/")
                })
            }
            context.contentResolver.openOutputStream(destination)?.use { output ->
                ZipOutputStream(output).use { archive ->
                    putStackFile(archive, original, "original/${original.name}")
                    putStackText(archive, gson.toJson(portableManifest), FOSSIN_PROJECT_MANIFEST_FILE)
                    File(directory, "assets").listFiles()?.filter(File::isFile)?.forEach { asset ->
                        putStackFile(archive, asset, "assets/${asset.name}")
                    }
                    File(directory, "masks").listFiles()?.filter(File::isFile)?.forEach { mask ->
                        putStackFile(archive, mask, "masks/${mask.name}")
                    }
                }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    suspend fun delete(context: Context, projectId: String) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            if (runCatching { UUID.fromString(projectId) }.isFailure) return@withContext
            projectDirectory(context, projectId).deleteRecursively()
        }
    }

    private fun root(context: Context) = File(context.filesDir, FOSSIN_STACK_PROJECTS_DIRECTORY)
    private fun projectDirectory(context: Context, id: String) = File(root(context), id)
    private fun readManifest(directory: File): JsonObject? = runCatching {
        JsonParser.parseString(File(directory, FOSSIN_PROJECT_MANIFEST_FILE).readText()).asJsonObject
    }.getOrNull()

    private fun copyStackUri(context: Context, source: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        openUriStream(context, source)?.use { input ->
            FileOutputStream(destination).use(input::copyTo)
        } ?: throw FileNotFoundException(source.toString())
    }

    private fun copyStackAsset(context: Context, directory: File, source: String?, base: String, fallback: String): String? {
        val uri = source?.let(Uri::parse) ?: return null
        val name = "$base.${extensionFor(context, uri, fallback)}"
        val destination = File(directory, name)
        if (!destination.isFile) copyStackUri(context, uri, destination)
        return name
    }

    private fun writeStackMask(destination: File, mask: StackMask) {
        destination.parentFile?.mkdirs()
        val pixels = IntArray(mask.width * mask.height) { index ->
            val alpha = mask.alpha[index].toInt() and 0xff
            android.graphics.Color.argb(alpha, 255, 255, 255)
        }
        Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888).use { bitmap ->
            FileOutputStream(destination).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun readStackMask(file: File, inverted: Boolean, feather: Float): StackMask? = runCatching {
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return@runCatching null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        StackMask(ByteArray(pixels.size) { index -> android.graphics.Color.alpha(pixels[index]).toByte() }, bitmap.width, bitmap.height, inverted, feather)
    }.getOrNull()

    private fun writeStackText(destination: File, contents: String) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeText(contents)
        if (!temporary.renameTo(destination)) {
            destination.delete()
            check(temporary.renameTo(destination))
        }
    }

    private fun putStackFile(archive: ZipOutputStream, file: File, path: String) {
        archive.putNextEntry(ZipEntry(path))
        FileInputStream(file).use { it.copyTo(archive) }
        archive.closeEntry()
    }

    private fun putStackText(archive: ZipOutputStream, contents: String, path: String) {
        archive.putNextEntry(ZipEntry(path))
        archive.write(contents.toByteArray(Charsets.UTF_8))
        archive.closeEntry()
    }

    private fun JsonObject.string(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonObject.int(key: String): Int = runCatching { get(key).asInt }.getOrDefault(0)
    private fun JsonObject.long(key: String, fallback: Long): Long = runCatching { get(key).asLong }.getOrDefault(fallback)
    private fun JsonObject.float(key: String, fallback: Float): Float = runCatching { get(key).asFloat }.getOrDefault(fallback)
    private fun JsonObject.bool(key: String, fallback: Boolean): Boolean = runCatching { get(key).asBoolean }.getOrDefault(fallback)
}

private fun Bitmap.use(block: (Bitmap) -> Unit) {
    try {
        block(this)
    } finally {
        if (!isRecycled) recycle()
    }
}

private fun stackParameters(tool: StackTool, state: EditorState): List<StackGestureParameter> {
    fun parameter(
        key: String,
        @StringRes labelRes: Int,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        update: (EditorState, Float) -> EditorState,
    ) = StackGestureParameter(key, labelRes, value, range, update)
    fun effect(effect: SnapEffect, key: String, @StringRes labelRes: Int) = parameter(
        key, labelRes, state.snapEffects[effect] ?: 0f, 0f..1f,
    ) { editor, value -> editor.copy(snapEffects = editor.snapEffects + (effect to value)) }

    return when (tool) {
        StackTool.Looks -> listOf(parameter("look-strength", R.string.fossin_strength, state.intensity, 0f..1f) { editor, value -> editor.copy(intensity = value) })
        StackTool.Tune -> listOf(
            parameter("brightness", R.string.fossin_brightness, state.exposure, -2f..2f) { editor, value -> editor.copy(exposure = value) },
            parameter("contrast", R.string.fossin_contrast, state.contrast, 0.5f..1.5f) { editor, value -> editor.copy(contrast = value) },
            parameter("saturation", R.string.fossin_saturation, state.saturation, 0f..2f) { editor, value -> editor.copy(saturation = value) },
            parameter("ambiance", R.string.fossin_ambiance, state.ambiance, -1f..1f) { editor, value -> editor.copy(ambiance = value) },
            parameter("highlights", R.string.fossin_highlights, state.highlights, -1f..1f) { editor, value -> editor.copy(highlights = value) },
            parameter("shadows", R.string.fossin_tonal_shadows, state.shadows, -1f..1f) { editor, value -> editor.copy(shadows = value) },
            parameter("warmth", R.string.fossin_warmth, state.warmth, -1f..1f) { editor, value -> editor.copy(warmth = value) },
        )
        StackTool.Details -> listOf(
            parameter("structure", R.string.fossin_structure, state.detail, -1f..1f) { editor, value -> editor.copy(detail = value) },
            parameter("sharpen", R.string.fossin_sharpening, state.sharpening, -1f..1f) { editor, value -> editor.copy(sharpening = value.coerceIn(0f, 1f)) },
        )
        StackTool.Dehaze -> listOf(parameter("dehaze", R.string.fossin_dehaze, state.tonePivot, -1f..1f) { editor, value -> editor.copy(tonePivot = value, detail = value * 0.35f) })
        StackTool.TonalContrast -> listOf(
            parameter("high", R.string.fossin_tonal_highlights, state.tonalContrastHighlights, -1f..1f) { editor, value -> editor.copy(tonalContrastHighlights = value) },
            parameter("mid", R.string.fossin_tonal_midtones, state.tonalContrastMidtones, -1f..1f) { editor, value -> editor.copy(tonalContrastMidtones = value) },
            parameter("low", R.string.fossin_tonal_shadows, state.tonalContrastShadows, -1f..1f) { editor, value -> editor.copy(tonalContrastShadows = value) },
            parameter("protect-high", R.string.fossin_protect_highlights, state.toneShoulder, -1f..1f) { editor, value -> editor.copy(toneShoulder = value) },
            parameter("protect-low", R.string.fossin_protect_shadows, state.toneToe, -1f..1f) { editor, value -> editor.copy(toneToe = value) },
        )
        StackTool.Curves -> listOf(
            parameter("curve-shadow", R.string.fossin_tonal_shadows, state.toneToe, -1f..1f) { editor, value -> editor.copy(toneToe = value) },
            parameter("curve-high", R.string.fossin_tonal_highlights, state.toneShoulder, -1f..1f) { editor, value -> editor.copy(toneShoulder = value) },
        )
        StackTool.WhiteBalance -> listOf(
            parameter("temperature", R.string.fossin_temperature, state.warmth, -1f..1f) { editor, value -> editor.copy(warmth = value) },
            parameter("tint", R.string.fossin_tint, state.tint, -1f..1f) { editor, value -> editor.copy(tint = value) },
        )
        StackTool.Color -> listOf(
            parameter("vibrance", R.string.fossin_vibrance, state.vibrance, -1f..1f) { editor, value -> editor.copy(vibrance = value) },
            parameter("fade", R.string.fossin_fade, state.fade, 0f..1f) { editor, value -> editor.copy(fade = value) },
            parameter("saturation", R.string.fossin_saturation, state.saturation, 0f..2f) { editor, value -> editor.copy(saturation = value) },
        )
        StackTool.ColorGrading -> listOf(
            parameter("shadows", R.string.fossin_grading_shadows, state.gradingShadowLuminance, -1f..1f) { editor, value -> editor.copy(gradingShadowLuminance = value) },
            parameter("midtones", R.string.fossin_grading_midtones, state.gradingMidtoneLuminance, -1f..1f) { editor, value -> editor.copy(gradingMidtoneLuminance = value) },
            parameter("highlights", R.string.fossin_grading_highlights, state.gradingHighlightLuminance, -1f..1f) { editor, value -> editor.copy(gradingHighlightLuminance = value) },
            parameter("blend", R.string.fossin_grading_blending, state.gradingBlending, 0f..1f) { editor, value -> editor.copy(gradingBlending = value) },
        )
        StackTool.LensBlur -> listOf(
            parameter("blur", R.string.fossin_blur_strength, state.lensBlurStrength, 0f..1f) { editor, value -> editor.copy(lensBlurStrength = value) },
            parameter("transition", R.string.fossin_transition, state.lensBlurTransition, 0.02f..1f) { editor, value -> editor.copy(lensBlurTransition = value) },
            parameter("size", R.string.fossin_radius, state.lensBlurRadius, 0.05f..1f) { editor, value -> editor.copy(lensBlurRadius = value) },
        )
        StackTool.Vignette -> listOf(
            parameter("outer", R.string.fossin_outer_brightness, state.vignette, -1f..1f) { editor, value -> editor.copy(vignette = value) },
            parameter("inner", R.string.fossin_inner_brightness, state.vignetteInner, -1f..1f) { editor, value -> editor.copy(vignetteInner = value) },
        )
        StackTool.Selective -> listOf(
            parameter("brightness", R.string.fossin_brightness, state.selectiveExposure, -2f..2f) { editor, value -> editor.copy(selectiveExposure = value) },
            parameter("contrast", R.string.fossin_contrast, state.selectiveContrast, 0f..2f) { editor, value -> editor.copy(selectiveContrast = value) },
            parameter("saturation", R.string.fossin_saturation, state.selectiveSaturation, 0f..2f) { editor, value -> editor.copy(selectiveSaturation = value) },
        )
        StackTool.Brush -> listOf(
            parameter("brightness", R.string.fossin_brightness, state.brushExposure, -2f..2f) { editor, value -> editor.copy(brushExposure = value) },
            parameter("saturation", R.string.fossin_saturation, state.brushSaturation, 0f..2f) { editor, value -> editor.copy(brushSaturation = value) },
            parameter("warmth", R.string.fossin_warmth, state.brushWarmth, -1f..1f) { editor, value -> editor.copy(brushWarmth = value) },
        )
        StackTool.Healing -> listOf(parameter("strength", R.string.fossin_strength, state.healingStrength, 0f..1f) { editor, value -> editor.copy(healingStrength = value) })
        StackTool.Perspective -> listOf(
            parameter("horizontal", R.string.fossin_horizontal, state.perspectiveHorizontal, -1f..1f) { editor, value -> editor.copy(perspectiveHorizontal = value) },
            parameter("vertical", R.string.fossin_vertical, state.perspectiveVertical, -1f..1f) { editor, value -> editor.copy(perspectiveVertical = value) },
            parameter("rotate", R.string.fossin_rotate, state.perspectiveRotate, -1f..1f) { editor, value -> editor.copy(perspectiveRotate = value) },
        )
        StackTool.Crop -> listOf(
            parameter("straighten", R.string.fossin_straighten, state.rotationFine, -1f..1f) { editor, value -> editor.copy(rotationFine = value) },
        )
        StackTool.Expand -> listOf(parameter("size", R.string.fossin_expand_amount, state.expandAmount, 0f..1f) { editor, value -> editor.copy(expandAmount = value) })
        StackTool.HeadPose -> listOf(
            parameter("horizontal", R.string.fossin_horizontal, state.headPoseHorizontal, -1f..1f) { editor, value -> editor.copy(headPoseHorizontal = value) },
            parameter("vertical", R.string.fossin_vertical, state.headPoseVertical, -1f..1f) { editor, value -> editor.copy(headPoseVertical = value) },
            parameter("tilt", R.string.fossin_tilt, state.headPoseTilt, -1f..1f) { editor, value -> editor.copy(headPoseTilt = value) },
        )
        StackTool.Grain -> listOf(parameter("grain", R.string.recipe_param_film_grain, state.grain, 0f..1f) { editor, value -> editor.copy(grain = value) })
        StackTool.Bloom -> listOf(parameter("bloom", R.string.recipe_param_bloom, state.bloom, 0f..1f) { editor, value -> editor.copy(bloom = value) })
        StackTool.Hdr -> listOf(effect(SnapEffect.HdrScape, "hdr", R.string.fossin_hdr_intensity))
        StackTool.ChromaticAberration -> listOf(parameter("aberration", R.string.fossin_chromatic_aberration, state.chromaticAberration, 0f..1f) { editor, value -> editor.copy(chromaticAberration = value) })
        StackTool.Halation -> listOf(parameter("halation", R.string.fossin_halation, state.halation, 0f..1f) { editor, value -> editor.copy(halation = value) })
        StackTool.Vintage -> listOf(effect(SnapEffect.Vintage, "vintage", R.string.fossin_strength))
        StackTool.BlackWhite -> listOf(effect(SnapEffect.BlackWhite, "bw", R.string.fossin_strength))
        StackTool.Drama -> listOf(effect(SnapEffect.Drama, "drama", R.string.fossin_strength))
        StackTool.Noir -> listOf(effect(SnapEffect.Noir, "noir", R.string.fossin_strength))
        StackTool.Grunge -> listOf(effect(SnapEffect.Grunge, "grunge", R.string.fossin_strength))
        StackTool.DoubleExposure -> listOf(parameter("opacity", R.string.fossin_opacity, state.overlayAlpha, 0f..1f) { editor, value -> editor.copy(overlayAlpha = value) })
        StackTool.Frame -> listOf(parameter("width", R.string.fossin_frame_width, state.frameWidth, 0f..1f) { editor, value -> editor.copy(frameWidth = value) })
        StackTool.Text -> listOf(
            parameter("size", R.string.fossin_text_size, state.textSize, 0.03f..0.2f) { editor, value -> editor.copy(textSize = value) },
            parameter("opacity", R.string.fossin_opacity, state.textOpacity, 0f..1f) { editor, value -> editor.copy(textOpacity = value) },
        )
    }
}

private suspend fun renderStackBitmap(
    context: Context,
    processor: LutImageProcessor,
    source: Bitmap,
    operations: List<StackOperation>,
): Bitmap = withContext(Dispatchers.Default) {
    var current = source
    operations.filter(StackOperation::enabled).forEach { operation ->
        val overlay = operation.state.overlayUri?.let { value ->
            runCatching { loadBitmap(context, Uri.parse(value), 2048) }.getOrNull()
        }
        val effected = renderEditorBitmap(processor, current, operation.state, overlay)
        overlay?.takeIf { it !== effected && !it.isRecycled }?.recycle()
        val next = operation.mask?.takeUnless { operation.tool.isGlobalGeometry }?.let { mask ->
            blendStackMask(current, effected, mask)
        } ?: effected
        if (current !== source && current !== next && !current.isRecycled) current.recycle()
        if (effected !== next && effected !== source && !effected.isRecycled) effected.recycle()
        current = next
    }
    current
}

private fun blendStackMask(input: Bitmap, effect: Bitmap, mask: StackMask): Bitmap {
    if (input.width != effect.width || input.height != effect.height) return effect
    val inPixels = IntArray(input.width * input.height)
    val effectPixels = IntArray(effect.width * effect.height)
    input.getPixels(inPixels, 0, input.width, 0, 0, input.width, input.height)
    effect.getPixels(effectPixels, 0, effect.width, 0, 0, effect.width, effect.height)
    val output = IntArray(inPixels.size)
    val featherRadius = (mask.feather.coerceIn(0f, 0.4f) * minOf(mask.width, mask.height)).roundToInt()
    inPixels.indices.forEach { index ->
        val x = index % input.width
        val y = index / input.width
        var alpha = sampleStackMask(mask, x.toFloat() / input.width, y.toFloat() / input.height)
        if (featherRadius > 0) alpha = smoothStackMask(mask, x.toFloat() / input.width, y.toFloat() / input.height, featherRadius)
        if (mask.inverted) alpha = 1f - alpha
        output[index] = blendStackPixel(inPixels[index], effectPixels[index], alpha)
    }
    return Bitmap.createBitmap(output, input.width, input.height, Bitmap.Config.ARGB_8888)
}

private fun blendStackPixel(base: Int, effect: Int, alpha: Float): Int {
    val amount = alpha.coerceIn(0f, 1f)
    fun component(from: Int, to: Int) = (from + (to - from) * amount).roundToInt().coerceIn(0, 255)
    return android.graphics.Color.argb(
        component(android.graphics.Color.alpha(base), android.graphics.Color.alpha(effect)),
        component(android.graphics.Color.red(base), android.graphics.Color.red(effect)),
        component(android.graphics.Color.green(base), android.graphics.Color.green(effect)),
        component(android.graphics.Color.blue(base), android.graphics.Color.blue(effect)),
    )
}

private fun sampleStackMask(mask: StackMask, nx: Float, ny: Float): Float {
    val x = (nx.coerceIn(0f, 1f) * (mask.width - 1)).roundToInt()
    val y = (ny.coerceIn(0f, 1f) * (mask.height - 1)).roundToInt()
    return (mask.alpha[y * mask.width + x].toInt() and 0xff) / 255f
}

private fun smoothStackMask(mask: StackMask, nx: Float, ny: Float, radius: Int): Float {
    val centerX = (nx.coerceIn(0f, 1f) * (mask.width - 1)).roundToInt()
    val centerY = (ny.coerceIn(0f, 1f) * (mask.height - 1)).roundToInt()
    val step = maxOf(1, radius / 3)
    var total = 0f
    var count = 0
    for (y in (centerY - radius)..(centerY + radius) step step) for (x in (centerX - radius)..(centerX + radius) step step) {
        if (x !in 0 until mask.width || y !in 0 until mask.height) continue
        total += (mask.alpha[y * mask.width + x].toInt() and 0xff) / 255f
        count++
    }
    return if (count == 0) 0f else total / count
}

/** Seeded local segmentation for the explicit manual fallback and for refining model output. */
private fun seededStackMask(bitmap: Bitmap, point: NormalizedPoint): StackMask {
    val scale = minOf(1f, STACK_MASK_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
    val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    val small = if (width == bitmap.width && height == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
    val pixels = IntArray(width * height)
    small.getPixels(pixels, 0, width, 0, 0, width, height)
    if (small !== bitmap) small.recycle()
    val startX = (point.x.coerceIn(0f, 1f) * (width - 1)).roundToInt()
    val startY = (point.y.coerceIn(0f, 1f) * (height - 1)).roundToInt()
    val seed = pixels[startY * width + startX]
    val seedR = android.graphics.Color.red(seed)
    val seedG = android.graphics.Color.green(seed)
    val seedB = android.graphics.Color.blue(seed)
    val alpha = ByteArray(width * height)
    val visited = BooleanArray(alpha.size)
    val queue = IntArray(alpha.size)
    var head = 0
    var tail = 0
    queue[tail++] = startY * width + startX
    visited[startY * width + startX] = true
    val tolerance = 82
    while (head < tail) {
        val index = queue[head++]
        val color = pixels[index]
        val distance = kotlin.math.abs(android.graphics.Color.red(color) - seedR) +
            kotlin.math.abs(android.graphics.Color.green(color) - seedG) +
            kotlin.math.abs(android.graphics.Color.blue(color) - seedB)
        if (distance > tolerance) continue
        alpha[index] = 0xff.toByte()
        val x = index % width
        val y = index / width
        intArrayOf(index - 1, index + 1, index - width, index + width).forEach { neighbor ->
            if (neighbor !in pixels.indices || visited[neighbor]) return@forEach
            val nx = neighbor % width
            val ny = neighbor / width
            if (kotlin.math.abs(nx - x) + kotlin.math.abs(ny - y) != 1) return@forEach
            visited[neighbor] = true
            queue[tail++] = neighbor
        }
    }
    return StackMask(alpha, width, height)
}

@Composable
private fun FossinStackEditor(initialUri: Uri?, onOpenCamera: () -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showingHall by rememberSaveable { mutableStateOf(initialUri == null) }
    var sourceUri by rememberSaveable { mutableStateOf<Uri?>(initialUri) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var operations by remember { mutableStateOf<List<StackOperation>>(emptyList()) }
    var undoStack by remember { mutableStateOf<List<List<StackOperation>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<StackOperation>>>(emptyList()) }
    var activeTool by remember { mutableStateOf<StackTool?>(null) }
    var draft by remember { mutableStateOf<StackOperation?>(null) }
    var editingOperationId by remember { mutableStateOf<String?>(null) }
    var activeParameterKey by remember { mutableStateOf<String?>(null) }
    var showParameters by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showMask by remember { mutableStateOf(false) }
    var maskAddMode by remember { mutableStateOf(true) }
    var maskOverlayVisible by remember { mutableStateOf(true) }
    var pickingNeutralPoint by remember { mutableStateOf(false) }
    var mainTab by remember { mutableStateOf(StackEditorTab.Styles) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(StackExportMode.KeepProject) }
    var isRendering by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var builtIns by remember { mutableStateOf<List<LutChoice>>(emptyList()) }
    val processor = remember { LutImageProcessor(context.applicationContext) }
    val renderMutex = remember { Mutex() }

    fun mutateStack(next: List<StackOperation>) {
        if (next == operations) return
        undoStack = (undoStack + listOf(operations)).takeLast(48)
        operations = next
        redoStack = emptyList()
    }
    fun undo() {
        val prior = undoStack.lastOrNull() ?: return
        redoStack = redoStack + listOf(operations)
        operations = prior
        undoStack = undoStack.dropLast(1)
    }
    fun redo() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = undoStack + listOf(operations)
        operations = next
        redoStack = redoStack.dropLast(1)
    }
    fun beginTool(tool: StackTool, existing: StackOperation? = null, initialState: EditorState = EditorState()) {
        activeTool = tool
        draft = existing ?: StackOperation(tool = tool, state = initialState)
        editingOperationId = existing?.id
        activeParameterKey = stackParameters(tool, existing?.state ?: EditorState()).firstOrNull()?.key
        showParameters = false
        mainTab = StackEditorTab.Tools
    }
    fun updateDraft(transform: (EditorState) -> EditorState) {
        draft = draft?.let { it.copy(state = transform(it.state)) }
    }
    fun commitDraft() {
        val candidate = draft ?: return
        val currentId = editingOperationId
        mutateStack(
            if (currentId == null) operations + candidate
            else operations.map { if (it.id == currentId) candidate else it },
        )
        activeTool = null
        draft = null
        editingOperationId = null
        showMask = false
    }
    fun discardDraft() {
        activeTool = null
        draft = null
        editingOperationId = null
        showMask = false
    }
    fun leaveEditor() {
        if (draft != null) {
            showExitDialog = true
            return
        }
        if (initialUri != null) onFinish() else {
            source = null
            rendered = null
            sourceUri = null
            projectId = null
            operations = emptyList()
            undoStack = emptyList()
            redoStack = emptyList()
            showingHall = true
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            sourceUri = it
            projectId = StackProjectStore.newId()
            operations = emptyList()
            undoStack = emptyList()
            redoStack = emptyList()
            source = null
            showingHall = false
        }
    }
    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected ->
            persistReadPermission(context, selected)
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        openUriStream(context, selected)?.use { LutParser.parse(it, lutDisplayName(context, selected)) }
                            ?: throw FileNotFoundException()
                    }
                }.onSuccess { config ->
                    updateDraft { state ->
                        val name = lutDisplayName(context, selected)
                        state.copy(lut = LutChoice(name, config), lutName = name, lutUri = selected.toString())
                    }
                }.onFailure {
                    Toast.makeText(context, R.string.fossin_lut_import_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val overlayPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected ->
            persistReadPermission(context, selected)
            updateDraft { state ->
                state.copy(overlayUri = selected.toString(), overlayAlpha = maxOf(0.45f, state.overlayAlpha))
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { target ->
        target?.let { uri ->
            val exportedSource = source ?: return@let
            val exportedOperations = operations
            val exportProjectId = projectId
            val exportSourceUri = sourceUri
            val mode = exportMode
            scope.launch {
                isExporting = true
                if (exportProjectId != null && exportSourceUri != null) {
                    StackProjectStore.save(context, exportProjectId, exportSourceUri, exportedOperations)
                }
                val fullSource = exportSourceUri?.let { runCatching { loadBitmap(context, it, maxEdge = 4096) }.getOrNull() } ?: exportedSource
                val output = runCatching {
                    renderMutex.withLock { renderStackBitmap(context, processor, fullSource, exportedOperations) }
                }.getOrNull()
                val saved = output != null && withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output.compress(Bitmap.CompressFormat.JPEG, 96, it) } == true
                }
                if (output != null && output !== exportedSource && output !== fullSource && !output.isRecycled) output.recycle()
                if (fullSource !== exportedSource && !fullSource.isRecycled) fullSource.recycle()
                isExporting = false
                Toast.makeText(context, if (saved) R.string.fossin_export_done else R.string.fossin_export_failed, Toast.LENGTH_LONG).show()
                if (saved && mode == StackExportMode.PhotoOnly && exportProjectId != null) {
                    StackProjectStore.delete(context, exportProjectId)
                    source = null
                    rendered = null
                    sourceUri = null
                    projectId = null
                    operations = emptyList()
                    undoStack = emptyList()
                    redoStack = emptyList()
                    showingHall = true
                }
            }
        }
    }
    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { target ->
        target?.let { uri ->
            val id = projectId ?: return@let
            val uriToSave = sourceUri ?: return@let
            val operationsToSave = operations
            scope.launch {
                isExporting = true
                val saved = StackProjectStore.save(context, id, uriToSave, operationsToSave)
                val archived = saved && StackProjectStore.exportArchive(context, id, uri)
                isExporting = false
                Toast.makeText(context, if (archived) R.string.fossin_export_package_done else R.string.fossin_export_package_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    fun shareEditedImage() {
        val previewSource = source ?: return
        val sharedOperations = operations
        val uriToShare = sourceUri
        scope.launch {
            isExporting = true
            var fullSource: Bitmap? = null
            var output: Bitmap? = null
            try {
                fullSource = uriToShare?.let { loadBitmap(context, it, maxEdge = 4096) } ?: previewSource
                output = renderMutex.withLock { renderStackBitmap(context, processor, fullSource!!, sharedOperations) }
                val shareDirectory = File(context.cacheDir, "shared").apply { mkdirs() }
                val shareFile = File(shareDirectory, "photo-editor-${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    FileOutputStream(shareFile).use { stream ->
                        check(output!!.compress(Bitmap.CompressFormat.JPEG, 96, stream))
                    }
                }
                val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, null))
            } catch (_: Throwable) {
                Toast.makeText(context, R.string.fossin_share_failed, Toast.LENGTH_LONG).show()
            } finally {
                output?.takeIf { it !== previewSource && it !== fullSource && !it.isRecycled }?.recycle()
                fullSource?.takeIf { it !== previewSource && !it.isRecycled }?.recycle()
                isExporting = false
            }
        }
    }

    DisposableEffect(processor) { onDispose { processor.release() } }
    LaunchedEffect(Unit) {
        builtIns = withContext(Dispatchers.IO) {
            LutParser.listAvailableLuts(context).mapNotNull { info ->
                runCatching { LutChoice(info.getName(), LutParser.parseFromAssets(context, info.fileName)) }.getOrNull()
            }
        }
    }
    LaunchedEffect(sourceUri) {
        val uri = sourceUri ?: return@LaunchedEffect
        if (source != null) return@LaunchedEffect
        val bitmap = loadBitmap(context, uri, maxEdge = 1440)
        if (bitmap != null) {
            source = bitmap
            if (projectId == null) projectId = StackProjectStore.newId()
        }
    }
    LaunchedEffect(builtIns, operations) {
        if (builtIns.isEmpty()) return@LaunchedEffect
        val restored = operations.map { operation ->
            val name = operation.state.lutName
            if (operation.state.lut == null && operation.state.lutUri == null && name != null) {
                builtIns.firstOrNull { it.name == name }?.let { operation.copy(state = operation.state.copy(lut = it)) } ?: operation
            } else operation
        }
        if (restored != operations) operations = restored
    }
    LaunchedEffect(operations) {
        val unresolved = operations.filter { operation ->
            operation.state.lut == null && operation.state.lutUri != null
        }
        if (unresolved.isEmpty()) return@LaunchedEffect
        val resolved = unresolved.associate { operation ->
            operation.id to runCatching {
                val uri = Uri.parse(operation.state.lutUri)
                val name = operation.state.lutName ?: lutDisplayName(context, uri)
                val config = withContext(Dispatchers.IO) {
                    openUriStream(context, uri)?.use { LutParser.parse(it, name) } ?: throw FileNotFoundException(uri.toString())
                }
                LutChoice(name, config)
            }.getOrNull()
        }
        if (resolved.values.any { it != null }) {
            operations = operations.map { operation ->
                resolved[operation.id]?.let { choice ->
                    operation.copy(state = operation.state.copy(lut = choice, lutName = choice.name))
                } ?: operation
            }
        }
    }
    LaunchedEffect(projectId, sourceUri, source, operations) {
        val id = projectId ?: return@LaunchedEffect
        val uri = sourceUri ?: return@LaunchedEffect
        if (source == null) return@LaunchedEffect
        delay(350)
        StackProjectStore.save(context, id, uri, operations)
    }

    val visibleOperations = draft?.let { pending ->
        editingOperationId?.let { id -> operations.map { if (it.id == id) pending else it } } ?: operations + pending
    } ?: operations
    LaunchedEffect(source, visibleOperations) {
        val input = source ?: return@LaunchedEffect
        isRendering = true
        try {
            val next = renderMutex.withLock { renderStackBitmap(context, processor, input, visibleOperations) }
            val old = rendered
            rendered = next
            if (old != null && old !== input && old !== next && !old.isRecycled) old.recycle()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            rendered = input
        } finally {
            isRendering = false
        }
    }

    if (showingHall) {
        BackHandler { onFinish() }
        StackEditorHall(
            onImport = { imagePicker.launch(arrayOf("image/*")) },
            onOpenCamera = onOpenCamera,
            onOpenProject = { summary ->
                scope.launch {
                    val project = StackProjectStore.load(context, summary.id) ?: return@launch
                    source = null
                    rendered = null
                    sourceUri = project.uri
                    projectId = project.id
                    operations = project.operations
                    undoStack = emptyList()
                    redoStack = emptyList()
                    showingHall = false
                }
            },
        )
        return
    }

    BackHandler { leaveEditor() }
    val image = if (showOriginal) source else rendered ?: source
    val active = activeTool
    val parameters = draft?.let { operation -> stackParameters(operation.tool, operation.state) }.orEmpty()
    val activeParameter = parameters.firstOrNull { it.key == activeParameterKey } ?: parameters.firstOrNull()
    val latestImage by rememberUpdatedState(image)
    val latestSource by rememberUpdatedState(source)
    val latestDraft by rememberUpdatedState(draft)
    val latestParameters by rememberUpdatedState(parameters)
    val latestMaskAddMode by rememberUpdatedState(maskAddMode)
    val latestUpdateDraft by rememberUpdatedState(newValue = { transform: (EditorState) -> EditorState -> updateDraft(transform) })

    Surface(Modifier.fillMaxSize(), color = Color(0xFF070708)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                StackTopBar(
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    onBack = ::leaveEditor,
                    onUndo = ::undo,
                    onRedo = ::redo,
                    onHoldOriginal = { showOriginal = it },
                    onLayers = { showLayers = true },
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (image == null) {
                        EmptyEditor({ imagePicker.launch(arrayOf("image/*")) }, onOpenCamera)
                    } else {
                        val imageModifier = Modifier
                            .fillMaxSize()
                            .pointerInput(active, pickingNeutralPoint) {
                                if (active != StackTool.WhiteBalance || !pickingNeutralPoint) return@pointerInput
                                detectTapGestures(onTap = { offset ->
                                    val bitmap = latestSource ?: return@detectTapGestures
                                    val rect = displayedImageRect(size.width.toFloat(), size.height.toFloat(), latestImage ?: bitmap)
                                    val x = (((offset.x - rect.left) / rect.width) * (bitmap.width - 1)).roundToInt().coerceIn(0, bitmap.width - 1)
                                    val y = (((offset.y - rect.top) / rect.height) * (bitmap.height - 1)).roundToInt().coerceIn(0, bitmap.height - 1)
                                    val color = bitmap.getPixel(x, y)
                                    val red = android.graphics.Color.red(color) / 255f
                                    val green = android.graphics.Color.green(color) / 255f
                                    val blue = android.graphics.Color.blue(color) / 255f
                                    latestUpdateDraft { state ->
                                        state.copy(
                                            warmth = (state.warmth + (blue - red) * 0.8f).coerceIn(-1f, 1f),
                                            tint = (state.tint + ((red + blue) * 0.5f - green) * 0.9f).coerceIn(-1f, 1f),
                                        )
                                    }
                                    pickingNeutralPoint = false
                                })
                            }
                            .pointerInput(active, showMask) {
                                if (active != StackTool.LensBlur || showMask) return@pointerInput
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var previousCentroid: Offset? = null
                                    var previousDistance = 0f
                                    var previousAngle = 0f
                                    var pointersPressed = true
                                    while (pointersPressed) {
                                        val event = awaitPointerEvent()
                                        val fingers = event.changes.filter { it.pressed }
                                        if (fingers.size >= 2) {
                                            val first = fingers[0].position
                                            val second = fingers[1].position
                                            val centroid = Offset((first.x + second.x) * 0.5f, (first.y + second.y) * 0.5f)
                                            val distance = (first - second).getDistance().coerceAtLeast(1f)
                                            val angle = kotlin.math.atan2(second.y - first.y, second.x - first.x)
                                            val bitmap = latestImage
                                            val previous = previousCentroid
                                            if (bitmap != null && previous != null) {
                                                val rect = displayedImageRect(size.width.toFloat(), size.height.toFloat(), bitmap)
                                                val scale = distance / previousDistance.coerceAtLeast(1f)
                                                val rotation = (angle - previousAngle) / Math.PI.toFloat()
                                                latestUpdateDraft { state ->
                                                    state.copy(
                                                        lensBlurX = (state.lensBlurX + (centroid.x - previous.x) / rect.width).coerceIn(0f, 1f),
                                                        lensBlurY = (state.lensBlurY + (centroid.y - previous.y) / rect.height).coerceIn(0f, 1f),
                                                        lensBlurRadius = (state.lensBlurRadius * scale).coerceIn(0.05f, 1f),
                                                        lensBlurAngle = (state.lensBlurAngle + rotation).coerceIn(-1f, 1f),
                                                    )
                                                }
                                            }
                                            previousCentroid = centroid
                                            previousDistance = distance
                                            previousAngle = angle
                                            fingers.forEach { it.consume() }
                                        }
                                        pointersPressed = event.changes.any { it.pressed }
                                    }
                                }
                            }
                            .pointerInput(active, showMask) {
                                var phase = SnapseedGesturePhase.Pending
                                var totalHorizontal = 0f
                                var totalVertical = 0f
                                var horizontalSinceSelection = 0f
                                var adjustmentHorizontal = 0f
                                var horizontalStartValue = 0f
                                var horizontalParameter: StackGestureParameter? = null
                                var verticalStartIndex = 0
                                var mask: StackMask? = null
                                var lastMaskPoint: Offset? = null
                                var cropStart: NormalizedPoint? = null
                                var draggingGuide = false

                                fun normalized(offset: Offset, bitmap: Bitmap): NormalizedPoint {
                                    val rect = displayedImageRect(size.width.toFloat(), size.height.toFloat(), bitmap)
                                    return NormalizedPoint(
                                        ((offset.x - rect.left) / rect.width).coerceIn(0f, 1f),
                                        ((offset.y - rect.top) / rect.height).coerceIn(0f, 1f),
                                    )
                                }

                                fun addMaskSeed(offset: Offset) {
                                    val sourceBitmap = latestSource ?: return
                                    val pending = latestDraft ?: return
                                    val point = normalized(offset, latestImage ?: sourceBitmap)
                                    val currentMask = mask ?: pending.mask
                                    mask = if (currentMask == null) {
                                        seededStackMask(sourceBitmap, point)
                                    } else {
                                        paintStackMask(currentMask, point, latestMaskAddMode)
                                    }
                                    draft = pending.copy(mask = mask)
                                }

                                detectDragGestures(
                                    onDragStart = { offset ->
                                        phase = SnapseedGesturePhase.Pending
                                        totalHorizontal = 0f
                                        totalVertical = 0f
                                        horizontalSinceSelection = 0f
                                        adjustmentHorizontal = 0f
                                        horizontalParameter = null
                                        lastMaskPoint = null
                                        cropStart = null
                                        draggingGuide = false
                                        val pending = latestDraft
                                        val bitmap = latestImage
                                        if (showMask && pending != null && latestSource != null && bitmap != null) {
                                            mask = pending.mask
                                            addMaskSeed(offset)
                                            lastMaskPoint = offset
                                            return@detectDragGestures
                                        }
                                        if (active == StackTool.Crop && pending != null && bitmap != null) {
                                            cropStart = normalized(offset, bitmap)
                                            return@detectDragGestures
                                        }
                                        if ((active == StackTool.LensBlur || active == StackTool.Vignette) && pending != null && bitmap != null) {
                                            val rect = displayedImageRect(size.width.toFloat(), size.height.toFloat(), bitmap)
                                            val center = if (active == StackTool.LensBlur) {
                                                Offset(rect.left + pending.state.lensBlurX * rect.width, rect.top + pending.state.lensBlurY * rect.height)
                                            } else {
                                                Offset(rect.left + pending.state.vignetteX * rect.width, rect.top + pending.state.vignetteY * rect.height)
                                            }
                                            draggingGuide = (offset - center).getDistance() < 44.dp.toPx()
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        val bitmap = latestImage
                                        if (showMask && bitmap != null && latestSource != null) {
                                            val previous = lastMaskPoint
                                            if (previous == null || (change.position - previous).getDistance() >= 18.dp.toPx()) {
                                                addMaskSeed(change.position)
                                                lastMaskPoint = change.position
                                            }
                                            change.consume()
                                            return@detectDragGestures
                                        }
                                        if (active == StackTool.Crop && bitmap != null) {
                                            cropStart?.let { start ->
                                                val point = normalized(change.position, bitmap)
                                                latestUpdateDraft { state ->
                                                    state.copy(
                                                        cropMode = CropMode.Free,
                                                        cropLeft = minOf(start.x, point.x),
                                                        cropTop = minOf(start.y, point.y),
                                                        cropRight = maxOf(start.x, point.x),
                                                        cropBottom = maxOf(start.y, point.y),
                                                    )
                                                }
                                            }
                                            change.consume()
                                            return@detectDragGestures
                                        }
                                        if (draggingGuide && bitmap != null) {
                                            val point = normalized(change.position, bitmap)
                                            latestUpdateDraft { state ->
                                                if (active == StackTool.LensBlur) state.copy(lensBlurX = point.x, lensBlurY = point.y)
                                                else state.copy(vignetteX = point.x, vignetteY = point.y)
                                            }
                                            change.consume()
                                            return@detectDragGestures
                                        }
                                        val current = latestParameters
                                        if (active == null || current.isEmpty()) return@detectDragGestures
                                        totalHorizontal += dragAmount.x
                                        totalVertical += dragAmount.y
                                        if (phase == SnapseedGesturePhase.Pending) {
                                            phase = snapseedInitialGesturePhase(
                                                totalHorizontal,
                                                totalVertical,
                                                GESTURE_TOUCH_SLOP_DP.dp.toPx(),
                                            )
                                            if (phase == SnapseedGesturePhase.Selecting) {
                                                verticalStartIndex = current.indexOfFirst { it.key == activeParameterKey }.takeIf { it >= 0 } ?: 0
                                                activeParameterKey = current[verticalStartIndex].key
                                                showParameters = true
                                            } else if (phase == SnapseedGesturePhase.Adjusting) {
                                                horizontalParameter = current.firstOrNull { it.key == activeParameterKey } ?: current.first()
                                                horizontalStartValue = horizontalParameter?.value ?: 0f
                                                adjustmentHorizontal = totalHorizontal - dragAmount.x
                                            }
                                        }
                                        if (phase == SnapseedGesturePhase.Selecting) {
                                            val index = snapseedParameterIndexForDrag(
                                                verticalStartIndex,
                                                current.size,
                                                totalVertical,
                                                GESTURE_PARAMETER_STEP_DP.dp.toPx(),
                                            )
                                            activeParameterKey = current[index].key
                                            horizontalSinceSelection += dragAmount.x
                                            if (snapseedShouldBeginHorizontalAdjustment(
                                                    horizontalSinceSelection,
                                                    dragAmount.x,
                                                    dragAmount.y,
                                                    GESTURE_DIRECTION_TURN_SLOP_DP.dp.toPx(),
                                                )
                                            ) {
                                                horizontalParameter = current.firstOrNull { it.key == activeParameterKey } ?: current.first()
                                                horizontalStartValue = horizontalParameter?.value ?: 0f
                                                adjustmentHorizontal = 0f
                                                phase = SnapseedGesturePhase.Adjusting
                                            }
                                        }
                                        if (phase == SnapseedGesturePhase.Adjusting) {
                                            adjustmentHorizontal += dragAmount.x
                                            horizontalParameter?.let { parameter ->
                                                val value = snapseedAdjustedValue(
                                                    horizontalStartValue,
                                                    parameter.range,
                                                    adjustmentHorizontal,
                                                    GESTURE_FULL_RANGE_DP.dp.toPx(),
                                                )
                                                latestUpdateDraft { state -> parameter.update(state, value) }
                                            }
                                        }
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        showParameters = false
                                        cropStart = null
                                        draggingGuide = false
                                    },
                                    onDragCancel = {
                                        showParameters = false
                                        cropStart = null
                                        draggingGuide = false
                                    },
                                )
                            }
                        Image(image.asImageBitmap(), stringResource(R.string.fossin_preview), imageModifier, contentScale = ContentScale.Fit)
                        if (showMask && draft?.mask != null && maskOverlayVisible) {
                            StackMaskOverlay(draft!!.mask!!, image)
                        }
                        if (showParameters && active != null) {
                            StackParameterMenu(parameters, activeParameterKey) { activeParameterKey = it.key }
                        }
                        if (active == StackTool.LensBlur && draft != null) {
                            StackLensGuide(draft!!.state, image)
                        }
                        if (active == StackTool.Vignette && draft != null) {
                            StackVignetteGuide(draft!!.state, image)
                        }
                        if (active == StackTool.Crop && draft != null) {
                            StackCropGuide(draft!!.state, image)
                        }
                    }
                    if (isRendering || isExporting) LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.6f))
                }
                if (active != null && draft != null) {
                    StackEditControls(
                        tool = active,
                        parameter = activeParameter,
                        state = draft!!.state,
                        onCancel = ::discardDraft,
                        onConfirm = ::commitDraft,
                        onAuto = {
                            source?.let { bitmap -> updateDraft { stackAutoTune(bitmap, it) } }
                        },
                        onMask = { showMask = !showMask },
                        onLayers = { showLayers = true },
                    )
                    if (showMask) {
                        StackMaskControls(
                            advanced = draft!!.mask != null,
                            addMode = maskAddMode,
                            overlayVisible = maskOverlayVisible,
                            feather = draft!!.mask?.feather ?: 0.08f,
                            onAdd = { maskAddMode = true },
                            onSubtract = { maskAddMode = false },
                            onInvert = { draft = draft?.let { it.copy(mask = it.mask?.copy(inverted = !it.mask.inverted)) } },
                            onOverlay = { maskOverlayVisible = !maskOverlayVisible },
                            onFeather = { value -> draft = draft?.let { it.copy(mask = it.mask?.copy(feather = value)) } },
                        )
                    } else {
                        StackToolContext(
                            tool = active,
                            operation = draft!!,
                            builtIns = builtIns,
                            previewBitmap = source,
                            onState = ::updateDraft,
                            onImportLut = { lutPicker.launch(arrayOf("application/*", "text/plain", "*/*")) },
                            onImportOverlay = { overlayPicker.launch(arrayOf("image/*")) },
                            onPickNeutral = { pickingNeutralPoint = true },
                        )
                    }
                } else {
                    when (mainTab) {
                        StackEditorTab.Styles -> StackStylesPanel(
                            builtIns = builtIns,
                            onLooks = { beginTool(StackTool.Looks) },
                            onStyle = { style -> beginTool(StackTool.Looks, initialState = EditorState(style = style)) },
                            onLut = { lut -> beginTool(StackTool.Looks, initialState = EditorState(lut = lut, lutName = lut.name)) },
                            onApplyDesign = { design -> mutateStack(operations + design.map { it.copy(id = UUID.randomUUID().toString(), mask = null) }) },
                            context = context,
                        )
                        StackEditorTab.Tools -> StackToolGrid { beginTool(it) }
                        StackEditorTab.Export -> StackExportPanel(
                            onExport = { showExportDialog = true },
                            onShare = ::shareEditedImage,
                            onEditablePackage = { archivePicker.launch("photo-editor-editable.zip") },
                        )
                    }
                    StackMainNavigation(mainTab) { mainTab = it }
                }
            }
            if (showLayers) {
                StackLayersSheet(
                    operations = operations,
                    onDismiss = { showLayers = false },
                    onEdit = { operation -> showLayers = false; beginTool(operation.tool, operation) },
                    onMask = { operation -> showLayers = false; beginTool(operation.tool, operation); showMask = true },
                    onToggle = { id -> mutateStack(operations.map { operation -> if (operation.id == id) operation.copy(enabled = !operation.enabled) else operation }) },
                    onDelete = { id -> mutateStack(operations.filterNot { it.id == id }) },
                    onSaveDesign = {
                        StackDesignStore.save(context, operations)
                        Toast.makeText(context, R.string.fossin_design_saved, Toast.LENGTH_SHORT).show()
                    },
                )
            }
            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text(stringResource(R.string.fossin_pending_title)) },
                    text = { Text(stringResource(R.string.fossin_pending_message)) },
                    confirmButton = { TextButton(onClick = { showExitDialog = false; commitDraft(); leaveEditor() }) { Text(stringResource(R.string.fossin_apply_all)) } },
                    dismissButton = { TextButton(onClick = { showExitDialog = false; discardDraft(); leaveEditor() }) { Text(stringResource(R.string.fossin_discard_exit)) } },
                )
            }
            if (showExportDialog) {
                FossinExportDialog(
                    onKeepProject = {
                        exportMode = StackExportMode.KeepProject
                        showExportDialog = false
                        exportPicker.launch("photo-editor-edit.jpg")
                    },
                    onPhotoOnly = {
                        exportMode = StackExportMode.PhotoOnly
                        showExportDialog = false
                        exportPicker.launch("photo-editor-export.jpg")
                    },
                    onExportPackage = {
                        showExportDialog = false
                        archivePicker.launch("photo-editor-editable.zip")
                    },
                    onDismiss = { showExportDialog = false },
                )
            }
        }
    }
}

@Composable
private fun StackTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHoldOriginal: (Boolean) -> Unit,
    onLayers: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xA8070708)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onUndo, enabled = canUndo) { Icon(AppIcons.AutoMirroredOutlinedUndo, stringResource(R.string.fossin_undo), tint = if (canUndo) Color.White else Color(0xFF4A4A4E)) }
        IconButton(onClick = onRedo, enabled = canRedo) { Icon(AppIcons.AutoMirroredOutlinedUndo, stringResource(R.string.fossin_redo), tint = if (canRedo) Color.White else Color(0xFF4A4A4E), modifier = Modifier.rotate(180f)) }
        Box(
            Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        onHoldOriginal(true)
                        tryAwaitRelease()
                        onHoldOriginal(false)
                    })
                },
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Visibility, stringResource(R.string.fossin_compare), tint = Color.White) }
        IconButton(onClick = onLayers) { Icon(AppIcons.PhotoLibrary, stringResource(R.string.fossin_layers), tint = Color.White) }
    }
}

@Composable
private fun StackMainNavigation(selected: StackEditorTab, onSelected: (StackEditorTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(Color(0xF20B0B0D))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StackMainTab(StackEditorTab.Styles, R.string.fossin_styles, selected, onSelected)
        StackMainTab(StackEditorTab.Tools, R.string.fossin_tools, selected, onSelected)
        StackMainTab(StackEditorTab.Export, R.string.fossin_export, selected, onSelected)
    }
}

@Composable
private fun StackMainTab(tab: StackEditorTab, @StringRes label: Int, selected: StackEditorTab, onSelected: (StackEditorTab) -> Unit) {
    TextButton(onClick = { onSelected(tab) }) {
        Text(stringResource(label), color = if (tab == selected) Color(0xFFFFC400) else Color(0xFFE2E2E6), fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StackToolGrid(onTool: (StackTool) -> Unit) {
    var category by remember { mutableStateOf(StackToolCategory.All) }
    val visible = StackTool.values().filter { category == StackToolCategory.All || it.category == category }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 310.dp)
            .background(Color(0xFF0B0B0D))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(StackToolCategory.values().toList()) { item ->
                FilterChip(selected = item == category, onClick = { category = item }, label = { Text(stringResource(item.labelRes), fontSize = 12.sp) })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().height(238.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(visible, key = { it.name }) { tool ->
                Surface(
                    modifier = Modifier
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onTool(tool) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1A1A1E),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            stringResource(tool.labelRes),
                            color = Color.White,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 4.dp),
                        )
                        if (tool.isNew) Text(stringResource(R.string.fossin_new), color = Color(0xFFFFC400), fontSize = 7.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StackStylesPanel(
    builtIns: List<LutChoice>,
    onLooks: () -> Unit,
    onStyle: (StylePreset) -> Unit,
    onLut: (LutChoice) -> Unit,
    onApplyDesign: (List<StackOperation>) -> Unit,
    context: Context,
) {
    val designs by remember(context) { mutableStateOf(StackDesignStore.list(context)) }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .background(Color(0xFF0B0B0D))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fossin_styles), color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onLooks) { Text(stringResource(R.string.fossin_luts), color = Color(0xFFFFC400)) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { StackStyleTile(stringResource(R.string.fossin_original), onLooks) }
            items(StylePreset.values().filter { it != StylePreset.None }) { style ->
                StackStyleTile(stringResource(style.labelRes)) { onStyle(style) }
            }
            items(builtIns.take(12)) { look -> StackStyleTile(look.name) { onLut(look) } }
            items(designs) { design -> StackStyleTile(design.name) { onApplyDesign(design.operations) } }
        }
    }
}

@Composable
private fun StackStyleTile(name: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(82.dp).height(72.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        color = Color(0xFF242429),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(name, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun StackExportPanel(onExport: () -> Unit, onShare: () -> Unit, onEditablePackage: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF0B0B0D)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onExport, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006E51)), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fossin_export)) }
            TextButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.share), color = Color.White) }
        }
        TextButton(onClick = onEditablePackage, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.fossin_export_editable_package), color = Color(0xFFFFC400), fontSize = 12.sp)
        }
    }
}

@Composable
private fun StackParameterMenu(
    parameters: List<StackGestureParameter>,
    selected: String?,
    onSelected: (StackGestureParameter) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.92f),
        color = Color(0xD9000000),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            parameters.forEach { parameter ->
                val isSelected = parameter.key == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) Color(0xFFFFC400) else Color.Transparent)
                        .clickable { onSelected(parameter) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text(stringResource(parameter.labelRes), color = if (isSelected) Color.Black else Color.White, modifier = Modifier.weight(1f))
                    Text(stackValueText(parameter.value, parameter.range), color = if (isSelected) Color.Black else Color.White)
                }
            }
        }
    }
}

private fun stackValueText(value: Float, range: ClosedFloatingPointRange<Float>): String {
    val normalized = ((value - range.start) / (range.endInclusive - range.start).coerceAtLeast(0.001f) * 200f - 100f).roundToInt()
    return if (normalized > 0) "+$normalized" else normalized.toString()
}

@Composable
private fun StackEditControls(
    tool: StackTool,
    parameter: StackGestureParameter?,
    state: EditorState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onAuto: () -> Unit,
    onMask: () -> Unit,
    onLayers: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        parameter?.let {
            Text(
                "${stringResource(it.labelRes)} ${stackValueText(it.value, it.range)}",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
            )
            StackTickRuler(it.value, it.range)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text("×", color = Color.White, fontSize = 28.sp) }
            if (tool == StackTool.Tune || tool == StackTool.WhiteBalance || tool == StackTool.Hdr) TextButton(onClick = onAuto) { Text(stringResource(R.string.fossin_auto), color = Color.White) }
            if (!tool.isGlobalGeometry) TextButton(onClick = onMask) { Text(stringResource(R.string.fossin_mask), color = Color.White) }
            TextButton(onClick = onLayers) { Text(stringResource(R.string.fossin_layers), color = Color.White) }
            TextButton(onClick = onConfirm) { Text("✓", color = Color(0xFFFFC400), fontSize = 25.sp) }
        }
    }
}

@Composable
private fun StackTickRuler(value: Float, range: ClosedFloatingPointRange<Float>) {
    val fraction = ((value - range.start) / (range.endInclusive - range.start).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    ComposeCanvas(Modifier.fillMaxWidth().height(22.dp)) {
        val selected = (fraction * 100).roundToInt()
        repeat(101) { index ->
            val x = size.width * index / 100f
            val highlighted = kotlin.math.abs(index - selected) <= 8
            drawLine(if (highlighted) Color(0xFFFFC400) else Color(0xFFE6E6E8), Offset(x, if (index % 10 == 0) 2f else 7f), Offset(x, 21f), strokeWidth = if (index % 10 == 0) 2.5f else 1.3f)
        }
        drawCircle(Color(0xFFFFC400), 4f, Offset(size.width * fraction, 3f))
    }
}

@Composable
private fun StackToolContext(
    tool: StackTool,
    operation: StackOperation,
    builtIns: List<LutChoice>,
    previewBitmap: Bitmap?,
    onState: ((EditorState) -> EditorState) -> Unit,
    onImportLut: () -> Unit,
    onImportOverlay: () -> Unit,
    onPickNeutral: () -> Unit,
) {
    when (tool) {
        StackTool.Looks -> {
            Column(Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(StylePreset.values().toList()) { style ->
                        FilterChip(
                            selected = operation.state.style == style,
                            onClick = { onState { it.copy(style = style) } },
                            label = { Text(stringResource(style.labelRes), fontSize = 11.sp) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fossin_luts), color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = onImportLut) { Text(stringResource(R.string.fossin_import_lut), color = Color(0xFFFFC400)) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = operation.state.lut == null, onClick = { onState { it.copy(lut = null, lutName = null, lutUri = null) } }, label = { Text(stringResource(R.string.fossin_original), fontSize = 11.sp) })
                    }
                    items(builtIns) { choice ->
                        FilterChip(
                            selected = operation.state.lutName == choice.name,
                            onClick = { onState { it.copy(lut = choice, lutName = choice.name, lutUri = null) } },
                            label = { Text(choice.name, fontSize = 11.sp) },
                        )
                    }
                }
            }
        }
        StackTool.ColorGrading -> StackColorGradingWheels(operation.state, onState)
        StackTool.Curves -> StackCurveEditor(operation.state, previewBitmap, onState)
        StackTool.WhiteBalance -> StackWhiteBalanceContext(onPickNeutral)
        StackTool.Crop -> StackCropModeRow(operation.state, onState)
        StackTool.LensBlur -> StackLensShapeRow(operation.state, onState)
        StackTool.Hdr -> StackHdrPresets(operation.state, onState)
        StackTool.DoubleExposure -> StackDoubleExposureContext(operation.state, onState, onImportOverlay)
        StackTool.Text -> StackTextEditor(operation.state, onState)
        StackTool.Frame -> StackFrameChooser(operation.state, onState)
        else -> Unit
    }
}

@Composable
private fun StackColorGradingWheels(state: EditorState, onState: ((EditorState) -> EditorState) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.fossin_color_grading), color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StackColorWheel(R.string.fossin_grading_shadows, state.gradingShadowHue, state.gradingShadowAmount) { hue, amount ->
                onState { it.copy(gradingShadowHue = hue, gradingShadowAmount = amount) }
            }
            StackColorWheel(R.string.fossin_grading_midtones, state.gradingMidtoneHue, state.gradingMidtoneAmount) { hue, amount ->
                onState { it.copy(gradingMidtoneHue = hue, gradingMidtoneAmount = amount) }
            }
            StackColorWheel(R.string.fossin_grading_highlights, state.gradingHighlightHue, state.gradingHighlightAmount) { hue, amount ->
                onState { it.copy(gradingHighlightHue = hue, gradingHighlightAmount = amount) }
            }
        }
    }
}

@Composable
private fun StackColorWheel(@StringRes label: Int, hue: Float, amount: Float, onChanged: (Float, Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ComposeCanvas(
            Modifier
                .size(76.dp)
                .pointerInput(hue, amount) {
                    detectDragGestures(
                        onDragStart = { point ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = point.x - cx
                            val dy = point.y - cy
                            val newHue = ((kotlin.math.atan2(dy, dx) / (2.0 * Math.PI) + 1.0) % 1.0).toFloat()
                            val newAmount = (kotlin.math.sqrt((dx * dx + dy * dy).toDouble()) / (minOf(size.width, size.height) / 2f)).toFloat().coerceIn(0f, 1f)
                            onChanged(newHue, newAmount)
                        },
                        onDrag = { change, _ ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = change.position.x - cx
                            val dy = change.position.y - cy
                            val newHue = ((kotlin.math.atan2(dy, dx) / (2.0 * Math.PI) + 1.0) % 1.0).toFloat()
                            val newAmount = (kotlin.math.sqrt((dx * dx + dy * dy).toDouble()) / (minOf(size.width, size.height) / 2f)).toFloat().coerceIn(0f, 1f)
                            onChanged(newHue, newAmount)
                            change.consume()
                        },
                    )
                },
        ) {
            val radius = minOf(size.width, size.height) / 2f
            repeat(48) { index ->
                val start = index / 48f * 360f
                val color = Color.hsv(start, 0.85f, 0.95f)
                drawArc(color, start, 360f / 48f + 1f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius))
            }
            drawCircle(Color(0xFF151518), radius * 0.34f)
            val angle = hue * (2.0 * Math.PI)
            drawCircle(Color.White, 5f, Offset(radius + kotlin.math.cos(angle).toFloat() * radius * amount, radius + kotlin.math.sin(angle).toFloat() * radius * amount))
        }
        Text(stringResource(label), color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun StackCurveEditor(state: EditorState, bitmap: Bitmap?, onState: ((EditorState) -> EditorState) -> Unit) {
    var channel by remember { mutableStateOf(CurveChannel.Master) }
    val histogram = remember(bitmap) { bitmap?.let(::stackHistogram) ?: IntArray(48) }
    Column(Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items(CurveChannel.values().toList()) { item ->
                FilterChip(selected = item == channel, onClick = { channel = item }, label = { Text(item.name, fontSize = 10.sp) })
            }
        }
        StackHistogramCurve(
            points = state.curves[channel] ?: defaultCurvePoints,
            histogram = histogram,
            onPoints = { points -> onState { it.copy(curves = it.curves + (channel to points)) } },
        )
    }
}

@Composable
private fun StackHistogramCurve(points: List<CurvePoint>, histogram: IntArray, onPoints: (List<CurvePoint>) -> Unit) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val current by rememberUpdatedState(points)
    ComposeCanvas(
        Modifier
            .fillMaxWidth()
            .height(122.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF18181C))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        selected = current.indices.minByOrNull { index ->
                            val point = current[index]
                            val dx = offset.x - point.x * size.width
                            val dy = offset.y - (1f - point.y) * size.height
                            dx * dx + dy * dy
                        }
                    },
                    onDrag = { change, _ ->
                        val index = selected ?: return@detectDragGestures
                        val old = current[index]
                        val minX = current.getOrNull(index - 1)?.x?.plus(0.02f) ?: 0f
                        val maxX = current.getOrNull(index + 1)?.x?.minus(0.02f) ?: 1f
                        val next = old.copy(
                            x = if (index == 0) 0f else if (index == current.lastIndex) 1f else (change.position.x / size.width).coerceIn(minX, maxX),
                            y = (1f - change.position.y / size.height).coerceIn(0f, 1f),
                        )
                        onPoints(current.toMutableList().also { it[index] = next })
                        change.consume()
                    },
                    onDragEnd = { selected = null },
                    onDragCancel = { selected = null },
                )
            },
    ) {
        val largest = histogram.maxOrNull()?.coerceAtLeast(1) ?: 1
        histogram.forEachIndexed { index, value ->
            val height = (value.toFloat() / largest * size.height * 0.75f).coerceAtLeast(1f)
            drawRect(Color(0xFF33333A), Offset(index * size.width / 48f, size.height - height), androidx.compose.ui.geometry.Size(size.width / 56f, height))
        }
        for (index in 0 until points.lastIndex) {
            val a = points[index]
            val b = points[index + 1]
            drawLine(Color(0xFFFFC400), Offset(a.x * size.width, (1f - a.y) * size.height), Offset(b.x * size.width, (1f - b.y) * size.height), strokeWidth = 3f)
        }
        points.forEach { point -> drawCircle(Color.White, 5f, Offset(point.x * size.width, (1f - point.y) * size.height)) }
    }
}

private fun stackHistogram(bitmap: Bitmap): IntArray {
    val preview = scaledPreviewBitmap(bitmap, 256)
    val pixels = IntArray(preview.width * preview.height)
    preview.getPixels(pixels, 0, preview.width, 0, 0, preview.width, preview.height)
    if (preview !== bitmap) preview.recycle()
    return IntArray(48).also { bins ->
        pixels.forEach { pixel ->
            val luma = (android.graphics.Color.red(pixel) * 0.2126f + android.graphics.Color.green(pixel) * 0.7152f + android.graphics.Color.blue(pixel) * 0.0722f) / 255f
            bins[(luma * (bins.size - 1)).roundToInt().coerceIn(0, bins.lastIndex)]++
        }
    }
}

@Composable
private fun StackLensShapeRow(state: EditorState, onState: ((EditorState) -> EditorState) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LensBlurShape.values().forEach { shape ->
                FilterChip(selected = state.lensBlurShape == shape, onClick = { onState { it.copy(lensBlurShape = shape) } }, label = { Text(stringResource(if (shape == LensBlurShape.Radial) R.string.fossin_radial else R.string.fossin_linear), fontSize = 11.sp) })
            }
        }
        Text(stringResource(R.string.fossin_lens_blur_gesture_hint), color = Color(0xFFA8A8AF), fontSize = 11.sp)
    }
}

@Composable
private fun StackCropModeRow(state: EditorState, onState: ((EditorState) -> EditorState) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(CropMode.values().toList()) { mode ->
                FilterChip(
                    selected = state.cropMode == mode,
                    onClick = { onState { it.copy(cropMode = mode) } },
                    label = { Text(stringResource(stackCropModeLabel(mode)), fontSize = 11.sp) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { onState { it.copy(rotation = (it.rotation + 270) % 360) } }) {
                Text(stringResource(R.string.fossin_rotate_left), color = Color.White)
            }
            TextButton(onClick = { onState { it.copy(rotation = (it.rotation + 90) % 360) } }) {
                Text(stringResource(R.string.fossin_rotate_right), color = Color.White)
            }
        }
    }
}

@Composable
private fun StackWhiteBalanceContext(onPickNeutral: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onPickNeutral) {
            Text(stringResource(R.string.fossin_pick_neutral), color = Color(0xFFFFC400))
        }
    }
}

@StringRes
private fun stackCropModeLabel(mode: CropMode): Int = when (mode) {
    CropMode.Original -> R.string.fossin_original
    CropMode.Free -> R.string.fossin_free
    CropMode.Square -> R.string.fossin_square
    CropMode.ThreeTwo -> R.string.fossin_three_two
    CropMode.FourThree -> R.string.fossin_four_three
    CropMode.FiveFour -> R.string.fossin_five_four
    CropMode.SevenFive -> R.string.fossin_seven_five
    CropMode.SixteenNine -> R.string.fossin_sixteen_nine
}

@Composable
private fun StackHdrPresets(state: EditorState, onState: ((EditorState) -> EditorState) -> Unit) {
    val items = listOf(
        R.string.fossin_hdr_nature to 0.35f,
        R.string.fossin_hdr_people to 0.22f,
        R.string.fossin_hdr_fine to 0.55f,
        R.string.fossin_hdr_intense to 0.82f,
    )
    LazyRow(
        Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { item ->
            FilterChip(
                selected = kotlin.math.abs((state.snapEffects[SnapEffect.HdrScape] ?: 0f) - item.second) < 0.04f,
                onClick = { onState { it.copy(snapEffects = it.snapEffects + (SnapEffect.HdrScape to item.second)) } },
                label = { Text(stringResource(item.first), fontSize = 11.sp) },
            )
        }
    }
}

@Composable
private fun StackDoubleExposureContext(
    state: EditorState,
    onState: ((EditorState) -> EditorState) -> Unit,
    onImportOverlay: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fossin_double_exposure), color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onImportOverlay) {
                Text(stringResource(R.string.fossin_import_overlay), color = Color(0xFFFFC400))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(OverlayBlendMode.values().toList()) { mode ->
                FilterChip(
                    selected = state.overlayBlendMode == mode,
                    onClick = { onState { it.copy(overlayBlendMode = mode) } },
                    label = { Text(mode.name, fontSize = 10.sp) },
                )
            }
        }
    }
}

@Composable
private fun StackTextEditor(state: EditorState, onState: ((EditorState) -> EditorState) -> Unit) {
    BasicTextField(
        value = state.text,
        onValueChange = { text -> onState { it.copy(text = text) } },
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
        modifier = Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(14.dp),
        decorationBox = { field ->
            if (state.text.isBlank()) Text(stringResource(R.string.fossin_text_hint), color = Color(0xFF98989E))
            field()
        },
    )
}

@Composable
private fun StackFrameChooser(state: EditorState, onState: ((EditorState) -> EditorState) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().background(Color(0xF2070708)).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(FrameStyle.values().toList()) { style ->
            FilterChip(selected = state.frameStyle == style, onClick = { onState { it.copy(frameStyle = style) } }, label = { Text(style.name, fontSize = 11.sp) })
        }
    }
}

@Composable
private fun StackMaskControls(
    advanced: Boolean,
    addMode: Boolean,
    overlayVisible: Boolean,
    feather: Float,
    onAdd: () -> Unit,
    onSubtract: () -> Unit,
    onInvert: () -> Unit,
    onOverlay: () -> Unit,
    onFeather: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xE8000000)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.fossin_mask_prompt), color = Color.White, fontSize = 12.sp)
        if (advanced) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = addMode, onClick = onAdd, label = { Text(stringResource(R.string.fossin_mask_add), fontSize = 10.sp) })
                FilterChip(selected = !addMode, onClick = onSubtract, label = { Text(stringResource(R.string.fossin_mask_subtract), fontSize = 10.sp) })
                TextButton(onClick = onInvert) { Text(stringResource(R.string.fossin_mask_invert), color = Color.White, fontSize = 10.sp) }
                TextButton(onClick = onOverlay) { Text(stringResource(R.string.fossin_mask_overlay), color = if (overlayVisible) Color(0xFFFFC400) else Color.White, fontSize = 10.sp) }
            }
            Slider(value = feather, onValueChange = onFeather, valueRange = 0f..0.4f, modifier = Modifier.fillMaxWidth())
        } else {
            Text(stringResource(R.string.fossin_mask_model_missing), color = Color(0xFFA9A9AE), fontSize = 11.sp)
        }
    }
}

@Composable
private fun BoxScope.StackMaskOverlay(mask: StackMask, bitmap: Bitmap) {
    ComposeCanvas(Modifier.fillMaxSize()) {
        val rect = displayedImageRect(size.width, size.height, bitmap)
        val cols = 48
        val rows = maxOf(1, (cols * rect.height / rect.width).roundToInt())
        for (row in 0 until rows) for (column in 0 until cols) {
            val alpha = sampleStackMask(mask, (column + 0.5f) / cols, (row + 0.5f) / rows)
            if (alpha > 0.04f) {
                drawRect(
                    Color(0xFFE53935).copy(alpha = alpha * 0.42f),
                    Offset(rect.left + column * rect.width / cols, rect.top + row * rect.height / rows),
                    androidx.compose.ui.geometry.Size(rect.width / cols + 1f, rect.height / rows + 1f),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.StackLensGuide(state: EditorState, bitmap: Bitmap) {
    ComposeCanvas(Modifier.fillMaxSize()) {
        val rect = displayedImageRect(size.width, size.height, bitmap)
        val center = Offset(rect.left + state.lensBlurX * rect.width, rect.top + state.lensBlurY * rect.height)
        if (state.lensBlurShape == LensBlurShape.Radial) {
            drawCircle(Color(0xFFFFC400), state.lensBlurRadius * minOf(rect.width, rect.height), center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        } else {
            val length = maxOf(rect.width, rect.height)
            val radians = state.lensBlurAngle * Math.PI.toFloat()
            val delta = Offset(
                kotlin.math.cos(radians.toDouble()).toFloat() * length,
                kotlin.math.sin(radians.toDouble()).toFloat() * length,
            )
            drawLine(Color(0xFFFFC400), center - delta, center + delta, strokeWidth = 2f)
        }
        drawCircle(Color.White, 5f, center)
    }
}

@Composable
private fun BoxScope.StackVignetteGuide(state: EditorState, bitmap: Bitmap) {
    ComposeCanvas(Modifier.fillMaxSize()) {
        val rect = displayedImageRect(size.width, size.height, bitmap)
        val center = Offset(rect.left + state.vignetteX * rect.width, rect.top + state.vignetteY * rect.height)
        drawCircle(Color(0xFFFFC400), state.vignetteRadius * minOf(rect.width, rect.height), center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f))
        drawCircle(Color(0xFFFFC400), 5f, center)
    }
}

@Composable
private fun BoxScope.StackCropGuide(state: EditorState, bitmap: Bitmap) {
    if (state.cropMode != CropMode.Free) return
    ComposeCanvas(Modifier.fillMaxSize()) {
        val rect = displayedImageRect(size.width, size.height, bitmap)
        val left = rect.left + state.cropLeft * rect.width
        val top = rect.top + state.cropTop * rect.height
        val right = rect.left + state.cropRight * rect.width
        val bottom = rect.top + state.cropBottom * rect.height
        val shade = Color.Black.copy(alpha = 0.48f)
        drawRect(shade, Offset(rect.left, rect.top), androidx.compose.ui.geometry.Size(rect.width, (top - rect.top).coerceAtLeast(0f)))
        drawRect(shade, Offset(rect.left, bottom), androidx.compose.ui.geometry.Size(rect.width, (rect.top + rect.height - bottom).coerceAtLeast(0f)))
        drawRect(shade, Offset(rect.left, top), androidx.compose.ui.geometry.Size((left - rect.left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)))
        drawRect(shade, Offset(right, top), androidx.compose.ui.geometry.Size((rect.left + rect.width - right).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)))
        val line = Color(0xFFFFC400)
        drawRect(line, Offset(left, top), androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(0f), 2f))
        drawRect(line, Offset(left, bottom - 2f), androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(0f), 2f))
        drawRect(line, Offset(left, top), androidx.compose.ui.geometry.Size(2f, (bottom - top).coerceAtLeast(0f)))
        drawRect(line, Offset(right - 2f, top), androidx.compose.ui.geometry.Size(2f, (bottom - top).coerceAtLeast(0f)))
    }
}

@Composable
private fun StackLayersSheet(
    operations: List<StackOperation>,
    onDismiss: () -> Unit,
    onEdit: (StackOperation) -> Unit,
    onMask: (StackOperation) -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSaveDesign: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize().background(Color(0xE9000000)).padding(horizontal = 18.dp, vertical = 48.dp),
        color = Color(0xFF151518),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fossin_layers), color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = Color(0xFFFFC400)) }
            }
            Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Color(0xFF25252A), shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.fossin_original_layer), color = Color.White, modifier = Modifier.padding(14.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                operations.forEach { operation ->
                    Surface(color = Color(0xFF222227), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(operation.tool.labelRes), color = Color.White, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { onEdit(operation) }) { Text(stringResource(R.string.fossin_edit), color = Color.White, fontSize = 11.sp) }
                                if (!operation.tool.isGlobalGeometry) TextButton(onClick = { onMask(operation) }) { Text(stringResource(R.string.fossin_mask), color = Color.White, fontSize = 11.sp) }
                                TextButton(onClick = { onToggle(operation.id) }) { Text(stringResource(if (operation.enabled) R.string.fossin_disable else R.string.fossin_enable), color = if (operation.enabled) Color.White else Color(0xFFA0A0A8), fontSize = 11.sp) }
                                TextButton(onClick = { onDelete(operation.id) }) { Text(stringResource(R.string.fossin_delete), color = Color(0xFFFF887B), fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
            Button(onClick = onSaveDesign, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006E51)), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fossin_save_design)) }
        }
    }
}

@Composable
private fun StackEditorHall(
    onImport: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenProject: (StackProjectSummary) -> Unit,
) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf<List<StackProjectSummary>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) { projects = StackProjectStore.list(context) }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF080809)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.fossin_library_subtitle), color = Color(0xFFA0A0A8), fontSize = 13.sp)
                }
                IconButton(onClick = { refresh++ }) { Icon(AppIcons.RestartAlt, stringResource(R.string.fossin_library_refresh), tint = Color.White) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onImport, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006E51))) { Text(stringResource(R.string.fossin_import)) }
                TextButton(onClick = onOpenCamera, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fossin_open_camera), color = Color.White) }
            }
            Text(stringResource(R.string.fossin_library_imported_edited), color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (projects.isEmpty()) {
                Surface(Modifier.fillMaxWidth().height(110.dp), color = Color(0xFF17171B), shape = RoundedCornerShape(18.dp)) {
                    Text(stringResource(R.string.fossin_library_imported_empty), color = Color(0xFFA6A6AC), textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(projects, key = StackProjectSummary::id) { project -> StackProjectCard(project, onOpenProject) }
                }
            }
        }
    }
}

@Composable
private fun StackProjectCard(project: StackProjectSummary, onOpen: (StackProjectSummary) -> Unit) {
    val context = LocalContext.current
    var preview by remember(project.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(project.uri) { preview = loadBitmap(context, project.uri, 384) }
    Surface(
        Modifier.width(144.dp).height(184.dp).clip(RoundedCornerShape(18.dp)).clickable { onOpen(project) },
        color = Color(0xFF1B1B20),
        shape = RoundedCornerShape(18.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            preview?.let { Image(it.asImageBitmap(), project.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Text(project.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xB8000000)).padding(10.dp))
        }
    }
}

private data class StackDesign(val name: String, val operations: List<StackOperation>)

private object StackDesignStore {
    private const val PREFERENCES = "fossin_stack_designs"
    private const val KEY = "designs"

    fun save(context: Context, operations: List<StackOperation>) {
        val existing = list(context).toMutableList()
        val serial = JsonArray()
        operations.forEach { operation ->
            val state = operation.state.copy(lut = null, lutUri = null, overlayUri = null)
            val bundle = with(editorStateSaver) { FossinProjectSaverScope.save(state) } ?: Bundle()
            serial.add(JsonObject().apply {
                addProperty("tool", operation.tool.name)
                addProperty("state", encodeBundle(bundle))
                operation.preset?.let { addProperty("preset", it) }
            })
        }
        existing += StackDesign("Design ${existing.size + 1}", operations.map { it.copy(mask = null) })
        val raw = JsonArray().apply {
            existing.takeLast(24).forEachIndexed { index, design ->
                add(JsonObject().apply {
                    addProperty("name", design.name)
                    if (index == existing.lastIndex) add("operations", serial) else add("operations", encodeDesign(design.operations))
                })
            }
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, raw.toString()).apply()
    }

    fun list(context: Context): List<StackDesign> = runCatching {
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        JsonParser.parseString(raw).asJsonArray.mapNotNull { item ->
            val objectValue = item.asJsonObject
            val ops = objectValue.getAsJsonArray("operations")?.mapNotNull { decodeDesignOperation(it.asJsonObject) }.orEmpty()
            if (ops.isEmpty()) null else StackDesign(objectValue.string("name") ?: "Design", ops)
        }
    }.getOrDefault(emptyList())

    private fun encodeDesign(operations: List<StackOperation>) = JsonArray().apply {
        operations.forEach { operation ->
            val bundle = with(editorStateSaver) { FossinProjectSaverScope.save(operation.state.copy(lut = null, lutUri = null, overlayUri = null)) } ?: Bundle()
            add(JsonObject().apply {
                addProperty("tool", operation.tool.name)
                addProperty("state", encodeBundle(bundle))
                operation.preset?.let { addProperty("preset", it) }
            })
        }
    }

    private fun decodeDesignOperation(value: JsonObject): StackOperation? = runCatching {
        val tool = value.string("tool")?.let { enumValueOf<StackTool>(it) } ?: return@runCatching null
        val state = value.string("state")?.let(::decodeBundle)?.let(editorStateSaver::restore) ?: return@runCatching null
        StackOperation(tool = tool, state = state, preset = value.string("preset"))
    }.getOrNull()

    private fun JsonObject.string(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString
}

private fun mergeStackMasks(current: StackMask?, next: StackMask, add: Boolean): StackMask {
    if (current == null || current.width != next.width || current.height != next.height) return next
    val combined = ByteArray(current.alpha.size) { index ->
        val old = current.alpha[index].toInt() and 0xff
        val incoming = next.alpha[index].toInt() and 0xff
        (if (add) maxOf(old, incoming) else (old - incoming).coerceAtLeast(0)).toByte()
    }
    return current.copy(alpha = combined)
}

/** Soft raster brush used after the automatic seed has created a mask. */
private fun paintStackMask(mask: StackMask, point: NormalizedPoint, add: Boolean, radiusFraction: Float = 0.065f): StackMask {
    val centerX = point.x.coerceIn(0f, 1f) * (mask.width - 1)
    val centerY = point.y.coerceIn(0f, 1f) * (mask.height - 1)
    val radius = (minOf(mask.width, mask.height) * radiusFraction.coerceIn(0.01f, 0.35f)).coerceAtLeast(1f)
    val left = (centerX - radius).roundToInt().coerceAtLeast(0)
    val right = (centerX + radius).roundToInt().coerceAtMost(mask.width - 1)
    val top = (centerY - radius).roundToInt().coerceAtLeast(0)
    val bottom = (centerY + radius).roundToInt().coerceAtMost(mask.height - 1)
    val output = mask.alpha.copyOf()
    for (y in top..bottom) for (x in left..right) {
        val distance = kotlin.math.sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toDouble()).toFloat()
        if (distance > radius) continue
        val strength = ((1f - distance / radius) * 255f).roundToInt().coerceIn(0, 255)
        val index = y * mask.width + x
        val previous = output[index].toInt() and 0xff
        output[index] = if (add) maxOf(previous, strength).toByte() else (previous - strength).coerceAtLeast(0).toByte()
    }
    return mask.copy(alpha = output)
}

private fun stackAutoTune(bitmap: Bitmap, state: EditorState): EditorState {
    val preview = scaledPreviewBitmap(bitmap, 256)
    val pixels = IntArray(preview.width * preview.height)
    preview.getPixels(pixels, 0, preview.width, 0, 0, preview.width, preview.height)
    if (preview !== bitmap) preview.recycle()
    val mean = pixels.asSequence().map { color ->
        (android.graphics.Color.red(color) * 0.2126f + android.graphics.Color.green(color) * 0.7152f + android.graphics.Color.blue(color) * 0.0722f) / 255f
    }.average().toFloat()
    val exposure = ((0.5f - mean) * 1.4f).coerceIn(-0.7f, 0.7f)
    return state.copy(exposure = exposure, contrast = 1.08f, ambiance = 0.16f, shadows = 0.12f, highlights = -0.08f)
}
