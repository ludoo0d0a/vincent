package fr.geoking.vincent.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import fr.geoking.vincent.BuildConfig
import fr.geoking.vincent.debug.InternalLog
import fr.geoking.vincent.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "VincentSignIn"

// Credential Manager → Google ID token → Firebase Auth (users appear in Firebase Console).
// Needs composeApp/google-services.json + Google provider enabled in Firebase Authentication.
// WEB_CLIENT_ID (local.properties / CI) is a fallback when default_web_client_id is absent.

private fun resolveWebClientId(context: Context): String {
    val fromFirebase = runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
    return fromFirebase.takeIf { it.isNotBlank() } ?: BuildConfig.WEB_CLIENT_ID
}

private suspend fun firebaseAuthWithGoogle(idToken: String): GoogleAccount? {
    val auth = FirebaseAuth.getInstance()
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(credential).await()
    return auth.currentUser?.let { user ->
        GoogleAccount(
            name = user.displayName ?: user.email?.substringBefore('@') ?: user.uid,
            email = user.email.orEmpty(),
            uid = user.uid,
        )
    }
}

private suspend fun requestGoogleIdToken(
    context: Context,
    credentialManager: CredentialManager,
    webClientId: String,
    oneTap: Boolean,
): String? {
    val option = if (oneTap) {
        GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(true)
            .build()
    } else {
        GetSignInWithGoogleOption.Builder(webClientId).build()
    }
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val response = credentialManager.getCredential(context, request)
    val credential = response.credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    Log.w(TAG, "Unexpected credential type: ${credential.type}")
    return null
}

@Composable
actual fun rememberGoogleSignIn(
    onError: (String) -> Unit,
    onResult: (GoogleAccount?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            InternalLog.i(TAG, "Starting Google Sign-in flow")
            val webClientId = resolveWebClientId(context)
            if (webClientId.isBlank()) {
                val error = "Web client ID missing — add google-services.json or WEB_CLIENT_ID in local.properties."
                Log.e(TAG, error)
                InternalLog.e(TAG, error)
                onError(error)
                onResult(null)
                return@launch
            }
            val account = try {
                val credentialManager = CredentialManager.create(context)
                val idToken = try {
                    InternalLog.i(TAG, "Requesting Google ID token (oneTap = true)")
                    requestGoogleIdToken(context, credentialManager, webClientId, oneTap = true)
                } catch (_: NoCredentialException) {
                    InternalLog.i(TAG, "OneTap failed, requesting Google ID token (oneTap = false)")
                    requestGoogleIdToken(context, credentialManager, webClientId, oneTap = false)
                }
                if (idToken != null) {
                    InternalLog.i(TAG, "ID token received, authenticating with Firebase")
                    firebaseAuthWithGoogle(idToken)
                } else {
                    InternalLog.w(TAG, "No ID token received")
                    null
                }
            } catch (e: CancellationException) {
                InternalLog.i(TAG, "Sign-in cancelled by user")
                throw e
            } catch (e: Exception) {
                val errorMsg = "Firebase Google sign-in failed: ${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, errorMsg, e)
                InternalLog.e(TAG, errorMsg, e)
                onError(e.message ?: e.javaClass.simpleName)
                null
            }
            if (account != null) {
                InternalLog.i(TAG, "Sign-in successful: ${account.email}")
            }
            onResult(account)
        }
    }
}
