package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.model.JarvisLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class VoiceEngineState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isMuted: Boolean = false,
    val partialTranscript: String = "",
    val audioLevel: Float = 0f,
    val speechRecognitionAvailable: Boolean = true,
    val ttsReady: Boolean = false,
    val statusMessage: String = "Voice Guardian ready",
    val errorMessage: String? = null
)

class VoiceGuardianEngine(
    context: Context,
    private val onFinalTranscript: (transcript: String, wakeTriggered: Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(
        VoiceEngineState(
            speechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        )
    )
    val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var currentLanguage: JarvisLanguage = JarvisLanguage.ENGLISH
    private var speechRate: Float = 1.0f

    init {
        tts = TextToSpeech(appContext) { status ->
            val ready = status == TextToSpeech.SUCCESS
            if (ready) {
                applyTtsSettings()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            isSpeaking = true,
                            statusMessage = "Jarvis speaking..."
                        )
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            isSpeaking = false,
                            audioLevel = 0f,
                            statusMessage = "Voice Guardian ready"
                        )
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            isSpeaking = false,
                            audioLevel = 0f
                        )
                    }
                })
            }
            _state.value = _state.value.copy(ttsReady = ready)
        }
    }

    fun updateSettings(language: JarvisLanguage, rate: Float, muted: Boolean) {
        currentLanguage = language
        speechRate = rate.coerceIn(0.5f, 2.0f)
        _state.value = _state.value.copy(isMuted = muted)
        if (muted) {
            stopSpeaking()
        }
        applyTtsSettings()
    }

    private fun applyTtsSettings() {
        val locale = Locale.forLanguageTag(currentLanguage.bcp47Tag)
        tts?.language = locale
        tts?.setSpeechRate(speechRate)
    }

    fun startListening(language: JarvisLanguage = currentLanguage) {
        currentLanguage = language
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = _state.value.copy(
                speechRecognitionAvailable = false,
                isListening = false,
                errorMessage = "On-device SpeechRecognizer service is unavailable on this device. Use text input below."
            )
            return
        }

        stopSpeaking()
        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(
                        isListening = true,
                        partialTranscript = "",
                        errorMessage = null,
                        statusMessage = "Listening (${language.displayName})... Say \"Hello Jarvis\" or your command"
                    )
                }

                override fun onBeginningOfSpeech() {
                    _state.value = _state.value.copy(statusMessage = "Capturing voice command...")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    _state.value = _state.value.copy(audioLevel = normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(
                        isListening = false,
                        audioLevel = 0.1f,
                        statusMessage = "Processing transcript..."
                    )
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech service network timeout. Check connectivity or type below."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched. Tap the orb and speak clearly."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech engine busy. Try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                        else -> "Voice recognition unavailable (code $error). Use text chat fallback."
                    }
                    _state.value = _state.value.copy(
                        isListening = false,
                        audioLevel = 0f,
                        errorMessage = msg,
                        statusMessage = "Voice Guardian ready"
                    )
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val rawText = matches?.firstOrNull()?.trim().orEmpty()
                    _state.value = _state.value.copy(
                        isListening = false,
                        audioLevel = 0f,
                        partialTranscript = rawText,
                        statusMessage = "Voice Guardian ready"
                    )
                    if (rawText.isNotBlank()) {
                        val (wakeDetected, cleaned) = parseWakePhrase(rawText)
                        onFinalTranscript(cleaned.ifBlank { rawText }, wakeDetected)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _state.value = _state.value.copy(partialTranscript = partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.bcp47Tag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isListening = false,
                audioLevel = 0f,
                errorMessage = e.localizedMessage ?: "Failed to start voice recognition."
            )
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(
            isListening = false,
            audioLevel = 0f,
            statusMessage = "Listening stopped"
        )
    }

    fun speak(text: String) {
        if (_state.value.isMuted || !_state.value.ttsReady || text.isBlank()) return
        applyTtsSettings()
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "Code block omitted.")
            .replace(Regex("[*_#`]"), "")
            .take(3500)
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
        _state.value = _state.value.copy(
            isSpeaking = false,
            audioLevel = 0f,
            statusMessage = "Interrupted"
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }

    private fun parseWakePhrase(raw: String): Pair<Boolean, String> {
        val lower = raw.lowercase(Locale.ROOT)
        val triggers = listOf("hello jarvis", "hey jarvis", "হ্যালো জার্ভিস", "مرحبا جارفيس")
        for (trigger in triggers) {
            val idx = lower.indexOf(trigger)
            if (idx >= 0) {
                val remainder = raw.removeRange(idx, idx + trigger.length)
                    .trimStart(',', '.', ' ', ':', ';', '!', '?')
                return true to remainder
            }
        }
        return false to raw
    }
}
