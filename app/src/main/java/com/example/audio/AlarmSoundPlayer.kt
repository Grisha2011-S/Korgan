package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AlarmSoundPlayer {
    private const val TAG = "AlarmSoundPlayer"
    private val vibrationPattern = longArrayOf(0, 700, 300, 700, 300, 1200)

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    @Synchronized
    fun start(context: Context) {
        val appContext = context.applicationContext
        raiseAlarmVolume(appContext)
        startVibration(appContext)
        startSound(appContext)
    }

    @Synchronized
    fun stop() {
        try {
            player?.stop()
        } catch (_: Exception) {}

        try {
            player?.release()
        } catch (_: Exception) {}
        player = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun startSound(context: Context) {
        if (player?.isPlaying == true) {
            return
        }

        if (player == null) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: return

                player = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Не удалось запустить звук тревоги", e)
                releasePlayer()
            }
        } else {
            try {
                player?.start()
            } catch (_: Exception) {
                releasePlayer()
            }
        }
    }

    private fun releasePlayer() {
        try {
            player?.stop()
        } catch (_: Exception) {}
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
    }

    private fun raiseAlarmVolume(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (maxVolume > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration(context: Context) {
        try {
            if (vibrator == null) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            }

            val device = vibrator ?: return
            if (!device.hasVibrator()) {
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
            } else {
                device.vibrate(vibrationPattern, 0)
            }
        } catch (_: Exception) {
        }
    }
}
