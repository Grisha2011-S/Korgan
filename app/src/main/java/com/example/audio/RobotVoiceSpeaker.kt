package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class RobotVoiceSpeaker(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            Log.e("RobotVoiceSpeaker", "Error initializing TTS", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
            isInitialized = true
        }
    }

    fun speak(
        text: String,
        repeatCount: Int = 6,
        speed: Float = 0.88f,
        pitch: Float = 1.05f,
        forceMaxVolume: Boolean = true,
        initialDelayMs: Long = 4000L
    ) {
        if (forceMaxVolume) {
            enableLoudSpeaker()
        }

        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)

        if (!isInitialized) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    tts?.setLanguage(Locale("ru", "RU"))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts?.setAudioAttributes(audioAttributes)
                    }
                    executeSpeech(text, repeatCount, initialDelayMs)
                }
            }
        } else {
            executeSpeech(text, repeatCount, initialDelayMs)
        }
    }

    private fun executeSpeech(text: String, repeatCount: Int, initialDelayMs: Long) {
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        _isSpeaking.value = true

        CoroutineScope(Dispatchers.Default).launch {
            if (initialDelayMs > 0) {
                delay(initialDelayMs)
            }
            for (i in 0 until repeatCount) {
                enableLoudSpeaker()
                
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
                } catch (_: Exception) {}
                delay(300)

                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "alert_speech_$i")
                delay(5500)
            }
            _isSpeaking.value = false
        }
    }

    fun playCountdownBeep(highPitch: Boolean = false) {
        try {
            val tone = if (highPitch) ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK else ToneGenerator.TONE_PROP_BEEP
            toneGenerator?.startTone(tone, 200)
        } catch (_: Exception) {}
    }

    fun enableLoudSpeaker() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true

            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)

            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)

            val maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0)
        } catch (e: Exception) {
            Log.e("RobotVoiceSpeaker", "Error maximizing volume", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
            toneGenerator?.stopTone()
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            stop()
            tts?.shutdown()
            toneGenerator?.release()
        } catch (_: Exception) {}
    }
}
