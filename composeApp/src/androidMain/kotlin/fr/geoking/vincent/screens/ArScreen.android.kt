package fr.geoking.vincent.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.Res
import vincent.composeapp.generated.resources.ar_install
import vincent.composeapp.generated.resources.ar_install_action
import vincent.composeapp.generated.resources.ar_marker_both_found
import vincent.composeapp.generated.resources.ar_marker_br
import vincent.composeapp.generated.resources.ar_marker_print_action
import vincent.composeapp.generated.resources.ar_marker_measured
import vincent.composeapp.generated.resources.ar_marker_point_br
import vincent.composeapp.generated.resources.ar_marker_point_tl
import vincent.composeapp.generated.resources.ar_marker_print
import vincent.composeapp.generated.resources.ar_marker_recorded
import vincent.composeapp.generated.resources.ar_marker_searching
import vincent.composeapp.generated.resources.ar_marker_setup_title
import vincent.composeapp.generated.resources.ar_marker_tl
import vincent.composeapp.generated.resources.ar_mode_marker
import vincent.composeapp.generated.resources.ar_mode_photo
import vincent.composeapp.generated.resources.ar_mode_title
import vincent.composeapp.generated.resources.ar_no_setup
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
import vincent.composeapp.generated.resources.cellar_spot_label
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import android.media.Image
import boofcv.struct.image.GrayU8
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import fr.geoking.vincent.ar.ArPoseBridge
import fr.geoking.vincent.ar.ArProjection
import fr.geoking.vincent.ar.BoofCvFiducials
import fr.geoking.vincent.ar.MarkerPrinter
import fr.geoking.vincent.ar.SquareMarkers
import fr.geoking.vincent.ar.imageYToGray
import fr.geoking.vincent.ai.rememberPhotoCapture
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import fr.geoking.vincent.ui.VCard
import fr.geoking.vincent.ui.WineBottle
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A marker seen this session: its decoded fiducial [id] and world [pose] (rack-face convention). */
private class MarkerWorldSighting(val id: Int, val pose: Pose)

/**
 * Detects the square-binary markers on ARCore camera frames without blocking the render thread.
 *
 * [submit] is called from `onSessionUpdated`: it cheaply copies the camera image's luminance plane,
 * the intrinsics and the world camera pose, closes the image, and (when not already busy) runs the
 * CPU-heavy BoofCV detection on a background dispatcher. Results are exposed in world space via
 * [sightings], refreshed once each detection completes.
 */
private class FiducialDetectionDriver(targetWidthMeters: Double) {
    private val fiducials = BoofCvFiducials(targetWidthMeters)
    private val gray = GrayU8(1, 1)
    private val busy = AtomicBoolean(false)

    @Volatile
    var sightings: List<MarkerWorldSighting> = emptyList()
        private set

    fun submit(frame: Frame, scope: CoroutineScope) {
        if (!busy.compareAndSet(false, true)) return
        val image: Image = try {
            frame.acquireCameraImage()
        } catch (_: Exception) {
            busy.set(false)
            return
        }
        val cameraPose: Pose
        val fx: Double
        val fy: Double
        val cx: Double
        val cy: Double
        try {
            val intr = frame.camera.imageIntrinsics
            val fl = intr.focalLength
            val pp = intr.principalPoint
            fx = fl[0].toDouble(); fy = fl[1].toDouble()
            cx = pp[0].toDouble(); cy = pp[1].toDouble()
            cameraPose = frame.camera.pose
            imageYToGray(image, gray)
        } catch (_: Exception) {
            busy.set(false)
            return
        } finally {
            image.close()
        }
        scope.launch(Dispatchers.Default) {
            try {
                sightings = fiducials.detect(gray, fx, fy, cx, cy)
                    .map { MarkerWorldSighting(it.id, ArPoseBridge.markerWorldPose(it, cameraPose)) }
            } catch (_: Exception) {
                // Keep the previous sightings; a transient detection failure shouldn't clear the grid.
            } finally {
                busy.set(false)
            }
        }
    }
}

