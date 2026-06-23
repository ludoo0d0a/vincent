package fr.geoking.vincent.data

/** Android: attaches a Firebase Auth state listener. Other targets: no-op. */
expect fun bootstrapAuth()

/** Android: signs out of Firebase. Other targets: no-op. */
expect fun platformSignOut()
