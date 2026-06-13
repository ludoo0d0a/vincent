package com.vincent.ai

import androidx.compose.runtime.Composable

/**
 * Returns a callback that starts voice dictation. The Android actual uses the
 * platform [android.speech.SpeechRecognizer] (free, works offline on most devices)
 * in fr-FR, streaming partial transcripts and an audio level for the waveform.
 *
 * - [onText]      latest transcript (partial while speaking, then final)
 * - [onLevel]     normalised 0..1 microphone level (for the waveform)
 * - [onListening] true while capturing, false when stopped/failed
 */
@Composable
expect fun rememberDictation(
    onText: (String) -> Unit,
    onLevel: (Float) -> Unit,
    onListening: (Boolean) -> Unit,
): () -> Unit
