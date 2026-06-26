package fr.geoking.vincent.ar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Drives screen recording of the AR view (camera + overlay) via [ScreenRecordService].
 * Call [start] to request the capture permission then begin; [stop] to finish.
 */
class ScreenRecorder internal constructor(private val context: Context) {

    var isRecording by mutableStateOf(false)
        private set

    var lastOutputPath: String? = null
        private set

    private var launcher: ActivityResultLauncher<Intent>? = null

    internal fun attach(l: ActivityResultLauncher<Intent>) { launcher = l }

    fun start() {
        if (isRecording) return
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher?.launch(mgr.createScreenCaptureIntent())
    }

    internal fun onPermissionGranted(resultCode: Int, data: Intent) {
        val metrics = context.resources.displayMetrics
        val dir = (context.getExternalFilesDir("ar-recordings") ?: File(context.filesDir, "ar-recordings")).apply { mkdirs() }
        val output = File(dir, "ar-${System.currentTimeMillis()}.mp4").absolutePath
        lastOutputPath = output

        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenRecordService.EXTRA_WIDTH, metrics.widthPixels)
            putExtra(ScreenRecordService.EXTRA_HEIGHT, metrics.heightPixels)
            putExtra(ScreenRecordService.EXTRA_DENSITY, metrics.densityDpi)
            putExtra(ScreenRecordService.EXTRA_OUTPUT, output)
        }
        ContextCompat.startForegroundService(context, intent)
        isRecording = true
    }

    fun stop() {
        if (!isRecording) return
        val intent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        context.startService(intent)
        isRecording = false
    }
}

@Composable
fun rememberScreenRecorder(): ScreenRecorder {
    val context = LocalContext.current
    val recorder = remember { ScreenRecorder(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            recorder.onPermissionGranted(result.resultCode, data)
        }
    }
    recorder.attach(launcher)
    return recorder
}
