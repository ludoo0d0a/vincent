package fr.geoking.vincent.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.Res
import vincent.composeapp.generated.resources.ar_anchor_need_marker
import vincent.composeapp.generated.resources.ar_anchor_reset
import vincent.composeapp.generated.resources.ar_anchor_setup_title
import vincent.composeapp.generated.resources.ar_anchor_tap_height
import vincent.composeapp.generated.resources.ar_anchor_tap_origin
import vincent.composeapp.generated.resources.ar_anchor_tap_width
import vincent.composeapp.generated.resources.ar_grid_height
import vincent.composeapp.generated.resources.ar_grid_width
import vincent.composeapp.generated.resources.ar_install
import vincent.composeapp.generated.resources.ar_install_action
import vincent.composeapp.generated.resources.ar_invalid_dimensions
import vincent.composeapp.generated.resources.ar_low_quality
import vincent.composeapp.generated.resources.ar_marker_print
import vincent.composeapp.generated.resources.ar_marker_searching
import vincent.composeapp.generated.resources.ar_marker_setup_title
import vincent.composeapp.generated.resources.ar_marker_width
import vincent.composeapp.generated.resources.ar_mode_anchor
import vincent.composeapp.generated.resources.ar_mode_marker
import vincent.composeapp.generated.resources.ar_mode_photo
import vincent.composeapp.generated.resources.ar_mode_title
import vincent.composeapp.generated.resources.ar_no_setup
import vincent.composeapp.generated.resources.ar_offset_x
import vincent.composeapp.generated.resources.ar_offset_y
import vincent.composeapp.generated.resources.ar_permission
import vincent.composeapp.generated.resources.ar_permission_action
import vincent.composeapp.generated.resources.ar_photo_hint
import vincent.composeapp.generated.resources.ar_retake
import vincent.composeapp.generated.resources.ar_searching
import vincent.composeapp.generated.resources.ar_setup_hint
import vincent.composeapp.generated.resources.ar_setup_save
import vincent.composeapp.generated.resources.ar_setup_start
import vincent.composeapp.generated.resources.ar_setup_title
import vincent.composeapp.generated.resources.ar_unsupported
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import fr.geoking.vincent.ar.ArProjection
import fr.geoking.vincent.ar.MarkerImages
import fr.geoking.vincent.ai.rememberPhotoCapture
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.rememberRackImageSaver
import fr.geoking.vincent.model.ArMode
import fr.geoking.vincent.model.NormPoint
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.RackArAnchor
import fr.geoking.vincent.model.RackArCalibration
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.model.cellSpotLabel
import fr.geoking.vincent.theme.VincentColors
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Per-frame AR tracking state, mutated in `onSessionUpdated` without reallocation. */
private class ArFrameHolder {
    var image: AugmentedImage? = null
    val view = FloatArray(16)
    val proj = FloatArray(16)
}

@Composable
actual fun ArScreen(rackIndex: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val rack = Racks.all.getOrNull(rackIndex)
    if (rack == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { cameraGranted = it }
    LaunchedEffect(Unit) { if (!cameraGranted) permLauncher.launch(Manifest.permission.CAMERA) }

    var setupMode by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !cameraGranted -> ArMessage(
                message = stringResource(Res.string.ar_permission),
                actionLabel = stringResource(Res.string.ar_permission_action),
                onAction = { permLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack,
            )

            // Configuration: a per-rack mode picker plus the matching calibration flow. Used when
            // the user explicitly reconfigures or the rack is not yet calibrated for its mode.
            setupMode || !rack.arReady -> ArConfigure(
                rackIndex = rackIndex,
                rack = rack,
                onDone = { setupMode = false },
                onBack = onBack,
            )

            // Live overlay. PHOTO needs no ARCore; MARKER / PLANE_ANCHOR are gated behind it.
            rack.arMode == ArMode.PHOTO -> ArPhotoView(
                rack = rack,
                onReconfigure = { setupMode = true },
                onBack = onBack,
            )

            else -> ArCoreGate(onBack = onBack) {
                ArMarkerLiveView(
                    rack = rack,
                    onReconfigure = { setupMode = true },
                    onBack = onBack,
                )
            }
        }
    }
}

