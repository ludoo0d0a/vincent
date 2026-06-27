package fr.geoking.vincent.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The user's remaining AI allowance for the day, as reported by the proxy. */
data class AiQuota(val remaining: Int, val limit: Int)

/**
 * Holds the latest AI quota reported by the Cloudflare Worker (via the
 * X-AI-Quota-* response headers). Platform AI clients update it after each call;
 * screens observe [quota] to show "x scans left today". Null until the first call.
 */
object AiUsage {
    var quota: AiQuota? by mutableStateOf(null)
        private set

    fun update(remaining: Int, limit: Int) {
        if (remaining >= 0 && limit > 0) quota = AiQuota(remaining = remaining, limit = limit)
    }
}
