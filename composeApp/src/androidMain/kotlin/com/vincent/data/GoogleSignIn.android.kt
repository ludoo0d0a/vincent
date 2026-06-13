package com.vincent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * OAuth 2.0 **Web application** client ID — NOT the Android client ID.
 * Create it in Google Cloud Console → APIs & Services → Credentials (or take the
 * `default_web_client_id` from a Firebase google-services.json). Sign-in fails
 * with this placeholder until it is set.
 */
private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

@Composable
actual fun rememberGoogleSignIn(onResult: (GoogleAccount?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val account = try {
                val credentialManager = CredentialManager.create(context)
                val option = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()
                val response = credentialManager.getCredential(context, request)
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data)
                    GoogleAccount(
                        name = token.displayName ?: token.givenName ?: token.id,
                        email = token.id,
                    )
                } else {
                    null
                }
            } catch (e: GetCredentialException) {
                null
            }
            onResult(account)
        }
    }
}
