package com.geoking.vincent.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDictation(
    onText: (String) -> Unit,
    onLevel: (Float) -> Unit,
    onListening: (Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    // Keep callbacks fresh without recreating the recognizer.
    val text by rememberUpdatedState(onText)
    val level by rememberUpdatedState(onLevel)
    val listening by rememberUpdatedState(onListening)

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listening(true)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = listening(false)
            override fun onError(error: Int) = listening(false)
            override fun onPartialResults(partial: Bundle?) {
                partial?.firstTranscript()?.let(text)
            }
            override fun onResults(results: Bundle?) {
                results?.firstTranscript()?.let(text)
                listening(false)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { recognizer?.destroy() }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recognizer?.startListening(frenchIntent())
    }

    return {
        if (recognizer == null) {
            listening(false)
        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recognizer.startListening(frenchIntent())
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

private fun frenchIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
}

private fun Bundle.firstTranscript(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
