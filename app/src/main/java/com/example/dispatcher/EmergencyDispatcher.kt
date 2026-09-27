package com.example.dispatcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.audio.RobotVoiceSpeaker
import com.example.data.AlertEvent
import com.example.data.AppDatabase
import com.example.data.UserPreferences
import com.example.location.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmergencyDispatcher(
    private val context: Context,
    private val preferences: UserPreferences,
    private val locationHelper: LocationHelper,
    private val robotVoiceSpeaker: RobotVoiceSpeaker,
    private val database: AppDatabase
) {
    private val TAG = "EmergencyDispatcher"

    fun buildEmergencyPhrase(userName: String, address: String, customTemplate: String): String {
        var template = customTemplate.ifBlank {
            "Здравствуйте! {имя} упал и не может встать, находится {адрес}!"
        }
        template = template.replace("{имя}", userName.ifBlank { "Пользователь" })
        template = template.replace("{имя пользователя}", userName.ifBlank { "Пользователь" })
        template = template.replace("{name}", userName.ifBlank { "Пользователь" })
        template = template.replace("{адрес}", address.ifBlank { "неизвестный адрес" })
        template = template.replace("{улица}", address.ifBlank { "неизвестная улица" })
        template = template.replace("{address}", address.ifBlank { "неизвестный адрес" })
        return template
    }

    suspend fun executeEmergencyProtocol(source: String): String = withContext(Dispatchers.IO) {
        val settings = preferences.getSettings()
        val address = locationHelper.getCurrentAddressDirectly()
        val locationState = locationHelper.locationState.value

        val spokenMessage = buildEmergencyPhrase(
            userName = settings.userName,
            address = address,
            customTemplate = settings.customTemplate
        )

        Log.i(TAG, "Executing emergency protocol: $spokenMessage (SMS: ${settings.sendSms}, Call & Voice: ${settings.enableCallAndVoice})")

        if (settings.enableCallAndVoice) {
            withContext(Dispatchers.Main) {
                robotVoiceSpeaker.speak(
                    text = spokenMessage,
                    repeatCount = 3,
                    speed = settings.robotVoiceSpeed,
                    pitch = settings.robotVoicePitch,
                    forceMaxVolume = settings.autoSpeakerphone,
                    initialDelayMs = 500L
                )
            }

            if (settings.contactPhone.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    makeEmergencyCall(settings.contactPhone)
                }
            }
        }

        var smsSuccess = false
        if (settings.sendSms && settings.contactPhone.isNotBlank()) {
            smsSuccess = sendEmergencySms(
                phone = settings.contactPhone,
                message = spokenMessage,
                lat = locationState.latitude,
                lng = locationState.longitude,
                langCode = settings.appLanguage
            )
        }

        val event = AlertEvent(
            source = source,
            status = if (smsSuccess) "SMS_SENT" else "ALERT_TRIGGERED",
            userName = settings.userName,
            contactPhone = settings.contactPhone,
            latitude = locationState.latitude,
            longitude = locationState.longitude,
            addressText = address,
            spokenMessage = spokenMessage
        )
        database.alertEventDao().insertEvent(event)

        return@withContext spokenMessage
    }

    @SuppressLint("MissingPermission")
    private fun sendEmergencySms(phone: String, message: String, lat: Double, lng: Double, langCode: String = "ru"): Boolean {
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            Log.w(TAG, "SEND_SMS permission not granted")
            return false
        }

        return try {
            val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val mapPrefix = when (langCode) {
                "kk" -> "Карта:"
                "en" -> "Map:"
                else -> "Карта:"
            }

            val alertPrefix = when (langCode) {
                "kk" -> "ДАБЫЛ!"
                "en" -> "EMERGENCY ALERT!"
                else -> "ТРЕВОГА!"
            }

            val mapsUrl = if (lat != 0.0 && lng != 0.0) {
                "\n$mapPrefix https://maps.google.com/?q=$lat,$lng"
            } else ""

            val fullSms = "$alertPrefix $message$mapsUrl"
            val parts = smsManager.divideMessage(fullSms)
            val cleanPhone = phone.replace(" ", "").replace("-", "")
            smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            Log.i(TAG, "Sent emergency SMS to $cleanPhone")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending emergency SMS", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun makeEmergencyCall(phone: String) {
        val cleanPhone = phone.replace(" ", "").replace("-", "")
        if (cleanPhone.isBlank()) return

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanPhone"))
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            Log.i(TAG, "Emergency call initiated to $cleanPhone")
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating emergency call", e)
        }
    }
}