/** Polls ARCore availability, re-checking on resume (e.g. after installing Play Services for AR). */
@Composable
private fun rememberArAvailability(): ArCoreApk.Availability? {
    val context = LocalContext.current
    var availability by remember { mutableStateOf<ArCoreApk.Availability?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refreshKey++ }
        lifecycleOwner?.lifecycle?.addObserver(obs)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(obs) }
    }
    LaunchedEffect(refreshKey) {
        var a = ArCoreApk.getInstance().checkAvailability(context)
        while (a.isTransient) {
            delay(200)
            a = ArCoreApk.getInstance().checkAvailability(context)
        }
        availability = a
    }
    return availability
}

/** Renders ARCore install / unsupported messages, showing [content] only when ARCore is ready. */
@Composable
private fun ArCoreGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val avail = rememberArAvailability()
    when {
        avail == null || avail.isTransient -> ArMessage(
            message = stringResource(Res.string.ar_searching),
            actionLabel = null,
            onAction = null,
            onBack = onBack,
        )

        avail == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED ||
            avail == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArMessage(
            message = stringResource(Res.string.ar_install),
            actionLabel = stringResource(Res.string.ar_install_action),
            onAction = {
                (context as? Activity)?.let { act ->
                    try {
                        ArCoreApk.getInstance().requestInstall(act, true)
                    } catch (_: Exception) {
                    }
                }
            },
            onBack = onBack,
        )

        avail != ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArMessage(
            message = stringResource(Res.string.ar_unsupported),
            actionLabel = null,
            onAction = null,
            onBack = onBack,
        )

        else -> content()
    }
}

/** Mode picker + the calibration flow for the selected mode. */
@Composable
private fun ArConfigure(rackIndex: Int, rack: Rack, onDone: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ArBackButton(onBack)
            Text(
                stringResource(Res.string.ar_mode_title),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.W800,
                modifier = Modifier.weight(1f),
            )
        }
        ArModeSelector(rack.arMode) { Racks.setArMode(rackIndex, it) }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (rack.arMode) {
                ArMode.PHOTO -> ArPhotoSetup(rackIndex, rack, onDone)
                ArMode.MARKER -> ArMarkerSetup(rackIndex, rack, onDone)
                ArMode.PLANE_ANCHOR -> ArCoreGate(onBack = onBack) {
                    ArAnchorSetup(rackIndex, rack, onDone)
                }
            }
        }
    }
}

@Composable
private fun ArModeSelector(current: ArMode, onSelect: (ArMode) -> Unit) {
    val options = listOf(
        ArMode.PHOTO to Res.string.ar_mode_photo,
        ArMode.MARKER to Res.string.ar_mode_marker,
        ArMode.PLANE_ANCHOR to Res.string.ar_mode_anchor,
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (mode, label) ->
            val selected = mode == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) VincentColors.Accent else VincentColors.Surface2)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.W800 else FontWeight.W600,
                )
            }
        }
    }
}

@Composable
private fun ArBackButton(onBack: () -> Unit) {
    Box(
        Modifier
            .padding(14.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text("\u2715", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.W700)
    }
}

@Composable
private fun ArMessage(
    message: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ArBackButton(onBack)
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                message,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent),
                ) { Text(actionLabel, fontWeight = FontWeight.W700) }
            }
        }
    }
}

@Composable
private fun ArHint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W600)
        }
    }
}

