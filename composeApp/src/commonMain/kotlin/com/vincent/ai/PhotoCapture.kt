package com.vincent.ai

import androidx.compose.runtime.Composable

/**
 * Returns a callback that captures a label photo and yields its JPEG bytes.
 *
 * Android uses the system camera (full-resolution) via `TakePicture` + a
 * FileProvider — no CameraX: for a one-shot label snap fed to Gemini, an in-app
 * CameraX preview/ImageCapture pipeline isn't worth the extra deps & code.
 */
@Composable
expect fun rememberPhotoCapture(onJpeg: (ByteArray) -> Unit): () -> Unit
