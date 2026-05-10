package com.guiderun.app.accessibility

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.guiderun.app.R
import timber.log.Timber

/**
 * Wraps Android SpeechRecognizer for on-demand voice input.
 * Must be created and used on the main thread (Android requirement).
 * Lifecycle: create per Fragment, call destroy() in onDestroyView().
 *
 * 国产 ROM 多数未预装可识别服务，此时 [isAvailable] 返回 false，调用方需提前判断给出 fallback。
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (errorMessage: String) -> Unit = {},
    private val onStartListening: () -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!isAvailable) {
            onError(context.getString(R.string.voice_input_unavailable))
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onStartListening() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    Timber.w("SpeechRecognizer error: $error")
                    onError(humanize(error))
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onResult)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }
            )
        }
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun humanize(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.voice_input_error_network)
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.voice_input_error_no_match)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            context.getString(R.string.voice_input_permission_denied)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_SERVER -> context.getString(R.string.voice_input_error_busy)
        SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.voice_input_unavailable)
        else -> context.getString(R.string.voice_input_error_generic)
    }
}