@Composable
private fun ArPhotoSetup(rackIndex: Int, rack: Rack, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val saver = rememberRackImageSaver()
    val density = LocalDensity.current
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    val takePhoto = rememberPhotoCapture { jpeg = it }
    val bytes = jpeg

    if (bytes == null) {
        CenterPrompt(
            message = stringResource(Res.string.ar_no_setup),
            actionLabel = stringResource(Res.string.ar_setup_start),
            onAction = { takePhoto() },
        )
        return
    }

    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    if (bitmap == null) {
        CenterPrompt(
            message = stringResource(Res.string.ar_retake),
            actionLabel = stringResource(Res.string.ar_setup_start),
            onAction = { jpeg = null; takePhoto() },
        )
        return
    }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()

    var corners by remember {
        mutableStateOf(
            listOf(
                NormPoint(0.15f, 0.15f),
                NormPoint(0.85f, 0.15f),
                NormPoint(0.85f, 0.85f),
                NormPoint(0.15f, 0.85f),
            ),
        )
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val handlePx = with(density) { 22.dp.toPx() }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            stringResource(Res.string.ar_setup_hint),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        Box(
            Modifier.fillMaxWidth().weight(1f).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(aspect).onSizeChanged { boxSize = it },
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                val w = boxSize.width.toFloat()
                val h = boxSize.height.toFloat()
                if (w > 0f && h > 0f) {
                    Canvas(Modifier.fillMaxSize()) {
                        val pts = corners.map { Offset(it.x * w, it.y * h) }
                        for (i in pts.indices) {
                            drawLine(Color(0xFFB5174E), pts[i], pts[(i + 1) % pts.size], strokeWidth = 4f)
                        }
                    }
                    corners.forEachIndexed { idx, c ->
                        Box(
                            Modifier
                                .offset {
                                    IntOffset(
                                        (c.x * w - handlePx / 2f).roundToInt(),
                                        (c.y * h - handlePx / 2f).roundToInt(),
                                    )
                                }
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB5174E))
                                .pointerInput(idx, w, h) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val nx = (corners[idx].x + drag.x / w).coerceIn(0f, 1f)
                                        val ny = (corners[idx].y + drag.y / h).coerceIn(0f, 1f)
                                        corners = corners.toMutableList().also { it[idx] = NormPoint(nx, ny) }
                                    }
                                },
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { jpeg = null; takePhoto() },
                colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Surface2),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(Res.string.ar_retake), color = Color.White) }
            Button(
                onClick = {
                    val data = bytes
                    scope.launch {
                        val path = saver.save(data, "$rackIndex-${rack.name}")
                        Racks.setArCalibration(rackIndex, path, RackArCalibration(corners))
                        onDone()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(Res.string.ar_setup_save), fontWeight = FontWeight.W700) }
        }
    }
}

