package fr.geoking.vincent.ai

/** Lifecycle of the on-device Gemma weights (downloaded separately from the APK). */
sealed class GemmaModelState {
    data object Missing : GemmaModelState()
    data class Downloading(val progress: Float) : GemmaModelState()
    data object Ready : GemmaModelState()
    data class Error(val message: String) : GemmaModelState()
}

/**
 * On-device Gemma 3 1B (MediaPipe `.task`). Weights live in app filesDir —
 * download from Settings, never bundled in the APK.
 */
expect object GemmaModel {
    val state: GemmaModelState
    /** Absolute path to the `.task` file when [state] is [GemmaModelState.Ready], else null. */
    val modelPath: String?
    fun isReady(): Boolean
    suspend fun download()
    fun delete()
}
