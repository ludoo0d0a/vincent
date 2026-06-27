package fr.geoking.vincent.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.debug.InternalLog
import fr.geoking.vincent.R
import fr.geoking.vincent.util.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import vincent.composeapp.generated.resources.Res
import vincent.composeapp.generated.resources.login_error_config
import vincent.composeapp.generated.resources.login_error_generic
import vincent.composeapp.generated.resources.login_error_network
import vincent.composeapp.generated.resources.login_error_no_account
import java.io.IOException

private const val TAG = "VincentSignIn"

// Credential Manager → Google ID token → Firebase Auth (users appear in Firebase Console).
// Needs composeApp/google-services.json + Google provider enabled in Firebase Authentication.
// WEB_CLIENT_ID (local.properties / CI) is a fallback when default_web_client_id is absent.

private fun resolveWebClientId(context: Context): String {
    val fromFirebase = runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
    return fromFirebase.takeIf { it.isNotBlank() } ?: BuildConfig.WEB_CLIENT_ID
}

/** Maps a sign-in failure to a short, localized, user-facing message. */
private suspend fun friendlyError(error: Throwable): String = when {
    // No usable Google account on the device (One-Tap and the explicit flow both came up empty).
    error is NoCredentialException -> getString(Res.string.login_error_no_account)
    // Offline / Firebase couldn't reach the network to exchange the token.
    error is FirebaseNetworkException || error is IOException || error.cause is IOException ->
        getString(Res.string.login_error_network)
    else -> getString(Res.string.login_error_generic)
}

private suspend fun firebaseAuthWithGoogle(idToken: String): GoogleAccount? = withContext(Dispatchers.IO) {
    InternalLog.i(TAG, "Authenticating with Firebase using Google ID token")
    val auth = FirebaseAuth.getInstance()
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    try {
        val result = auth.signInWithCredential(credential).await()
        val user = result.user
        if (user != null) {
            InternalLog.i(TAG, "Firebase Auth successful: ${user.email}")
            GoogleAccount(
                name = user.displayName ?: user.email?.substringBefore('@') ?: user.uid,
                email = user.email.orEmpty(),
                uid = user.uid,
            )
        } else {
            InternalLog.e(TAG, "Firebase Auth successful but currentUser is null")
            null
        }
    } catch (e: Exception) {
        InternalLog.e(TAG, "Firebase Auth failed", e)
        throw e
    }
}

private suspend fun requestGoogleIdToken(
    context: Context,
    credentialManager: CredentialManager,
    webClientId: String,
    oneTap: Boolean,
): String? {
    InternalLog.i(TAG, "requestGoogleIdToken(oneTap=$oneTap)")
    val option = if (oneTap) {
        GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(true)
            // Returning users (single previously-used account) are signed in without a tap.
            .setAutoSelectEnabled(true)
            .build()
    } else {
        GetSignInWithGoogleOption.Builder(webClientId).build()
    }
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    // Use Activity context if available for CredentialManager to show UI
    val activityContext = context.findActivity() ?: context
    if (activityContext != context) {
        InternalLog.i(TAG, "Using Activity context for CredentialManager")
    }

    val response = credentialManager.getCredential(activityContext, request)
    val credential = response.credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        InternalLog.i(TAG, "Google ID token received (length=${idToken.length})")
        return idToken
    }
    val error = "Unexpected credential type: ${credential.type}"
    Log.w(TAG, error)
    InternalLog.w(TAG, error)
    return null
}

@Composable
actual fun rememberGoogleSignIn(
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onResult: (GoogleAccount?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            InternalLog.i(TAG, "Starting Google Sign-in flow")
            withContext(Dispatchers.Main) { onLoading(true) }
            try {
                val webClientId = resolveWebClientId(context)
                if (webClientId.isBlank()) {
                    val devError = "Web client ID missing — add google-services.json or WEB_CLIENT_ID in local.properties."
                    Log.e(TAG, devError)
                    InternalLog.e(TAG, devError)
                    val userError = getString(Res.string.login_error_config)
                    withContext(Dispatchers.Main) {
                        onError(userError)
                        onResult(null)
                    }
                    return@launch
                }
                val account = try {
                    val credentialManager = CredentialManager.create(context)
                    val idToken = try {
                        requestGoogleIdToken(context, credentialManager, webClientId, oneTap = true)
                    } catch (_: NoCredentialException) {
                        InternalLog.i(TAG, "OneTap failed (NoCredential), requesting Google ID token (oneTap = false)")
                        requestGoogleIdToken(context, credentialManager, webClientId, oneTap = false)
                    }
                    if (idToken != null) {
                        firebaseAuthWithGoogle(idToken)
                    } else {
                        InternalLog.w(TAG, "Google login failed: no ID token received")
                        val userError = getString(Res.string.login_error_generic)
                        withContext(Dispatchers.Main) { onError(userError) }
                        null
                    }
                } catch (e: GetCredentialCancellationException) {
                    InternalLog.i(TAG, "Sign-in cancelled by user (CredentialManager)")
                    null
                } catch (e: Exception) {
                    val devError = "Firebase Google sign-in failed: ${e.javaClass.simpleName}: ${e.message}"
                    Log.e(TAG, devError, e)
                    InternalLog.e(TAG, devError, e)
                    val userError = friendlyError(e)
                    withContext(Dispatchers.Main) { onError(userError) }
                    null
                }
                if (account != null) {
                    InternalLog.i(TAG, "Sign-in successful: ${account.email}")
                }
                withContext(Dispatchers.Main) { onResult(account) }
            } catch (e: CancellationException) {
                InternalLog.i(TAG, "Sign-in coroutine cancelled")
                throw e
            } finally {
                withContext(Dispatchers.Main) { onLoading(false) }
            }
        }
    }
}