/** MARKER / PLANE_ANCHOR live overlay: tracks the marker beacon and projects the grid onto it. */
@Composable
private fun ArMarkerLiveView(rack: Rack, onReconfigure: () -> Unit, onBack: () -> Unit) {
    val anchor = rack.arAnchor
    val markerBitmap = remember(anchor?.markerId) { anchor?.let { MarkerImages.bitmap(it.markerId) } }

    val holder = remember { ArFrameHolder() }
    var frameTick by remember { mutableIntStateOf(0) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var lowQuality by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        if (anchor != null && anchor.isValid && markerBitmap != null) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                planeRenderer = false,
                sessionConfiguration = { session, config ->
                    try {
                        val db = AugmentedImageDatabase(session)
                        db.addImage(anchor.markerId, markerBitmap, anchor.markerWidthMeters)
                        config.augmentedImageDatabase = db
                    } catch (_: Exception) {
                        lowQuality = true
                    }
                    config.focusMode = Config.FocusMode.AUTO
                    config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                },
                onSessionUpdated = { _, frame ->
                    val cam = frame.camera
                    if (cam.trackingState == TrackingState.TRACKING) {
                        cam.getViewMatrix(holder.view, 0)
                        cam.getProjectionMatrix(holder.proj, 0, 0.1f, 100f)
                        holder.image = frame.getUpdatedTrackables(AugmentedImage::class.java)
                            .firstOrNull { it.name == anchor.markerId && it.trackingState == TrackingState.TRACKING }
                        frameTick++
                    }
                },
            )

            val img = holder.image
            if (frameTick >= 0 && img != null && viewSize.width > 0 && viewSize.height > 0) {
                val gridFrame = ArProjection.gridFrame(img.centerPose, anchor)
                val positions = ArProjection.cellPositions(
                    gridFrame = gridFrame,
                    gridWidthMeters = anchor.gridWidthMeters,
                    gridHeightMeters = anchor.gridHeightMeters,
                    cols = rack.cols,
                    rows = rack.rows,
                    staggered = rack.staggered,
                    viewMatrix = holder.view,
                    projMatrix = holder.proj,
                    widthPx = viewSize.width,
                    heightPx = viewSize.height,
                )
                Canvas(Modifier.fillMaxSize()) {
                    positions.forEach { pos ->
                        if (pos.visible) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.45f),
                                radius = 4.dp.toPx(),
                                center = Offset(pos.x, pos.y),
                            )
                        }
                    }
                }
                positions.forEach { pos ->
                    val cell = rack.cells.getOrNull(pos.cellIndex)
                    if (pos.visible && cell != null && cell.occupied) {
                        CellChip(rack, pos.cellIndex, pos.x, pos.y)
                    }
                }
            } else {
                ArHint(stringResource(Res.string.ar_marker_searching))
            }
        }

        ArTopBar(onBack = onBack, onReconfigure = onReconfigure)
        if (lowQuality) ArHint(stringResource(Res.string.ar_low_quality))
    }
}

/** PHOTO mode: 2D overlay on the frozen reference photo, with pinch-zoom / pan and tappable chips. */
@Composable
private fun ArPhotoView(rack: Rack, onReconfigure: () -> Unit, onBack: () -> Unit) {
    val calibration = rack.arCalibration
    val bitmap = remember(rack.arImagePath) { rack.arImagePath?.let { BitmapFactory.decodeFile(it) } }

    if (bitmap == null || calibration == null || !calibration.isValid) {
        Box(Modifier.fillMaxSize()) {
            ArTopBar(onBack = onBack, onReconfigure = onReconfigure)
            ArHint(stringResource(Res.string.ar_searching))
        }
        return
    }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()

    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember { mutableIntStateOf(-1) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, drag, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        pan += drag
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = pan.x,
                        translationY = pan.y,
                    )
                    .onSizeChanged { boxSize = it },
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                val w = boxSize.width.toFloat()
                val h = boxSize.height.toFloat()
                if (w > 0f && h > 0f) {
                    for (row in 0 until rack.rows) {
                        for (col in 0 until rack.cols) {
                            val idx = row * rack.cols + col
                            val cell = rack.cells.getOrNull(idx) ?: continue
                            if (!cell.occupied) continue
                            val p = ArProjection.cellQuadPoint(calibration, col, row, rack.cols, rack.rows, rack.staggered)
                            CellChip(
                                rack = rack,
                                cellIndex = idx,
                                x = p.x * w,
                                y = p.y * h,
                                selected = selected == idx,
                                onClick = { selected = if (selected == idx) -1 else idx },
                            )
                        }
                    }
                }
            }
        }

        ArTopBar(onBack = onBack, onReconfigure = onReconfigure)
        ArHint(stringResource(Res.string.ar_photo_hint))
    }
}

@Composable
private fun ArTopBar(onBack: () -> Unit, onReconfigure: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArBackButton(onBack)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .padding(14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onReconfigure() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text(stringResource(Res.string.ar_setup_title), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W700) }
    }
}

/** Centered prompt (no back button) used inside [ArConfigure], which already provides navigation. */
@Composable
private fun CenterPrompt(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W600)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent),
        ) { Text(actionLabel, fontWeight = FontWeight.W700) }
    }
}

@Composable
private fun ArNumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = VincentColors.Accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
            focusedLabelColor = VincentColors.Accent,
            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
            cursorColor = VincentColors.Accent,
        ),
    )
}

