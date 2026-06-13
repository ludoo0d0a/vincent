package com.vincent.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A signed-in Google user (the fields Vincent actually displays). */
data class GoogleAccount(
    val name: String,
    val email: String,
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"
}

/** Observable auth state. `null` account = signed out (show the login screen). */
object Auth {
    var account by mutableStateOf<GoogleAccount?>(null)

    fun signOut() {
        account = null
    }
}