/** Distinct highlight colours so each detected marker reads as its own zone. */
private val MarkerHighlightTl = Color(0xFF3B82F6) // top-left: blue
private val MarkerHighlightBr = Color(0xFFF59E0B) // bottom-right: orange

/** Outlines a marker's physical square (centred on [centerPose], side [widthMeters]) in [color]. */
private fun DrawScope.drawMarkerHighlight(
    centerPose: Pose,
    widthMeters: Float,
    view: FloatArray,
    proj: FloatArray,
    widthPx: Int,
    heightPx: Int,
    color: Color,
) {
    val pts = ArProjection.markerCorners(centerPose, widthMeters, widthMeters)
        .map { ArProjection.projectWorldPoint(it, view, proj, widthPx, heightPx) }
    if (pts.any { !it.visible }) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
    drawPath(path, color.copy(alpha = 0.28f))
    drawPath(path, color, style = Stroke(width = 5f))
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

            // Live overlay. PHOTO needs no ARCore; MARKERS is gated behind it.
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
                ArMode.MARKERS -> ArCoreGate(onBack = onBack) {
                    ArMarkerSetup(rackIndex, rack, onDone)
                }
            }
        }
    }
}

@Composable
private fun ArModeSelector(current: ArMode, onSelect: (ArMode) -> Unit) {
    val options = listOf(
        ArMode.PHOTO to Res.string.ar_mode_photo,
        ArMode.MARKERS to Res.string.ar_mode_marker,
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

/** Per-frame state for the live marker overlay, mutated in `onSessionUpdated` without reallocation. */
private class MarkerLiveHolder {
    val view = FloatArray(16)
    val proj = FloatArray(16)
    var arAnchor: Anchor? = null
    var tlMarkerPose: Pose? = null
    var brMarkerPose: Pose? = null
}

/**
 * MARKERS live overlay. BoofCV detects the two square-binary markers on the ARCore camera frames;
 * the top-left beacon establishes an ARCore world [Anchor] at the grid origin, so the grid stays
 * locked even when no marker is in view. The anchor is re-created automatically if ARCore loses it.
 */
@Composable
private fun ArMarkerLiveView(rack: Rack, onReconfigure: () -> Unit, onBack: () -> Unit) {
    val anchor = rack.arAnchor
    val scope = rememberCoroutineScope()
    val holder = remember { MarkerLiveHolder() }
    var frameTick by remember { mutableIntStateOf(0) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        if (anchor != null && anchor.isValid) {
            val driver = remember(anchor.markerWidthMeters) {
                FiducialDetectionDriver(anchor.markerWidthMeters.toDouble())
            }
            val sessionConfig = remember {
                { _: Session, config: Config ->
                    config.focusMode = Config.FocusMode.AUTO
                    config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                }
            }
            val onSessionUpdated = remember(anchor) {
                { session: Session, frame: Frame ->
                    val cam = frame.camera
                    if (cam.trackingState == TrackingState.TRACKING) {
                        cam.getViewMatrix(holder.view, 0)
                        cam.getProjectionMatrix(holder.proj, 0, 0.1f, 100f)
                        driver.submit(frame, scope)
                        val sights = driver.sightings
                        holder.tlMarkerPose = sights.firstOrNull { it.id == anchor.tlFiducialId }?.pose
                        holder.brMarkerPose = sights.firstOrNull { it.id == anchor.brFiducialId }?.pose
                        // (Re)establish the world anchor from the top-left beacon when needed.
                        val needsAnchor = holder.arAnchor?.trackingState != TrackingState.TRACKING
                        val tl = holder.tlMarkerPose
                        if (needsAnchor && tl != null) {
                            holder.arAnchor?.detach()
                            holder.arAnchor = runCatching {
                                session.createAnchor(ArProjection.gridFrame(tl, anchor))
                            }.getOrNull()
                        }
                        frameTick++
                    }
                }
            }
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                planeRenderer = false,
                sessionConfiguration = sessionConfig,
                onSessionUpdated = onSessionUpdated,
            )
            DisposableEffect(Unit) {
                onDispose { holder.arAnchor?.detach(); holder.arAnchor = null }
            }

            val gridPose = holder.arAnchor?.takeIf { it.trackingState == TrackingState.TRACKING }?.pose
            if (frameTick >= 0 && gridPose != null && viewSize.width > 0 && viewSize.height > 0) {
                val positions = ArProjection.cellPositions(
                    gridFrame = gridPose,
                    gridWidthMeters = anchor.gridWidthMeters,
                    gridHeightMeters = anchor.gridHeightMeters,
                    cols = rack.cols,
                    rows = rack.rows,
                    staggered = rack.staggered,
                    staggerOffset = rack.staggerOffset,
                    viewMatrix = holder.view,
                    projMatrix = holder.proj,
                    widthPx = viewSize.width,
                    heightPx = viewSize.height,
                )
                Canvas(Modifier.fillMaxSize()) {
                    holder.tlMarkerPose?.let {
                        drawMarkerHighlight(it, anchor.markerWidthMeters, holder.view, holder.proj, viewSize.width, viewSize.height, MarkerHighlightTl)
                    }
                    holder.brMarkerPose?.let {
                        drawMarkerHighlight(it, anchor.markerWidthMeters, holder.view, holder.proj, viewSize.width, viewSize.height, MarkerHighlightBr)
                    }
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
                        CellSquare(rack, pos.cellIndex, pos.x, pos.y)
                    }
                }
            } else {
                ArHint(stringResource(Res.string.ar_marker_searching))
            }
        }

        ArTopBar(onBack = onBack, onReconfigure = onReconfigure)
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
                            val p = ArProjection.cellQuadPoint(calibration, col, row, rack.cols, rack.rows, rack.staggered, rack.staggerOffset)
                            if (cell.occupied) {
                                CellSquare(
                                    rack = rack,
                                    cellIndex = idx,
                                    x = p.x * w,
                                    y = p.y * h,
                                    selected = selected == idx,
                                    onClick = { selected = if (selected == idx) -1 else idx },
                                )
                            } else {
                                // Thin ring marking a free slot, so the user can check the
                                // grid alignment against the real (empty) holes on the photo.
                                EmptySlotMarker(x = p.x * w, y = p.y * h)
                            }
                        }
                    }
                }
            }
        }

        ArTopBar(onBack = onBack, onReconfigure = onReconfigure)
        if (selected >= 0) {
            ArSelectedCard(
                rack = rack,
                cellIndex = selected,
                onDismiss = { selected = -1 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        } else {
            ArHint(stringResource(Res.string.ar_photo_hint))
        }
    }
}