/** MARKER setup: print the marker, enter its size and the rack dimensions / offset. */
@Composable
private fun ArMarkerSetup(rackIndex: Int, rack: Rack, onDone: () -> Unit) {
    val markerBitmap = remember(rack.id) { MarkerImages.bitmap(rack.id).asImageBitmap() }
    val existing = rack.arAnchor
    var markerW by remember { mutableStateOf(existing?.let { (it.markerWidthMeters * 100f).toCmString() } ?: "10") }
    var gridW by remember { mutableStateOf(existing?.let { (it.gridWidthMeters * 100f).toCmString() } ?: "") }
    var gridH by remember { mutableStateOf(existing?.let { (it.gridHeightMeters * 100f).toCmString() } ?: "") }
    var offX by remember { mutableStateOf(existing?.let { (it.tx * 100f).toCmString() } ?: "0") }
    var offY by remember { mutableStateOf(existing?.let { (it.tz * 100f).toCmString() } ?: "0") }
    var error by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            stringResource(Res.string.ar_marker_setup_title),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.W800,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.ar_marker_print),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = markerBitmap,
                contentDescription = null,
                modifier = Modifier.size(170.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(10.dp))
        ArNumberField(stringResource(Res.string.ar_marker_width), markerW) { markerW = it }
        ArNumberField(stringResource(Res.string.ar_grid_width), gridW) { gridW = it }
        ArNumberField(stringResource(Res.string.ar_grid_height), gridH) { gridH = it }
        ArNumberField(stringResource(Res.string.ar_offset_x), offX) { offX = it }
        ArNumberField(stringResource(Res.string.ar_offset_y), offY) { offY = it }
        if (error) {
            Text(
                stringResource(Res.string.ar_invalid_dimensions),
                color = VincentColors.Accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val mw = markerW.cmToMeters()
                val gw = gridW.cmToMeters()
                val gh = gridH.cmToMeters()
                val ox = offX.cmToMetersSigned()
                val oy = offY.cmToMetersSigned()
                if (mw == null || gw == null || gh == null || mw <= 0f || gw <= 0f || gh <= 0f) {
                    error = true
                } else {
                    Racks.setArAnchor(
                        rackIndex,
                        RackArAnchor(
                            markerId = rack.id,
                            markerWidthMeters = mw,
                            tx = ox, ty = 0f, tz = oy,
                            qx = 0f, qy = 0f, qz = 0f, qw = 1f,
                            gridWidthMeters = gw,
                            gridHeightMeters = gh,
                        ),
                    )
                    onDone()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.ar_setup_save), fontWeight = FontWeight.W700) }
    }
}

/** Per-frame state for the anchor placement, mutated in `onSessionUpdated`. */
private class AnchorSetupHolder {
    var pendingX = -1f
    var pendingY = -1f
    val points = ArrayList<FloatArray>(3)
    var markerPose: Pose? = null
    var markerVisible = false
}

/**
 * PLANE_ANCHOR setup: while the marker beacon is visible, the user taps the rack's top-left,
 * top-right and bottom-left corners on detected planes. The resulting grid frame is stored
 * relative to the marker so it re-localises across sessions.
 */
