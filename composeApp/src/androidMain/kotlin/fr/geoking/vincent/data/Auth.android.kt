package fr.geoking.vincent.data

import com.google.firebase.auth.FirebaseAuth
import fr.geoking.vincent.debug.InternalLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
private const val TAG = "VincentAuth"

/** Wires [Auth.account] to the Firebase Auth session (persisted across restarts). */
actual fun bootstrapAuth() {
    InternalLog.i(TAG, "Bootstrapping Auth state listener")
    FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        InternalLog.i(TAG, "Auth state changed: ${user?.email ?: "signed out"}")
        authScope.launch {
            Auth.account = user?.toGoogleAccount()
            cloudSyncOnAuthChanged(user?.uid)
        }
    }
}

actual fun platformSignOut() {
    FirebaseAuth.getInstance().signOut()
}

private fun com.google.firebase.auth.FirebaseUser.toGoogleAccount() = GoogleAccount(
    name = displayName ?: email?.substringBefore('@') ?: uid,
    email = email.orEmpty(),
    uid = uid,
)
