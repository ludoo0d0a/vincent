package fr.geoking.vincent.ai

import androidx.compose.runtime.Composable

/**
 * Returns a callback that launches the platform barcode scanner and reports the
 * decoded value (EAN-13 / EAN-8 / UPC) — or `null` on cancel/failure. The Android
 * actual uses Google Code Scanner (Play Services, no camera permission needed).
 * Other targets can no-op.
 */
@Composable
expect fun rememberBarcodeScanner(onResult: (String?) -> Unit): () -> Unit
