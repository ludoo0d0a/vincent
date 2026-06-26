package fr.geoking.vincent.data

import androidx.compose.runtime.Composable

/**
 * Returns a callback that launches the platform Google sign-in flow and reports
 * the resulting [GoogleAccount] (or `null` on cancel/failure). The Android actual
 * uses Credential Manager + Firebase Authentication (Google provider).
 */
@Composable
expect fun rememberGoogleSignIn(
    onLoading: (Boolean) -> Unit = {},
    onError: (String) -> Unit,
    onResult: (GoogleAccount?) -> Unit,
): () -> Unit
