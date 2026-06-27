package fr.geoking.vincent.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.Res
import vincent.composeapp.generated.resources.ar_install
import vincent.composeapp.generated.resources.ar_install_action
import vincent.composeapp.generated.resources.ar_low_quality
import vincent.composeapp.generated.resources.ar_no_setup
import vincent.composeapp.generated.resources.ar_permission
import vincent.composeapp.generated.resources.ar_permission_action
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import fr.geoking.vincent.ar.ArProjection
import fr.geoking.vincent.ai.rememberPhotoCapture
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.rememberRackImageSaver
import fr.geoking.vincent.model.NormPoint
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.RackArCalibration
import fr.geoking.vincent.model.WineColor
import fr.geoking.vincent.model.cellSpotLabel
import fr.geoking.vincent.theme.VincentColors
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    var availability by remember { mutableStateOf<ArCoreApk.Availability?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Re-check ARCore availability on resume (e.g. after the user installs Play Services for AR).
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refreshKey++ }
        lifecycleOwner?.lifecycle?.addObserver(obs)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(obs) }
    }

    LaunchedEffect(cameraGranted, refreshKey) {
        if (!cameraGranted) return@LaunchedEffect
        var a = ArCoreApk.getInstance().checkAvailability(context)
        while (a.isTransient) {
            delay(200)
            a = ArCoreApk.getInstance().checkAvailability(context)
        }
        availability = a
    }

    var setupMode by remember { mutableStateOf(false) }
    val avail = availability

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !cameraGranted -> ArMessage(
                message = stringResource(Res.string.ar_permission),
                actionLabel = stringResource(Res.string.ar_permission_action),
                onAction = { permLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack,
            )

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

            setupMode || !rack.arReady -> ArSetup(
                rackIndex = rackIndex,
                rack = rack,
                onDone = { setupMode = false },
                onBack = onBack,
            )

            else -> ArLiveView(
                rack = rack,
                onReconfigure = { setupMode = true },
                onBack = onBack,
            )
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
private fun ArSetup(rackIndex: Int, rack: Rack, onDone: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val saver = rememberRackImageSaver()
    val density = LocalDensity.current
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    val takePhoto = rememberPhotoCapture { jpeg = it }
    val bytes = jpeg

    if (bytes == null) {
        ArMessage(
            message = stringResource(Res.string.ar_no_setup),
            actionLabel = stringResource(Res.string.ar_setup_start),
            onAction = { takePhoto() },
            onBack = onBack,
        )
        return
    }

    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    if (bitmap == null) {
        ArMessage(
            message = stringResource(Res.string.ar_retake),
            actionLabel = stringResource(Res.string.ar_setup_start),
            onAction = { jpeg = null; takePhoto() },
            onBack = onBack,
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ArBackButton(onBack)
            Text(
                stringResource(Res.string.ar_setup_title),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.W800,
                modifier = Modifier.weight(1f),
            )
        }
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

@Composable
private fun ArLiveView(rack: Rack, onReconfigure: () -> Unit, onBack: () -> Unit) {
    val calibration = rack.arCalibration
    val refBitmap = remember(rack.arImagePath) { rack.arImagePath?.let { BitmapFactory.decodeFile(it) } }

    val holder = remember { ArFrameHolder() }
    var frameTick by remember { mutableIntStateOf(0) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var lowQuality by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        if (refBitmap != null && calibration != null && calibration.isValid) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                planeRenderer = false,
                sessionConfiguration = { session, config ->
                    try {
                        val db = AugmentedImageDatabase(session)
                        db.addImage("rack", refBitmap)
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
                            .firstOrNull { it.name == "rack" && it.trackingState == TrackingState.TRACKING }
                        frameTick++
                    }
                },
            )

            val img = holder.image
            if (frameTick >= 0 && img != null && viewSize.width > 0 && viewSize.height > 0) {
                val positions = ArProjection.cellPositions(
                    image = img,
                    calibration = calibration,
                    cols = rack.cols,
                    rows = rack.rows,
                    staggered = rack.staggered,
                    viewMatrix = holder.view,
                    projMatrix = holder.proj,
                    widthPx = viewSize.width,
                    heightPx = viewSize.height,
                )
                // Draw matrix: small dots for all visible cells.
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
                ArHint(stringResource(Res.string.ar_searching))
            }
        }

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
            ) { Text(stringResource(Res.string.ar_retake), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W700) }
        }

        if (lowQuality) ArHint(stringResource(Res.string.ar_low_quality))
    }
}

@Composable
private fun CellChip(rack: Rack, cellIndex: Int, x: Float, y: Float) {
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
            .background(Color.Black.copy(alpha = 0.68f))
            .border(1.5.dp, chipColor, RoundedCornerShape(10.dp))
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
