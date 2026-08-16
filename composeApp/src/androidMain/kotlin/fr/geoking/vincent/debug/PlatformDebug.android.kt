package fr.geoking.vincent.debug

import fr.geoking.vincent.BuildConfig

actual fun initHttpDebug() {
    val isDebug = BuildConfig.DEBUG
    HttpDebug.enabled = isDebug
    HttpDebug.apiKeyHint = when {
        fr.geoking.vincent.data.Settings.geminiApiKey.isNotBlank() ->
            "Gemini BYOK: clé présente (${fr.geoking.vincent.data.Settings.geminiApiKey.length} car.)"
        BuildConfig.GEMINI_API_KEY.isNotBlank() ->
            "GEMINI_API_KEY build: présente (non utilisée — préférez Réglages)"
        else ->
            "Gemini: aucune clé (Gemma local ou Réglages → clé utilisateur)"
    }
}
