package fr.geoking.vincent.debug

import fr.geoking.vincent.BuildConfig

actual fun initHttpDebug() {
    val isDebug = BuildConfig.DEBUG
    HttpDebug.enabled = isDebug
    HttpDebug.apiKeyHint = when {
        BuildConfig.GEMONI_API_KEY.isBlank() ->
            "gemoni_api_key: absente — ajoutez-la dans local.properties"
        !BuildConfig.GEMONI_API_KEY.startsWith("AIza") ->
            "gemoni_api_key: format suspect (attendu AIzaSy… depuis aistudio.google.com/apikey)"
        else ->
            "gemoni_api_key: présente (${BuildConfig.GEMONI_API_KEY.length} car.)"
    }
}