@Composable
private fun ArAnchorSetup(rackIndex: Int, rack: Rack, onDone: () -> Unit) {
    val markerBitmap = remember(rack.id) { MarkerImages.bitmap(rack.id) }
    var markerW by remember { mutableStateOf("10") }
    val markerWMeters = markerW.cmToMeters()?.takeIf { it > 0f } ?: 0.1f

    val holder = remember { AnchorSetupHolder() }
    var tick by remember { mutableIntStateOf(0) }
    var lowQuality by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = true,
            sessionConfiguration = { session, config ->
                try {
                    val db = AugmentedImageDatabase(session)
                    db.addImage(rack.id, markerBitmap, markerWMeters)
                    config.augmentedImageDatabase = db
                } catch (_: Exception) {
                    lowQuality = true
                }
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.focusMode = Config.FocusMode.AUTO
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            },
            onSessionUpdated = { _, frame ->
                val cam = frame.camera
                if (cam.trackingState == TrackingState.TRACKING) {
                    val mk = frame.getUpdatedTrackables(AugmentedImage::class.java)
                        .firstOrNull { it.name == rack.id && it.trackingState == TrackingState.TRACKING }
                    holder.markerVisible = mk != null
                    if (mk != null) holder.markerPose = mk.centerPose

                    if (holder.pendingX >= 0f && holder.points.size < 3) {
                        try {
                            val hit = frame.hitTest(holder.pendingX, holder.pendingY).firstOrNull()
                            if (hit != null) {
                                val p = hit.hitPose
                                holder.points.add(floatArrayOf(p.tx(), p.ty(), p.tz()))
                            }
                        } catch (_: Exception) {
                        }
                        holder.pendingX = -1f
                        holder.pendingY = -1f
                    }
                    tick++
                }
            },
        )

        // Tap capture overlay (same bounds as the AR view, so offsets map to hit-test pixels).
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { off -> holder.pendingX = off.x; holder.pendingY = off.y }
            },
        )

        val step = if (tick >= 0) holder.points.size else 0
        val instruction = when {
            !holder.markerVisible -> stringResource(Res.string.ar_anchor_need_marker)
            step == 0 -> stringResource(Res.string.ar_anchor_tap_origin)
            step == 1 -> stringResource(Res.string.ar_anchor_tap_width)
            step == 2 -> stringResource(Res.string.ar_anchor_tap_height)
            else -> stringResource(Res.string.ar_anchor_setup_title)
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(instruction, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W600) }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    ArNumberField(stringResource(Res.string.ar_marker_width), markerW) { markerW = it }
                }
                if (step > 0) {
                    Button(
                        onClick = { holder.points.clear(); tick++ },
                        colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Surface2),
                    ) { Text(stringResource(Res.string.ar_anchor_reset), color = Color.White, fontSize = 12.sp) }
                }
                if (step >= 3) {
                    Button(
                        onClick = {
                            val anchor = buildAnchorFromTaps(rack.id, markerWMeters, holder)
                            if (anchor != null) {
                                Racks.setArAnchor(rackIndex, anchor)
                                onDone()
                            } else {
                                holder.points.clear(); tick++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent),
                    ) { Text(stringResource(Res.string.ar_setup_save), fontWeight = FontWeight.W700) }
                }
            }
        }

        if (lowQuality) ArHint(stringResource(Res.string.ar_low_quality))
    }
}

private fun buildAnchorFromTaps(markerId: String, markerWidthMeters: Float, holder: AnchorSetupHolder): RackArAnchor? {
    val mk = holder.markerPose ?: return null
    if (holder.points.size < 3) return null
    val o = holder.points[0]
    val wEnd = holder.points[1]
    val hEnd = holder.points[2]
    val widthVec = vSub(wEnd, o)
    val heightVec = vSub(hEnd, o)
    val gw = vLen(widthVec)
    val gh = vLen(heightVec)
    if (gw < 0.02f || gh < 0.02f) return null

    val ex = vNorm(widthVec)
    val hProj = vSub(heightVec, vScale(ex, vDot(heightVec, ex)))
    val ez = vNorm(hProj)
    if (vLen(ez) < 1e-3f) return null
    val ey = vCross(ez, ex)

    val center = vAdd(o, vAdd(vScale(widthVec, 0.5f), vScale(heightVec, 0.5f)))
    val quat = quaternionFromAxes(ex, ey, ez)
    val anchorPose = Pose(center, quat)
    val relative = mk.inverse().compose(anchorPose)

    val rt = FloatArray(3)
    relative.getTranslation(rt, 0)
    val rq = FloatArray(4)
    relative.getRotationQuaternion(rq, 0)

    return RackArAnchor(
        markerId = markerId,
        markerWidthMeters = markerWidthMeters,
        tx = rt[0], ty = rt[1], tz = rt[2],
        qx = rq[0], qy = rq[1], qz = rq[2], qw = rq[3],
        gridWidthMeters = gw,
        gridHeightMeters = gh,
    )
}