/** Thin empty ring drawn over a free rack slot in PHOTO mode to verify grid alignment. */
@Composable
private fun EmptySlotMarker(x: Float, y: Float) {
    val density = LocalDensity.current
    val sizeDp = 22.dp
    val halfPx = with(density) { sizeDp.toPx() / 2f }
    Box(
        Modifier
            .offset { IntOffset((x - halfPx).roundToInt(), (y - halfPx).roundToInt()) }
            .size(sizeDp)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
    )
}

/** Detail card for the tapped bottle in PHOTO mode, mirroring the cellar screen's peek card. */
@Composable
private fun ArSelectedCard(
    rack: Rack,
    cellIndex: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cell = rack.cells.getOrNull(cellIndex) ?: return
    val spot = cellSpotLabel(cellIndex, rack.cols)
    val bottle = Cellar.bottles.firstOrNull { it.cellarSpot == spot }
    val categoryLabel = cell.category?.let { stringResource(it.label) }
    val colorLabel = cell.color?.let { stringResource(it.label) }
    val title = bottle?.domain ?: categoryLabel ?: colorLabel
        ?: stringResource(Res.string.cellar_spot_label, spot)
    val subtitle = buildString {
        append(spot)
        (bottle?.vintage ?: cell.vintage)?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        (bottle?.price ?: cell.price)?.let { append(" · $it €") }
    }
    VCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            WineBottle(
                cell.color ?: bottle?.color ?: WineColor.RED,
                Modifier.size(width = 26.dp, height = 40.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W800,
                    color = VincentColors.Fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = VincentColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "\u2715",
                    color = VincentColors.Muted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700,
                )
            }
        }
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

