package com.geoking.vincent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.geoking.vincent.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

// WEB_CLIENT_ID (OAuth 2.0 *Web application* client, NOT the Android one) comes from
// local.properties (or CI env) via BuildConfig. Sign-in fails until it is set.

@Composable
actual fun rememberGoogleSignIn(onResult: (GoogleAccount?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val account = try {
                val credentialManager = CredentialManager.create(context)
                val option = GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).build()
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
