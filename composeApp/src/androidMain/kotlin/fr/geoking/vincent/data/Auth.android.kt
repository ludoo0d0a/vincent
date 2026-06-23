package fr.geoking.vincent.data

import com.google.firebase.auth.FirebaseAuth

/** Wires [Auth.account] to the Firebase Auth session (persisted across restarts). */
actual fun bootstrapAuth() {
    FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
        Auth.account = firebaseAuth.currentUser?.toGoogleAccount()
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