private const val MARKER_WIDTH_METERS = 0.05f

/** Per-frame state for the two-marker capture, mutated in `onSessionUpdated` without reallocation. */
private class ArMarkerSetupHolder {
    var tlPose: Pose? = null
    var brPose: Pose? = null
    val view = FloatArray(16)
    val proj = FloatArray(16)
}

@Composable
private fun MarkerStatusChip(label: String, recorded: Boolean) {
    val bg = if (recorded) Color(0xFF35C46B) else Color.Black.copy(alpha = 0.55f)
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            (if (recorded) "\u2713 " else "") + label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.W700,
        )
    }
}

@Composable
private fun MarkerPrintTile(label: String, bitmap: androidx.compose.ui.graphics.ImageBitmap, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W700)
        Spacer(Modifier.height(6.dp))
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Pre-capture screen: shows the two square-binary markers to print and a print action, then launches
 * the live camera so the user can sweep over the markers stuck on the rack.
 */
@Composable
private fun ArMarkerConfigure(
    generatedTl: Bitmap,
    generatedBr: Bitmap,
    tlLabel: String,
    brLabel: String,
    onPrint: () -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            stringResource(Res.string.ar_marker_setup_title),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.W800,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.ar_marker_print), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MarkerPrintTile(tlLabel, generatedTl.asImageBitmap(), Modifier.weight(1f))
            MarkerPrintTile(brLabel, generatedBr.asImageBitmap(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPrint,
            colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Surface2),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.ar_marker_print_action), color = Color.White, fontWeight = FontWeight.W700) }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.ar_setup_start), fontWeight = FontWeight.W700) }
    }
}

/**
 * MARKERS setup. The user prints the two square-binary markers, sticks them on the rack's top-left
 * and bottom-right corners, then sweeps the camera over them. BoofCV detects both markers on the
 * ARCore frames; their 3D positions give the rack's physical width/height (no measurement is typed).
 * Each detected marker's zone is highlighted in its own colour (top-left blue, bottom-right orange).
 */
