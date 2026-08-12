package fr.geoking.vincent.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The user's remaining AI allowance for the day, as reported by the proxy. */
data class AiQuota(val remaining: Int, val limit: Int)

/** Which recognition path produced the last label/voice result (cost KPIs). */
enum class AiPath {
    OCR_LOCAL,
    VOICE_LOCAL,
    OCR_TEXT_FALLBACK,
    VISION_FALLBACK,
    TEXT_FALLBACK,
}

/**
 * Holds the latest AI quota reported by the Cloudflare Worker (via the
 * X-AI-Quota-* response headers). Platform AI clients update it after each call;
 * screens observe [quota] to show "x scans left today". Null until the first call.
 *
 * Path counters track on-device vs Gemini usage for label/voice cost KPIs.
 */
object AiUsage {
    var quota: AiQuota? by mutableStateOf(null)
        private set

    var geminiVisionCalls: Int by mutableStateOf(0)
        private set
    var geminiTextCalls: Int by mutableStateOf(0)
        private set
    var localParseHits: Int by mutableStateOf(0)
        private set
    var fallbackCount: Int by mutableStateOf(0)
        private set
    var lastPath: AiPath? by mutableStateOf(null)
        private set

    fun update(remaining: Int, limit: Int) {
        if (remaining >= 0 && limit > 0) quota = AiQuota(remaining = remaining, limit = limit)
    }

    fun recordLocal(path: AiPath) {
        localParseHits += 1
        lastPath = path
    }

    fun recordGeminiVision() {
        geminiVisionCalls += 1
        fallbackCount += 1
        lastPath = AiPath.VISION_FALLBACK
    }

    fun recordGeminiText(path: AiPath = AiPath.TEXT_FALLBACK) {
        geminiTextCalls += 1
        fallbackCount += 1
        lastPath = path
    }

    /** Fraction of label/voice attempts that needed Gemini (0 when no attempts yet). */
    fun fallbackRate(): Float {
        val total = localParseHits + fallbackCount
        if (total <= 0) return 0f
        return fallbackCount.toFloat() / total.toFloat()
    }
}
