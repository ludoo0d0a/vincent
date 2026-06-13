package com.vincent.data

import androidx.compose.runtime.Composable

/**
 * Returns a callback that launches the platform Google sign-in flow and reports
 * the resulting [GoogleAccount] (or `null` on cancel/failure). The Android actual
 * uses Credential Manager + Sign in with Google; other targets can no-op.
 */
@Composable
expect fun rememberGoogleSignIn(onResult: (GoogleAccount?) -> Unit): () -> Unit
