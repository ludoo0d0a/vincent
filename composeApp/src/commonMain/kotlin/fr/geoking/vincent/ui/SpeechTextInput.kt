package fr.geoking.vincent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.vincent.ai.rememberDictation
import fr.geoking.vincent.theme.VincentColors

private val WaveformBars = listOf(18, 40, 66, 30, 78, 48, 80, 34, 60, 22, 50, 74, 28, 44, 16)

/** Where the mic trigger sits relative to the transcript box. */
enum class SpeechMicPlacement {
    /** Large mic button below the text area (default). */
    External,
    /** Compact mic icon inside the text area, aligned to the right. */
    Inline,
}

/**
 * Text field driven by [android.speech.SpeechRecognizer] (via [rememberDictation]):
 * live transcript, optional mic level waveform, and mic button. STT only — no AI parsing.
 *
 * @param showWaveform when true, shows animated bars driven by the mic level
 * @param onDictationEnd called when capture stops with a non-blank transcript
 */
@Composable
fun SpeechTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Touchez le micro et parlez…",
    statusIdle: String = "Dictée vocale",
    statusListening: String = "● Écoute…",
    title: String? = null,
    subtitle: String? = null,
    micPlacement: SpeechMicPlacement = SpeechMicPlacement.External,
    showWaveform: Boolean = true,
    onDictationEnd: (String) -> Unit = {},
    onListeningChange: (Boolean) -> Unit = {},
) {
    var listening by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(0f) }
    var lastTranscript by remember { mutableStateOf(value) }

    val startDictation = rememberDictation(
        onText = {
            lastTranscript = it
            onValueChange(it)
        },
        onLevel = { if (showWaveform) level = it },
        onListening = { active ->
            listening = active
            onListeningChange(active)
            if (!active && lastTranscript.isNotBlank()) {
                onDictationEnd(lastTranscript)
            }
        },
    )

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (listening) statusListening else statusIdle,
            color = VincentColors.Accent,
            fontWeight = FontWeight.W700,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Start),
        )
        if (title != null) {
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.W700, color = VincentColors.Fg)
        }
        if (subtitle != null) {
            Text(subtitle, fontSize = 12.sp, color = VincentColors.Muted, modifier = Modifier.padding(top = 3.dp))
        }

        if (showWaveform) {
            DictationWaveform(listening = listening, level = level)
        }

        when (micPlacement) {
            SpeechMicPlacement.External -> {
                TranscriptBox(
                    value = value,
                    placeholder = placeholder,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpeechMicButton(
                    listening = listening,
                    onClick = startDictation,
                    size = 64.dp,
                    iconSize = 28.dp,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            SpeechMicPlacement.Inline -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VincentColors.Surface)
                        .border(1.dp, VincentColors.Border, RoundedCornerShape(16.dp))
                        .padding(start = 15.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        value.ifBlank { placeholder },
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        color = if (value.isBlank()) VincentColors.Faint else VincentColors.Fg,
                        lineHeight = 22.sp,
                    )
                    SpeechMicButton(
                        listening = listening,
                        onClick = startDictation,
                        size = 40.dp,
                        iconSize = 22.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DictationWaveform(listening: Boolean, level: Float) {
    Row(
        Modifier.height(80.dp).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WaveformBars.forEach { base ->
            val h = (base * (0.35f + 0.65f * (if (listening) level else 0f))).coerceIn(6f, 80f)
            Box(
                Modifier
                    .width(5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(VincentColors.Accent.copy(alpha = if (listening) 0.9f else 0.4f)),
            )
        }
    }
}

@Composable
private fun TranscriptBox(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(VincentColors.Surface)
            .border(1.dp, VincentColors.Border, RoundedCornerShape(16.dp))
            .padding(15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.ifBlank { placeholder },
            fontSize = 15.sp,
            color = if (value.isBlank()) VincentColors.Faint else VincentColors.Fg,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun SpeechMicButton(
    listening: Boolean,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .then(modifier)
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(if (listening) VincentColors.Accent else VincentColors.AccentSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Parler",
            tint = if (listening) Color.White else VincentColors.Accent,
            modifier = Modifier.size(iconSize),
        )
    }
}
