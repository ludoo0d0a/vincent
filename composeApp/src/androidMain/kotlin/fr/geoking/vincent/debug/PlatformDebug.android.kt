package fr.geoking.vincent.debug

import fr.geoking.vincent.BuildConfig

actual fun initHttpDebug() {
    val isDebug = BuildConfig.DEBUG
    HttpDebug.enabled = isDebug
    HttpDebug.apiKeyHint = when {
        BuildConfig.GEMINI_API_KEY.isBlank() ->
            "GEMINI_API_KEY: absente — ajoutez-la dans local.properties"
        else ->
            "GEMINI_API_KEY: présente (${BuildConfig.GEMINI_API_KEY.length} car.)"
    }
}