private fun String.cmToMeters(): Float? = trim().replace(',', '.').toFloatOrNull()?.let { it / 100f }
private fun String.cmToMetersSigned(): Float = cmToMeters() ?: 0f
private fun Float.toCmString(): String = this.roundToInt().toString()

private fun vSub(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
private fun vAdd(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])
private fun vScale(a: FloatArray, s: Float) = floatArrayOf(a[0] * s, a[1] * s, a[2] * s)
private fun vDot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
private fun vLen(a: FloatArray) = sqrt(vDot(a, a))
private fun vNorm(a: FloatArray): FloatArray {
    val l = vLen(a)
    return if (l > 1e-6f) floatArrayOf(a[0] / l, a[1] / l, a[2] / l) else floatArrayOf(0f, 0f, 0f)
}
private fun vCross(a: FloatArray, b: FloatArray) = floatArrayOf(
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0],
)

/** Quaternion (x, y, z, w) from a rotation whose columns are the world directions of the local axes. */
private fun quaternionFromAxes(ex: FloatArray, ey: FloatArray, ez: FloatArray): FloatArray {
    val m00 = ex[0]; val m10 = ex[1]; val m20 = ex[2]
    val m01 = ey[0]; val m11 = ey[1]; val m21 = ey[2]
    val m02 = ez[0]; val m12 = ez[1]; val m22 = ez[2]
    val trace = m00 + m11 + m22
    return when {
        trace > 0f -> {
            val s = sqrt(trace + 1f) * 2f
            floatArrayOf((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s)
        }
        m00 > m11 && m00 > m22 -> {
            val s = sqrt(1f + m00 - m11 - m22) * 2f
            floatArrayOf(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
        }
        m11 > m22 -> {
            val s = sqrt(1f + m11 - m00 - m22) * 2f
            floatArrayOf((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s)
        }
        else -> {
            val s = sqrt(1f + m22 - m00 - m11) * 2f
            floatArrayOf((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s)
        }
    }
}

@Composable
private fun CellChip(
    rack: Rack,
    cellIndex: Int,
    x: Float,
    y: Float,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val cell = rack.cells.getOrNull(cellIndex) ?: return
    val spot = cellSpotLabel(cellIndex, rack.cols)
    val bottle = Cellar.bottles.firstOrNull { it.cellarSpot == spot }
    val name = bottle?.domain
    val vintage = cell.vintage ?: bottle?.vintage
    val price = cell.price ?: bottle?.price
    val color: WineColor? = cell.color ?: bottle?.color
    val chipColor = color?.glass ?: VincentColors.Accent

    val halfW = with(density) { 62.dp.toPx() }
    val halfH = with(density) { 24.dp.toPx() }

    Box(
        Modifier
            .offset { IntOffset((x - halfW).roundToInt(), (y - halfH).roundToInt()) }
            .width(124.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = if (selected) 0.85f else 0.68f))
            .border(if (selected) 2.5.dp else 1.5.dp, chipColor, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            fr.geoking.vincent.ui.WineBottle(
                color = color ?: WineColor.RED,
                modifier = Modifier.size(width = 18.dp, height = 28.dp),
                showLabel = true,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(spot, color = chipColor, fontSize = 11.sp, fontWeight = FontWeight.W900)
                if (name != null) {
                    Text(
                        name,
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.W600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val sub = buildString {
                    if (vintage != null) append(vintage)
                    if (price != null) {
                        if (isNotEmpty()) append(" \u00B7 ")
                        append("$price\u20AC")
                    }
                }
                if (sub.isNotEmpty()) {
                    Text(sub, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
                }
            }
        }
    }
}
