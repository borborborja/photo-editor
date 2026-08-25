package com.hinnka.mycamera.ui.camera

import android.graphics.SurfaceTexture
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hinnka.mycamera.R
import com.hinnka.mycamera.model.BeginnerSimulation
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.ui.components.GalleryThumbnail
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.viewmodel.CameraViewModel
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import com.hinnka.mycamera.video.CaptureMode

/**
 * A deliberately calm, Pixel-inspired camera: photo-first, big shutter and a
 * compact row of built-in simulations.  It reuses the fast Camera2 pipeline
 * from [CameraViewModel], but never exposes or applies a creative LUT.
 */
@Composable
fun BeginnerCameraScreen(
    viewModel: CameraViewModel,
    galleryViewModel: GalleryViewModel,
    onGalleryClick: () -> Unit,
    onSwitchToPro: () -> Unit,
    canSwitchToPro: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val isCameraInitialized by viewModel.isInitialized.collectAsState()
    val latestPhoto by galleryViewModel.latestPhoto.collectAsState()
    val simulation by viewModel.beginnerSimulation.collectAsState()
    val recipe by viewModel.currentRecipeParams.collectAsState()
    var previewSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }

    // Covers relaunches into Beginner mode as well as the first-run wizard.
    LaunchedEffect(Unit) {
        viewModel.prepareBeginnerCamera()
    }

    LaunchedEffect(
        isCameraInitialized,
        previewSurfaceTexture,
        state.availableCameras.isNotEmpty(),
    ) {
        val surfaceTexture = previewSurfaceTexture ?: return@LaunchedEffect
        if (isCameraInitialized) {
            viewModel.openCamera(surfaceTexture)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkAndRecoverCamera()
        galleryViewModel.refreshLatestPhoto()
    }

    LaunchedEffect(Unit) {
        viewModel.imageSavedEvent.collect {
            galleryViewModel.refreshLatestPhoto()
        }
    }

    val currentCameraId = state.currentCameraId
    val calibrationOffset by viewModel.getCameraOrientationOffset(currentCameraId)
        .collectAsState(initial = 0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreviewGL(
            aspectRatio = state.getPreviewAspectRatio(),
            previewSize = state.currentPreviewSize,
            captureSize = state.currentCaptureSize,
            captureMode = CaptureMode.PHOTO,
            sensorOrientation = state.getCurrentCameraInfo()?.sensorOrientation ?: 0,
            lensFacing = if (
                state.getCurrentCameraInfo()?.lensFacing ==
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
            ) {
                0
            } else {
                1
            },
            calibrationOffset = calibrationOffset,
            baselineLut = null,
            currentLut = null,
            baselineColorRecipeParams = ColorRecipeParams.DEFAULT,
            colorRecipeParams = recipe,
            focusPoint = state.focusPoint,
            focusPointSource = state.focusPointSource,
            isFocusLocked = state.isFocusLocked,
            isFocusing = state.isFocusing,
            focusSuccess = state.focusSuccess,
            meteringMode = state.meteringMode,
            onSurfaceTextureReady = { previewSurfaceTexture = it },
            onSurfaceDestroyed = { destroyedSurfaceTexture ->
                if (previewSurfaceTexture === destroyedSurfaceTexture) {
                    previewSurfaceTexture = null
                }
                viewModel.closeCamera(destroyedSurfaceTexture)
            },
            onTap = { x, y, width, height ->
                if (state.isFocusLocked) {
                    viewModel.unlockFocus()
                } else {
                    viewModel.focusOnPoint(x, y, width, height)
                }
            },
            onLongPress = { x, y, width, height ->
                viewModel.lockFocusOnPoint(x, y, width, height)
            },
            onGLSurfaceViewReady = { viewModel.glSurfaceView = it },
            isAutoFocus = state.isAutoFocus,
            focusPeakingEnabled = false,
            modifier = Modifier.fillMaxSize(),
        )

        if (state.showGrid) {
            GridOverlay(
                aspectRatio = state.getPreviewAspectRatio(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        BeginnerTopBar(
            flashMode = state.flashMode,
            onFlashToggle = viewModel::toggleFlash,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.camera_experience_simulations),
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            BeginnerSimulationRow(
                selected = simulation,
                onSelected = viewModel::selectBeginnerSimulation,
            )
            Spacer(Modifier.height(14.dp))
            BeginnerZoomRow(
                zoomRatio = viewModel.zoomRatioByMain,
                onZoomSelected = { zoom ->
                    viewModel.setZoomRatio(
                        zoom.coerceIn(viewModel.globalMinZoom, viewModel.globalMaxZoom)
                    )
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GalleryThumbnail(
                    latestPhoto = latestPhoto,
                    viewModel = galleryViewModel,
                    onClick = onGalleryClick,
                )

                BeginnerShutterButton(
                    enabled = !state.isCapturing && state.captureMode == CaptureMode.PHOTO,
                    onCapture = viewModel::capture,
                )

                IconButton(
                    onClick = viewModel::switchCamera,
                    enabled = !state.isCapturing,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = AppIcons.Cameraswitch,
                        contentDescription = stringResource(R.string.switch_camera),
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.camera_experience_photo),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    text = stringResource(R.string.camera_experience_pro_switch),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = canSwitchToPro, onClick = onSwitchToPro)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun BeginnerTopBar(
    flashMode: Int,
    onFlashToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 36.dp, start = 14.dp, end = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.46f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                text = stringResource(R.string.camera_experience_beginner),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        IconButton(onClick = onFlashToggle) {
            Icon(
                imageVector = when (flashMode) {
                    0 -> AppIcons.FlashOff
                    1 -> AppIcons.FlashOn
                    else -> AppIcons.FlashlightOn
                },
                contentDescription = stringResource(R.string.flash),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun BeginnerSimulationRow(
    selected: BeginnerSimulation,
    onSelected: (BeginnerSimulation) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BeginnerSimulation.entries.forEach { simulation ->
            FilterChip(
                selected = simulation == selected,
                onClick = { onSelected(simulation) },
                label = {
                    Text(
                        text = stringResource(simulation.titleRes()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.58f),
                    labelColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = simulation == selected,
                    borderColor = Color.White.copy(alpha = 0.45f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun BeginnerZoomRow(
    zoomRatio: Float,
    onZoomSelected: (Float) -> Unit,
) {
    val stops = listOf(0.5f, 1f, 2f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stops.forEach { zoom ->
            val selected = kotlin.math.abs(zoomRatio - zoom) < 0.12f
            Text(
                text = formatZoomRatioLabel(zoom),
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onZoomSelected(zoom) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun BeginnerShutterButton(
    enabled: Boolean,
    onCapture: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onCapture),
        color = Color.White.copy(alpha = if (enabled) 1f else 0.48f),
        shape = CircleShape,
        contentColor = Color.Black,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(62.dp),
                color = Color.Transparent,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.Black),
            ) {}
        }
    }
}

private fun BeginnerSimulation.titleRes(): Int = when (this) {
    BeginnerSimulation.NATURAL -> R.string.camera_simulation_natural
    BeginnerSimulation.WARM -> R.string.camera_simulation_warm
    BeginnerSimulation.COOL -> R.string.camera_simulation_cool
    BeginnerSimulation.VIVID -> R.string.camera_simulation_vivid
    BeginnerSimulation.MONO -> R.string.camera_simulation_mono
}
