package fr.geoking.vincent.data

import fr.geoking.vincent.BuildConfig

actual object Features {
    actual val arEnabled: Boolean get() = BuildConfig.AR_ENABLED
}
