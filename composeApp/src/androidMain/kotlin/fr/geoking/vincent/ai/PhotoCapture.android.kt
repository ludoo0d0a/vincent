package fr.geoking.vincent.ai

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberPhotoCapture(onJpeg: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onJpeg)
    var target by remember { mutableStateOf<Uri?>(null) }
    var file by remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) file?.let { callback(it.readBytes()) }
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) target?.let { takePicture.launch(it) }
    }

    fun prepareUri(): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val f = File(dir, "label.jpg")
        file = f
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        target = uri
        return uri
    }

    return {
        val uri = prepareUri()
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePicture.launch(uri)
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }
}
