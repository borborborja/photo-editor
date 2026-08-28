package com.hinnka.mycamera.ui.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.CameraState
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.video.CaptureMode

/**
 * Presentation-only camera chrome.  It deliberately has no Photon-specific
 * vocabulary: the viewfinder remains dominant and every advanced setting is
 * reached through a single, predictable control surface.
 */
@Composable
fun PixelCameraTopBar(
    captureMode: CaptureMode,
    flashMode: Int,
    videoTorchEnabled: Boolean,
    isPro: Boolean,
    rawEnabled: Boolean,
    lutEnabled: Boolean,
    focusLocked: Boolean,
    onFlashClick: () -> Unit,
    onControlsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelCameraGlassButton(
            onClick = onFlashClick,
            buttonDescription = stringResource(R.string.flash),
        ) {
            Icon(
                imageVector = when {
                    captureMode == CaptureMode.VIDEO && videoTorchEnabled -> AppIcons.FlashlightOn
                    captureMode == CaptureMode.VIDEO -> AppIcons.FlashlightOff
                    flashMode == 1 -> AppIcons.FlashOn
                    flashMode == 2 -> AppIcons.FlashlightOn
                    else -> AppIcons.FlashOff
                },
                contentDescription = null,
                tint = if (
                    (captureMode == CaptureMode.VIDEO && videoTorchEnabled) ||
                    (captureMode != CaptureMode.VIDEO && flashMode != 0)
                ) accent else Color.White,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isPro && rawEnabled) PixelCameraStatusChip("RAW", accent)
            if (isPro && lutEnabled) PixelCameraStatusChip("LUT", accent)
            if (focusLocked) PixelCameraStatusChip("AF LOCK", accent)
        }

        PixelCameraGlassButton(
            onClick = onControlsClick,
            buttonDescription = if (isPro) "Controls Pro" else "Controls",
        ) {
            Icon(
                imageVector = AppIcons.Tune,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun PixelCameraGlassButton(
    onClick: () -> Unit,
    buttonDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.46f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = buttonDescription },
        ) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun PixelCameraStatusChip(label: String, accent: Color) {
    Surface(
        color = Color.Black.copy(alpha = 0.48f),
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/** Familiar Pixel-like stops, filtered so unavailable lenses are never offered. */
internal fun pixelZoomStops(minimumZoom: Float, maximumZoom: Float): List<Float> {
    val lower = minimumZoom.coerceAtLeast(0.1f)
    val upper = maximumZoom.coerceAtLeast(lower)
    val stops = listOf(0.5f, 1f, 2f, 5f, 10f).filter { it in (lower - 0.05f)..(upper + 0.05f) }
    return stops.ifEmpty { listOf(1f.coerceIn(lower, upper)) }
}

@Composable
fun PixelCameraZoomPill(
    zoomRatio: Float,
    minimumZoom: Float,
    maximumZoom: Float,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stops = remember(minimumZoom, maximumZoom) { pixelZoomStops(minimumZoom, maximumZoom) }
    if (stops.size <= 1) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stops.forEach { stop ->
                val selected = kotlin.math.abs(zoomRatio - stop) < 0.13f
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { onZoomSelected(stop) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = formatPixelZoom(stop),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

private fun formatPixelZoom(value: Float): String {
    val whole = value.toInt()
    return if (value == whole.toFloat()) "${whole}×" else "${value}×"
}

/** Bottom capture controls shared by the simple and Pro experiences. */
@Composable
fun PixelCameraCaptureControls(
    state: CameraState,
    latestPhoto: MediaData?,
    onGalleryClick: () -> Unit,
    onSwitchCameraClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onModeSelected: (CaptureMode) -> Unit,
    onPauseToggle: () -> Unit,
    onVideoFrameCapture: () -> Unit,
    allowLongPress: Boolean,
    modifier: Modifier = Modifier,
) {
    val isRecording = state.videoRecordingState.isRecording
    val isProcessing = state.videoRecordingState.isProcessing
    val selectedMode = if (state.captureMode == CaptureMode.VIDEO) CaptureMode.VIDEO else CaptureMode.PHOTO
    val modeEnabled = !isRecording && !isProcessing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.captureMode == CaptureMode.QUICK_SHOT) {
            PixelCameraStatusChip("RÀPIDA", MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRecording) {
                PixelCameraRoundAction(
                    icon = if (state.videoRecordingState.isPaused) Icons.Default.PlayArrow else AppIcons.Pause,
                    description = if (state.videoRecordingState.isPaused) "Resume video" else "Pause video",
                    onClick = onPauseToggle,
                )
            } else {
                PixelCameraGalleryButton(latestPhoto = latestPhoto, onClick = onGalleryClick)
            }

            PixelCameraShutter(
                captureMode = state.captureMode,
                isCapturing = state.isCapturing,
                isRecording = isRecording,
                isProcessing = isProcessing,
                enabled = !state.isCapturing && !isProcessing,
                allowLongPress = allowLongPress,
                onClick = onCaptureClick,
                onLongPressStart = onLongPressStart,
                onLongPressEnd = onLongPressEnd,
            )

            if (isRecording) {
                PixelCameraRoundAction(
                    icon = AppIcons.CameraAlt,
                    description = stringResource(R.string.take_photo),
                    onClick = onVideoFrameCapture,
                )
            } else {
                PixelCameraRoundAction(
                    icon = AppIcons.Cameraswitch,
                    description = stringResource(R.string.switch_camera),
                    onClick = onSwitchCameraClick,
                    enabled = !isProcessing,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        PixelCameraModeRail(
            selected = selectedMode,
            enabled = modeEnabled,
            onSelected = onModeSelected,
        )
    }
}

@Composable
private fun PixelCameraGalleryButton(latestPhoto: MediaData?, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = Color.Black.copy(alpha = 0.46f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
    ) {
        if (latestPhoto == null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(AppIcons.PhotoLibrary, stringResource(R.string.gallery), tint = Color.White)
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(latestPhoto.thumbnailUri)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.gallery),
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun PixelCameraRoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.size(48.dp),
        color = Color.Black.copy(alpha = 0.46f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, description, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f))
        }
    }
}

@Composable
private fun PixelCameraShutter(
    captureMode: CaptureMode,
    isCapturing: Boolean,
    isRecording: Boolean,
    isProcessing: Boolean,
    enabled: Boolean,
    allowLongPress: Boolean,
    onClick: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
) {
    val centerScale by animateFloatAsState(
        targetValue = if (isRecording) 0.54f else 0.86f,
        label = "pixelShutterCenterScale",
    )
    var longPressStarted = false
    Surface(
        modifier = Modifier
            .size(82.dp)
            .clip(CircleShape)
            .pointerInput(enabled, allowLongPress, isRecording) {
                detectTapGestures(
                    onTap = { if (enabled) onClick() },
                    onLongPress = if (allowLongPress && !isRecording) {
                        {
                            if (enabled) {
                                longPressStarted = true
                                onLongPressStart()
                            }
                        }
                    } else null,
                    onPress = {
                        try {
                            tryAwaitRelease()
                        } finally {
                            if (longPressStarted) {
                                longPressStarted = false
                                onLongPressEnd()
                            }
                        }
                    },
                )
            },
        color = Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(4.dp, Color.White.copy(alpha = if (enabled) 1f else 0.4f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size((70 * centerScale).dp),
                color = if (captureMode == CaptureMode.VIDEO) Color(0xFFE53935) else Color.White,
                shape = if (isRecording) RoundedCornerShape(10.dp) else CircleShape,
            ) {}
            if (isCapturing || isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = if (captureMode == CaptureMode.VIDEO) Color.White else Color.Black,
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

@Composable
private fun PixelCameraModeRail(
    selected: CaptureMode,
    enabled: Boolean,
    onSelected: (CaptureMode) -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(min = 168.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelCameraModeItem(
            text = stringResource(R.string.capture_mode_video),
            selected = selected == CaptureMode.VIDEO,
            enabled = enabled,
            onClick = { onSelected(CaptureMode.VIDEO) },
        )
        PixelCameraModeItem(
            text = stringResource(R.string.capture_mode_photo),
            selected = selected == CaptureMode.PHOTO,
            enabled = enabled,
            onClick = { onSelected(CaptureMode.PHOTO) },
        )
    }
}

@Composable
private fun PixelCameraModeItem(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text.uppercase(),
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.72f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Box(
            modifier = Modifier
                .height(2.dp)
                .widthIn(min = if (selected) 20.dp else 0.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}
