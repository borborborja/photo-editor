package com.hinnka.mycamera.ui.camera

import android.graphics.SurfaceTexture
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hinnka.mycamera.R
import com.hinnka.mycamera.model.BeginnerSimulation
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.ui.icons.AppIcons
import com.hinnka.mycamera.video.CaptureMode
import com.hinnka.mycamera.viewmodel.CameraViewModel
import com.hinnka.mycamera.viewmodel.GalleryViewModel

/** A calm, photo-first camera that deliberately never exposes LUT or RAW controls. */
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
    var controlsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.prepareBeginnerCamera() }
    LaunchedEffect(isCameraInitialized, previewSurfaceTexture, state.availableCameras.isNotEmpty()) {
        previewSurfaceTexture?.let { surfaceTexture ->
            if (isCameraInitialized) viewModel.openCamera(surfaceTexture)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkAndRecoverCamera()
        galleryViewModel.refreshLatestPhoto()
    }
    LaunchedEffect(Unit) {
        viewModel.imageSavedEvent.collect { galleryViewModel.refreshLatestPhoto() }
    }

    val currentCamera = state.getCurrentCameraInfo()
    val displayIntrinsicZoom = currentCamera?.displayIntrinsicZoomRatio?.takeIf { it > 0f } ?: 1f
    val minimumVisibleZoom = (currentCamera?.minZoom ?: 1f) * displayIntrinsicZoom
    val maximumVisibleZoom = (currentCamera?.maxZoom ?: 1f) * displayIntrinsicZoom
    val calibrationOffset by viewModel.getCameraOrientationOffset(state.currentCameraId)
        .collectAsState(initial = 0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(state.availableCameras) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            viewModel.isZooming = true
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                viewModel.setZoomRatio(
                                    (viewModel.zoomRatioByMain * zoom).coerceIn(
                                        viewModel.globalMinZoom,
                                        viewModel.globalMaxZoom,
                                    ),
                                )
                            }
                            event.changes.forEach { it.consume() }
                        }
                        if (event.changes.all { !it.pressed }) {
                            viewModel.isZooming = false
                            break
                        }
                    }
                }
            },
    ) {
        CameraPreviewGL(
            aspectRatio = state.getPreviewAspectRatio(),
            previewSize = state.currentPreviewSize,
            captureSize = state.currentCaptureSize,
            captureMode = state.captureMode,
            sensorOrientation = currentCamera?.sensorOrientation ?: 0,
            lensFacing = if (
                currentCamera?.lensFacing ==
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
            ) 0 else 1,
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
                if (previewSurfaceTexture === destroyedSurfaceTexture) previewSurfaceTexture = null
                viewModel.closeCamera(destroyedSurfaceTexture)
            },
            onTap = { x, y, width, height ->
                if (state.isFocusLocked) viewModel.unlockFocus()
                else viewModel.focusOnPoint(x, y, width, height)
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

        PixelCameraTopBar(
            captureMode = state.captureMode,
            flashMode = state.flashMode,
            videoTorchEnabled = state.videoConfig.torchEnabled,
            isPro = false,
            rawEnabled = false,
            lutEnabled = false,
            focusLocked = state.isFocusLocked,
            onFlashClick = {
                if (state.captureMode == CaptureMode.VIDEO) {
                    viewModel.setVideoTorchEnabled(!state.videoConfig.torchEnabled)
                } else {
                    viewModel.toggleFlash()
                }
            },
            onControlsClick = { controlsOpen = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelCameraZoomPill(
                zoomRatio = viewModel.zoomRatioByMain,
                minimumZoom = minimumVisibleZoom,
                maximumZoom = maximumVisibleZoom,
                onZoomSelected = viewModel::setZoomRatio,
            )
            Spacer(Modifier.height(18.dp))
            PixelCameraCaptureControls(
                state = state,
                latestPhoto = latestPhoto,
                onGalleryClick = onGalleryClick,
                onSwitchCameraClick = viewModel::switchCamera,
                onCaptureClick = viewModel::capture,
                onLongPressStart = viewModel::startContinuousCapture,
                onLongPressEnd = viewModel::stopContinuousCapture,
                onModeSelected = viewModel::setCaptureMode,
                onPauseToggle = {
                    if (state.videoRecordingState.isPaused) viewModel.resumeVideoRecording()
                    else viewModel.pauseVideoRecording()
                },
                onVideoFrameCapture = viewModel::captureVideoFrame,
                allowLongPress = state.captureMode == CaptureMode.PHOTO,
            )
        }
    }

    if (controlsOpen) {
        BeginnerCameraControlsSheet(
            selectedSimulation = simulation,
            showGrid = state.showGrid,
            timerSeconds = state.timerSeconds,
            canSwitchToPro = canSwitchToPro,
            onDismiss = { controlsOpen = false },
            onSimulationSelected = viewModel::selectBeginnerSimulation,
            onToggleGrid = viewModel::toggleGrid,
            onToggleTimer = viewModel::toggleTimer,
            onSwitchToPro = {
                controlsOpen = false
                onSwitchToPro()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeginnerCameraControlsSheet(
    selectedSimulation: BeginnerSimulation,
    showGrid: Boolean,
    timerSeconds: Int,
    canSwitchToPro: Boolean,
    onDismiss: () -> Unit,
    onSimulationSelected: (BeginnerSimulation) -> Unit,
    onToggleGrid: () -> Unit,
    onToggleTimer: () -> Unit,
    onSwitchToPro: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("Controls de càmera", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Text("Aspecte", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            BeginnerSimulationRow(selected = selectedSimulation, onSelected = onSimulationSelected)
            Text(
                "Simulacions integrades. No s’aplica cap LUT.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(18.dp))
            SimpleCameraToggleRow("Graella", showGrid, onToggleGrid)
            SimpleCameraActionRow(
                title = "Temporitzador",
                summary = if (timerSeconds == 0) "Desactivat" else "${timerSeconds} s",
                icon = AppIcons.Timer,
                onClick = onToggleTimer,
            )
            Spacer(Modifier.height(10.dp))
            SimpleCameraActionRow(
                title = "Passar a controls Pro",
                summary = "RAW, LUTs i ajustos manuals",
                icon = AppIcons.Tune,
                onClick = onSwitchToPro,
                enabled = canSwitchToPro,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SimpleCameraToggleRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SimpleCameraActionRow(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, title, tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BeginnerSimulationRow(selected: BeginnerSimulation, onSelected: (BeginnerSimulation) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BeginnerSimulation.entries.forEach { simulation ->
            FilterChip(
                selected = simulation == selected,
                onClick = { onSelected(simulation) },
                label = {
                    Text(
                        stringResource(simulation.titleRes()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
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
