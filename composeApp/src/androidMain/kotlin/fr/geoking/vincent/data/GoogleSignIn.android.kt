package fr.geoking.vincent.data

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import fr.geoking.vincent.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "VincentSignIn"

// WEB_CLIENT_ID (OAuth 2.0 *Web application* client, NOT the Android one) comes from
// local.properties (or CI env) via BuildConfig. Sign-in also needs an Android OAuth
// client in the SAME project, registered with this app's package + signing SHA-1.

@Composable
actual fun rememberGoogleSignIn(onResult: (GoogleAccount?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            if (BuildConfig.WEB_CLIENT_ID.isBlank()) {
                Log.e(TAG, "WEB_CLIENT_ID is blank — set it in local.properties (OAuth Web client ID).")
                onResult(null)
                return@launch
            }
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
                    Log.w(TAG, "Unexpected credential type: ${credential.type}")
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surfaces the real cause (e.g. "[16] Caller not whitelisted" → the Android
                // OAuth client is missing/mismatched for this package + signing SHA-1).
                Log.e(TAG, "Google sign-in failed: ${e.javaClass.simpleName}: ${e.message}", e)
                null
            }
            onResult(account)
        }
    }
}
