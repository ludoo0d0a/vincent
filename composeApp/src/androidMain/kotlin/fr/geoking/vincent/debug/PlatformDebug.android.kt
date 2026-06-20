package fr.geoking.vincent.debug

import fr.geoking.vincent.BuildConfig

actual fun initHttpDebug() {
    HttpDebug.enabled = BuildConfig.DEBUG
    HttpDebug.apiKeyHint = when {
        BuildConfig.GEMINI_API_KEY.isBlank() ->
            "GEMINI_API_KEY: absente — ajoutez-la dans local.properties"
        !BuildConfig.GEMINI_API_KEY.startsWith("AIza") ->
            "GEMINI_API_KEY: format suspect (attendu AIzaSy… depuis aistudio.google.com/apikey)"
        else ->
            "GEMINI_API_KEY: présente (${BuildConfig.GEMINI_API_KEY.length} car.)"
    }
}
