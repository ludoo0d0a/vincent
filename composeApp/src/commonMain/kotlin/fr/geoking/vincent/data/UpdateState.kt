package fr.geoking.vincent.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Observable state of an in-app (flexible) update download, surfaced to the UI as a
 * non-blocking overlay. Platform code (the Play update listener) feeds it; Compose reads it.
 */
object UpdateState {
    /** True while a flexible update is pending/downloading (or finishing before restart). */
    var downloading by mutableStateOf(false)
        private set

    /** Download progress in 0f..1f, or null when not yet known (indeterminate). */
    var progress by mutableStateOf<Float?>(null)
        private set

    fun onDownloading(progress: Float?) {
        this.progress = progress
        downloading = true
    }

    fun onIdle() {
        downloading = false
        progress = null
    }
}
