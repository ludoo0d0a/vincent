package fr.geoking.vincent.data

import fr.geoking.vincent.BuildConfig

actual object Features {
    actual val arEnabled: Boolean get() = BuildConfig.AR_ENABLED
    actual val cloudSync: Boolean get() = BuildConfig.CLOUD_SYNC
}
