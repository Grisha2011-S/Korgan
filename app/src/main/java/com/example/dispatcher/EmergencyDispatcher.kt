package com.example.dispatcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.audio.RobotVoiceSpeaker
import com.example.cloud.AlertDelivery
import com.example.cloud.FamilyCloud
import com.example.cloud.SentAlert
import com.example.data.AlertEvent
import com.example.data.AppDatabase
import com.example.data.EmergencyContact
import com.example.data.UserPreferences
import com.example.data.isValidPhoneNumber
import com.example.data.normalizePhoneNumber
import com.example.i18n.FamilyText
import com.example.location.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DispatchStatus {
    TEST,
    NO_CONTACT,
    SMS_SENT,
    SMS_PARTIAL,
    CALL_STARTED,
    /** Тревога доставлена родственникам в приложение, SMS не понадобились. */
    APP_SENT,
    /** Родственник ответил «Я еду». */
    APP_ACKED,
    FAILED
}

data class DispatchResult(
    val status: DispatchStatus,
    val message: String,
    val sentCount: Int = 0,
    val totalCount: Int = 0,
    /** Сколько родственников связано с телефоном через сервер (0 — приложение не использовалось). */
    val appRecipients: Int = 0,
    val appAckedBy: String? = null
)

class EmergencyDispatcher(
    private val context: Context,
    private val preferences: UserPreferences,
    private val locationHelper: LocationHelper,
    private val robotVoiceSpeaker: RobotVoiceSpeaker,
    private val database: AppDatabase,
    private val familyCloud: FamilyCloud,
    private val batteryProvider: () -> Int
) {
    private val TAG = "EmergencyDispatcher"
    private val smsMessageBuilder = SmsMessageBuilder()

    fun buildSmsMessage(
        source: String,
        userName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        customTemplate: String,
        languageCode: String
    ): String = smsMessageBuilder.build(
        source = source,
        userName = userName,
        address = address,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        template = customTemplate,
        languageCode = languageCode
    )

    fun buildVoiceMessage(
        source: String,
        userName: String,
        address: String,
        languageCode: String
    ): String = smsMessageBuilder.buildVoiceMessage(
        source = source,
        userName = userName,
        address = address,
        languageCode = languageCode
    )

    suspend fun executeEmergencyProtocol(
        source: String,
        isTest: Boolean = false
    ): DispatchResult = withContext(Dispatchers.IO) {
        val settings = preferences.getSettings()
        val address = locationHelper.getCurrentAddressDirectly()
        val locationState = locationHelper.locationState.value
        val contacts: List<EmergencyContact> = settings.validContacts

        val smsText = smsMessageBuilder.build(
            source = source,
            userName = settings.userName,
            address = address,
            latitude = locationState.latitude,
            longitude = locationState.longitude,
            accuracyMeters = locationState.accuracy,
            template = settings.customTemplate,
            languageCode = settings.appLanguage
        )
        val voiceText = smsMessageBuilder.buildVoiceMessage(
            source = source,
            userName = settings.userName,
            address = address,
            languageCode = settings.appLanguage
        )

        Log.i(
            TAG,
            "Executing emergency protocol (test=$isTest, source=$source, recipients=${contacts.size}, " +
                "SMS: ${settings.sendSms}, Call & Voice: ${settings.enableCallAndVoice})"
        )

        var sentCount = 0
        var callStarted = false

        // 1. Родственники в приложении Қорған (через сервер). Проверочная тревога
        //    тоже уходит в приложение — так удобно проверить связку телефонов.
        val delivery = familyCloud.state.value.delivery
        val appAlert: SentAlert? = if (delivery != AlertDelivery.SMS) {
            sendToApp(source, isTest, settings.userName, settings.appLanguage, smsText, address,
                locationState.latitude, locationState.longitude, locationState.accuracy)
        } else {
            null
        }
        var appAckedBy: String? = null

        // 2. Нужны ли SMS и звонок. Если сервер недоступен или никто не связан,
        //    SMS уходят при любом режиме: тревога не должна потеряться.
        val smsNeeded = when {
            isTest -> false
            delivery == AlertDelivery.SMS || delivery == AlertDelivery.BOTH -> true
            appAlert == null -> true
            delivery == AlertDelivery.APP -> false
            else -> {
                val timeoutMs = familyCloud.state.value.ackTimeoutSeconds * 1000L
                appAckedBy = familyCloud.waitForAck(appAlert.id, timeoutMs)
                Log.i(TAG, "App alert ${appAlert.id}: acked by ${appAckedBy ?: "nobody"}")
                appAckedBy == null
            }
        }

        val status = when {
            isTest -> DispatchStatus.TEST
            !smsNeeded && appAckedBy != null -> DispatchStatus.APP_ACKED
            !smsNeeded -> DispatchStatus.APP_SENT
            contacts.isEmpty() && appAlert != null -> DispatchStatus.APP_SENT
            contacts.isEmpty() -> {
                Log.w(TAG, "No valid emergency contacts configured, alert was not dispatched")
                DispatchStatus.NO_CONTACT
            }
            else -> {
                if (settings.sendSms) {
                    contacts.forEach { contact ->
                        if (sendEmergencySms(contact.phone, smsText)) {
                            sentCount++
                        }
                    }
                }

                if (settings.enableCallAndVoice) {
                    withContext(Dispatchers.Main) {
                        robotVoiceSpeaker.speak(
                            text = voiceText,
                            repeatCount = 3,
                            speed = settings.robotVoiceSpeed,
                            pitch = settings.robotVoicePitch,
                            forceMaxVolume = settings.autoSpeakerphone,
                            initialDelayMs = 500L
                        )
                    }
                    withContext(Dispatchers.Main) {
                        callStarted = makeEmergencyCall(contacts.first().phone)
                    }
                }

                when {
                    settings.sendSms && sentCount == contacts.size -> DispatchStatus.SMS_SENT
                    settings.sendSms && sentCount > 0 -> DispatchStatus.SMS_PARTIAL
                    callStarted -> DispatchStatus.CALL_STARTED
                    else -> DispatchStatus.FAILED
                }
            }
        }

        val recipientsForHistory = contacts
            .map { it.phone.trim() }
            .ifEmpty { settings.emergencyContacts.map { it.phone.trim() }.filter { it.isNotBlank() } }
            .joinToString(", ")

        val event = AlertEvent(
            source = if (isTest) AlertSource.MANUAL_TEST else source,
            status = status.name,
            userName = settings.userName,
            contactPhone = recipientsForHistory,
            latitude = locationState.latitude,
            longitude = locationState.longitude,
            addressText = address,
            spokenMessage = smsText
        )
        database.alertEventDao().insertEvent(event)

        DispatchResult(
            status = status,
            message = smsText,
            sentCount = sentCount,
            totalCount = contacts.size,
            appRecipients = appAlert?.linked ?: 0,
            appAckedBy = appAckedBy
        )
    }

    private suspend fun sendToApp(
        source: String,
        isTest: Boolean,
        userName: String,
        languageCode: String,
        smsText: String,
        address: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float
    ): SentAlert? {
        val kind = when {
            isTest -> "TEST"
            AlertSource.isSos(source) -> "SOS"
            else -> "FALL"
        }
        val text = FamilyText.get(languageCode)
        val title = when (kind) {
            "SOS" -> text.alertNotificationSos
            "TEST" -> text.alertNotificationTest
            else -> text.alertNotificationFall
        }.format(userName.ifBlank { "Қорған" })
        val hasFix = latitude != 0.0 || longitude != 0.0
        val sent = familyCloud.sendAlert(
            kind = kind,
            title = title,
            body = smsText,
            latitude = latitude.takeIf { hasFix },
            longitude = longitude.takeIf { hasFix },
            accuracy = accuracy.takeIf { hasFix && it > 0f },
            address = address,
            battery = batteryProvider().takeIf { it in 0..100 }
        )
        Log.i(TAG, "App alert: ${sent?.let { "${it.linked} linked, ${it.pushed} pushed" } ?: "not sent"}")
        return sent?.takeIf { it.linked > 0 }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun sendEmergencySms(phone: String, message: String): Boolean {
        if (!isValidPhoneNumber(phone)) {
            Log.w(TAG, "Skipping invalid emergency number")
            return false
        }

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            Log.w(TAG, "SEND_SMS permission not granted")
            return false
        }

        val cleanPhone = normalizePhoneNumber(phone)
        if (cleanPhone.isBlank()) return false

        return try {
            val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            if (smsManager == null) {
                Log.e(TAG, "SmsManager is unavailable")
                return false
            }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            Log.i(TAG, "Sent emergency SMS to $cleanPhone (${parts.size} part(s))")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending emergency SMS", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun makeEmergencyCall(phone: String): Boolean {
        val cleanPhone = normalizePhoneNumber(phone)
        if (cleanPhone.isBlank()) return false

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

        return try {
            context.startActivity(intent)
            Log.i(TAG, "Emergency call initiated to $cleanPhone (direct=$hasCallPermission)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating emergency call", e)
            false
        }
    }

    companion object {
        const val SOURCE_MANUAL_TEST = AlertSource.MANUAL_TEST
        const val SOURCE_BLE = AlertSource.ESP32_FALL
        const val SOURCE_SOS = AlertSource.ESP32_SOS
    }
}