@Composable
private fun ArMarkerSetup(rackIndex: Int, rack: Rack, onDone: () -> Unit) {
    val context = LocalContext.current
    val tlFidId = remember(rack.id) { SquareMarkers.tlId(rack.id) }
    val brFidId = remember(rack.id) { SquareMarkers.brId(rack.id) }
    val generatedTl = remember(tlFidId) { SquareMarkers.bitmap(tlFidId) }
    val generatedBr = remember(brFidId) { SquareMarkers.bitmap(brFidId) }

    val tlLabel = stringResource(Res.string.ar_marker_tl)
    val brLabel = stringResource(Res.string.ar_marker_br)
    val printJob = stringResource(Res.string.ar_marker_setup_title)

    var started by remember { mutableStateOf(false) }
    if (!started) {
        ArMarkerConfigure(
            generatedTl = generatedTl,
            generatedBr = generatedBr,
            tlLabel = tlLabel,
            brLabel = brLabel,
            onPrint = {
                MarkerPrinter.printMarkers(
                    context,
                    printJob,
                    listOf(tlLabel to generatedTl, brLabel to generatedBr),
                )
            },
            onStart = { started = true },
        )
        return
    }

    val scope = rememberCoroutineScope()
    val driver = remember { FiducialDetectionDriver(MARKER_WIDTH_METERS.toDouble()) }
    val holder = remember { ArMarkerSetupHolder() }
    var tick by remember { mutableIntStateOf(0) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    var tlRecorded by remember { mutableStateOf(false) }
    var brRecorded by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        val sessionConfig = remember {
            { _: Session, config: Config ->
                config.focusMode = Config.FocusMode.AUTO
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            }
        }
        val onSessionUpdated = remember {
            { _: Session, frame: Frame ->
                val cam = frame.camera
                if (cam.trackingState == TrackingState.TRACKING) {
                    cam.getViewMatrix(holder.view, 0)
                    cam.getProjectionMatrix(holder.proj, 0, 0.1f, 100f)
                    driver.submit(frame, scope)
                    val sights = driver.sightings
                    sights.firstOrNull { it.id == tlFidId }?.let { holder.tlPose = it.pose }
                    sights.firstOrNull { it.id == brFidId }?.let { holder.brPose = it.pose }
                    tick++
                }
            }
        }
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = false,
            sessionConfiguration = sessionConfig,
            onSessionUpdated = onSessionUpdated,
        )

        val tlPose = if (tick >= 0) holder.tlPose else null
        val brPose = if (tick >= 0) holder.brPose else null
        val tlSeen = tlPose != null
        val brSeen = brPose != null
        val measure = if (tlPose != null && brPose != null) measureFromMarkers(tlPose, brPose) else null

        val tlMsg = stringResource(Res.string.ar_marker_recorded, tlLabel)
        val brMsg = stringResource(Res.string.ar_marker_recorded, brLabel)
        LaunchedEffect(tlSeen) { if (tlSeen && !tlRecorded) { tlRecorded = true; toast = tlMsg } }
        LaunchedEffect(brSeen) { if (brSeen && !brRecorded) { brRecorded = true; toast = brMsg } }
        LaunchedEffect(toast) { if (toast != null) { delay(1800); toast = null } }

        if (viewSize.width > 0 && viewSize.height > 0) {
            Canvas(Modifier.fillMaxSize()) {
                // Spanned rack rectangle (neutral) for alignment feedback.
                if (measure != null) {
                    val pts = measure.corners.map {
                        ArProjection.projectWorldPoint(it, holder.view, holder.proj, viewSize.width, viewSize.height)
                    }
                    if (pts.all { it.visible }) {
                        val path = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                            close()
                        }
                        drawPath(path, Color.White.copy(alpha = 0.10f))
                        drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(width = 3f))
                    }
                }
                // Each detected marker's own zone, in a distinct colour.
                tlPose?.let {
                    drawMarkerHighlight(it, MARKER_WIDTH_METERS, holder.view, holder.proj, viewSize.width, viewSize.height, MarkerHighlightTl)
                }
                brPose?.let {
                    drawMarkerHighlight(it, MARKER_WIDTH_METERS, holder.view, holder.proj, viewSize.width, viewSize.height, MarkerHighlightBr)
                }
            }
        }

        val instruction = when {
            tlSeen && brSeen -> stringResource(Res.string.ar_marker_both_found)
            tlSeen -> stringResource(Res.string.ar_marker_point_br)
            else -> stringResource(Res.string.ar_marker_point_tl)
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(instruction, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W600) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarkerStatusChip(tlLabel, tlSeen)
                        MarkerStatusChip(brLabel, brSeen)
                    }
                    val t = toast
                    if (t != null) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF35C46B).copy(alpha = 0.92f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(t, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W700) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (measure != null) {
                    Text(
                        stringResource(
                            Res.string.ar_marker_measured,
                            (measure.widthM * 100f).roundToInt(),
                            (measure.heightM * 100f).roundToInt(),
                        ),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W700,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(
                    onClick = {
                        val tp = holder.tlPose
                        val bp = holder.brPose
                        if (tp != null && bp != null) {
                            val built = buildAnchorFromMarkers(rack.id, tlFidId, brFidId, tp, bp)
                            if (built != null) {
                                Racks.setArAnchor(rackIndex, built)
                                onDone()
                            }
                        }
                    },
                    enabled = measure != null,
                    colors = ButtonDefaults.buttonColors(containerColor = VincentColors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(Res.string.ar_setup_save), fontWeight = FontWeight.W700) }
            }
        }
    }
}

