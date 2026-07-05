package fr.geoking.vincent.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberCsvImport(onLoading: (Boolean) -> Unit, onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                onLoading(true)
                try {
                    val text = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                        try {
                            // Strict UTF-8 decoding to catch encoding issues
                            java.nio.charset.Charset.forName("UTF-8").newDecoder()
                                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                        } catch (e: Exception) {
                            // Fallback to Windows-1252 (common for French Excel CSV exports)
                            String(bytes, java.nio.charset.Charset.forName("Windows-1252"))
                        }
                    }
                    if (text != null) onText(text)
                } finally {
                    onLoading(false)
                }
            }
        }
    }
    return { launcher.launch("text/*") }
}

@Composable
actual fun rememberCsvExport(
    filename: String,
    content: () -> String,
    onResult: (Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) {
            onResult(false)
        } else {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(content().encodeToByteArray()) }
                    }.getOrNull() != null
                }
                onResult(ok)
            }
        }
    }
    return { launcher.launch(filename) }
}

@Composable
actual fun rememberVincentImport(onLoading: (Boolean) -> Unit, onBytes: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                onLoading(true)
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes != null) onBytes(bytes)
                } finally {
                    onLoading(false)
                }
            }
        }
    }
    return { launcher.launch("*/*") }
}

@Composable
actual fun rememberVincentExport(
    includePhotos: Boolean,
    content: suspend () -> ByteArray,
    onResult: (Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val date = java.time.LocalDate.now().toString()
    val filename = if (includePhotos) "vincent-backup-$date.vincent" else "vincent-backup-$date.json"
    val mime = if (includePhotos) "application/zip" else "application/json"
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mime),
    ) { uri ->
        if (uri == null) {
            onResult(false)
        } else {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = content()
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    }.getOrNull() != null
                }
                onResult(ok)
            }
        }
    }
    return { launcher.launch(filename) }
}