private class MarkerMeasure(val widthM: Float, val heightM: Float, val corners: List<FloatArray>)

/** Derives the rack rectangle from the two marker centre poses, in the top-left marker's plane. */
private fun measureFromMarkers(tlPose: Pose, brPose: Pose): MarkerMeasure? {
    val pTl = floatArrayOf(tlPose.tx(), tlPose.ty(), tlPose.tz())
    val pBr = floatArrayOf(brPose.tx(), brPose.ty(), brPose.tz())
    val ex = tlPose.axis(1f, 0f, 0f) // +X right across the rack face
    val ez = tlPose.axis(0f, 0f, 1f) // +Z down the rack face
    val d = vSub(pBr, pTl)
    val width = kotlin.math.abs(vDot(d, ex))
    val height = kotlin.math.abs(vDot(d, ez))
    if (width < 0.02f || height < 0.02f) return null
    val exW = vScale(ex, width)
    val ezH = vScale(ez, height)
    val corners = listOf(
        pTl,
        vAdd(pTl, exW),
        vAdd(vAdd(pTl, exW), ezH),
        vAdd(pTl, ezH),
    )
    return MarkerMeasure(width, height, corners)
}

/**
 * Builds the persistent anchor from the two markers: the grid frame is centred on the rectangle
 * and shares the top-left marker's orientation, stored relative to that marker (the live beacon).
 */
private fun buildAnchorFromMarkers(
    markerId: String,
    tlFiducialId: Int,
    brFiducialId: Int,
    tlPose: Pose,
    brPose: Pose,
): RackArAnchor? {
    val measure = measureFromMarkers(tlPose, brPose) ?: return null
    val center = vScale(vAdd(measure.corners[0], measure.corners[2]), 0.5f)
    val rq = FloatArray(4)
    tlPose.getRotationQuaternion(rq, 0)
    val gridCenterPose = Pose(center, rq)
    val relative = tlPose.inverse().compose(gridCenterPose)
    val rt = FloatArray(3); relative.getTranslation(rt, 0)
    val rrq = FloatArray(4); relative.getRotationQuaternion(rrq, 0)
    return RackArAnchor(
        markerId = markerId,
        markerWidthMeters = MARKER_WIDTH_METERS,
        tx = rt[0], ty = rt[1], tz = rt[2],
        qx = rrq[0], qy = rrq[1], qz = rrq[2], qw = rrq[3],
        gridWidthMeters = measure.widthM,
        gridHeightMeters = measure.heightM,
        tlFiducialId = tlFiducialId,
        brFiducialId = brFiducialId,
    )
}

private fun Pose.axis(x: Float, y: Float, z: Float): FloatArray {
    val out = FloatArray(3)
    rotateVector(floatArrayOf(x, y, z), 0, out, 0)
    return out
}

private fun vSub(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
private fun vAdd(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])
private fun vScale(a: FloatArray, s: Float) = floatArrayOf(a[0] * s, a[1] * s, a[2] * s)
private fun vDot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

/** PHOTO mode marker: a fixed-size square showing the cell's grid position (e.g. "A2", "D4"). */
@Composable
private fun CellSquare(
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
    val color: WineColor? = cell.color ?: bottle?.color
    val squareColor = color?.glass ?: VincentColors.Accent

    val sizeDp = 26.dp
    val halfPx = with(density) { sizeDp.toPx() / 2f }

    Box(
        Modifier
            .offset { IntOffset((x - halfPx).roundToInt(), (y - halfPx).roundToInt()) }
            .size(sizeDp)
            .clip(RoundedCornerShape(7.dp))
            .background(squareColor.copy(alpha = if (selected) 0.85f else 0.5f))
            .border(if (selected) 2.5.dp else 1.5.dp, squareColor, RoundedCornerShape(7.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            spot,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.W800,
            maxLines = 1,
        )
    }
}
