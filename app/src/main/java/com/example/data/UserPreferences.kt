package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val appLanguage: String = "ru",
    val hasCompletedLanguageSetup: Boolean = false,
    val userName: String = "Алексей",
    val contactPhone: String = "+79991234567",
    val customTemplate: String = "Здравствуйте! {имя} упал и не может встать, находится {адрес}!",
    val countdownSeconds: Int = 10,
    val autoSpeakerphone: Boolean = true,
    val sendSms: Boolean = true,
    val enableCallAndVoice: Boolean = false,
    val robotVoiceSpeed: Float = 0.95f,
    val robotVoicePitch: Float = 1.0f,
    val savedBleAddress: String = "",
    val savedBleName: String = "",
    val autoReconnect: Boolean = true,
    val serviceEnabled: Boolean = true
)

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fall_guard_preferences", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<UserSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): UserSettings {
        return UserSettings(
            appLanguage = prefs.getString(KEY_APP_LANGUAGE, "ru") ?: "ru",
            hasCompletedLanguageSetup = prefs.getBoolean(KEY_LANGUAGE_SETUP_COMPLETED, false),
            userName = prefs.getString(KEY_USER_NAME, "Алексей") ?: "Алексей",
            contactPhone = prefs.getString(KEY_CONTACT_PHONE, "+79991234567") ?: "+79991234567",
            customTemplate = prefs.getString(
                KEY_CUSTOM_TEMPLATE,
                "Здравствуйте! {имя} упал и не может встать, находится {адрес}!"
            ) ?: "Здравствуйте! {имя} упал и не может встать, находится {адрес}!",
            countdownSeconds = prefs.getInt(KEY_COUNTDOWN_SECONDS, 10),
            autoSpeakerphone = prefs.getBoolean(KEY_AUTO_SPEAKERPHONE, true),
            sendSms = prefs.getBoolean(KEY_SEND_SMS, true),
            enableCallAndVoice = prefs.getBoolean(KEY_ENABLE_CALL_AND_VOICE, false),
            robotVoiceSpeed = prefs.getFloat(KEY_VOICE_SPEED, 0.95f),
            robotVoicePitch = prefs.getFloat(KEY_VOICE_PITCH, 1.0f),
            savedBleAddress = prefs.getString(KEY_BLE_ADDRESS, "") ?: "",
            savedBleName = prefs.getString(KEY_BLE_NAME, "") ?: "",
            autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, true),
            serviceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        )
    }

    fun getSettings(): UserSettings = _settingsFlow.value

    fun updateAppLanguage(lang: String, markSetupCompleted: Boolean = true) {
        val current = _settingsFlow.value
        val defaultTemplates = listOf(
            "Здравствуйте! {имя} упал и не может встать, находится {адрес}!",
            "Сәлеметсіз бе! {имя} құлап қалды және тұра алмайды, мекенжайы: {адрес}!",
            "Hello! {name} has fallen and cannot get up, located at {address}!"
        )

        val newTemplate = if (current.customTemplate in defaultTemplates || current.customTemplate.isBlank()) {
            when (lang) {
                "kk" -> "Сәлеметсіз бе! {имя} құлап қалды және тұра алмайды, мекенжайы: {адрес}!"
                "en" -> "Hello! {name} has fallen and cannot get up, located at {address}!"
                else -> "Здравствуйте! {имя} упал и не может встать, находится {адрес}!"
            }
        } else {
            current.customTemplate
        }

        prefs.edit()
            .putString(KEY_APP_LANGUAGE, lang)
            .putBoolean(KEY_LANGUAGE_SETUP_COMPLETED, markSetupCompleted)
            .putString(KEY_CUSTOM_TEMPLATE, newTemplate)
            .apply()

        _settingsFlow.value = _settingsFlow.value.copy(
            appLanguage = lang,
            hasCompletedLanguageSetup = markSetupCompleted,
            customTemplate = newTemplate
        )
    }

    fun markLanguageSetupCompleted() {
        prefs.edit().putBoolean(KEY_LANGUAGE_SETUP_COMPLETED, true).apply()
        _settingsFlow.value = _settingsFlow.value.copy(hasCompletedLanguageSetup = true)
    }

    fun updateUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name.trim()).apply()
        _settingsFlow.value = _settingsFlow.value.copy(userName = name.trim())
    }

    fun updateContactPhone(phone: String) {
        prefs.edit().putString(KEY_CONTACT_PHONE, phone.trim()).apply()
        _settingsFlow.value = _settingsFlow.value.copy(contactPhone = phone.trim())
    }

    fun updateCustomTemplate(template: String) {
        prefs.edit().putString(KEY_CUSTOM_TEMPLATE, template).apply()
        _settingsFlow.value = _settingsFlow.value.copy(customTemplate = template)
    }

    fun updateCountdownSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_COUNTDOWN_SECONDS, seconds).apply()
        _settingsFlow.value = _settingsFlow.value.copy(countdownSeconds = seconds)
    }

    fun updateAutoSpeakerphone(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SPEAKERPHONE, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(autoSpeakerphone = enabled)
    }

    fun updateSendSms(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEND_SMS, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(sendSms = enabled)
    }

    fun updateEnableCallAndVoice(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_CALL_AND_VOICE, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(enableCallAndVoice = enabled)
    }

    fun updateVoiceSettings(speed: Float, pitch: Float) {
        prefs.edit()
            .putFloat(KEY_VOICE_SPEED, speed)
            .putFloat(KEY_VOICE_PITCH, pitch)
            .apply()
        _settingsFlow.value = _settingsFlow.value.copy(
            robotVoiceSpeed = speed,
            robotVoicePitch = pitch
        )
    }

    fun saveBleDevice(name: String, address: String) {
        prefs.edit()
            .putString(KEY_BLE_NAME, name)
            .putString(KEY_BLE_ADDRESS, address)
            .apply()
        _settingsFlow.value = _settingsFlow.value.copy(
            savedBleName = name,
            savedBleAddress = address
        )
    }

    fun clearSavedBleDevice() {
        prefs.edit()
            .remove(KEY_BLE_NAME)
            .remove(KEY_BLE_ADDRESS)
            .apply()
        _settingsFlow.value = _settingsFlow.value.copy(
            savedBleName = "",
            savedBleAddress = ""
        )
    }

    fun updateAutoReconnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(autoReconnect = enabled)
    }

    fun updateServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(serviceEnabled = enabled)
    }

    companion object {
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_LANGUAGE_SETUP_COMPLETED = "language_setup_completed"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_CONTACT_PHONE = "contact_phone"
        private const val KEY_CUSTOM_TEMPLATE = "custom_template"
        private const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
        private const val KEY_AUTO_SPEAKERPHONE = "auto_speakerphone"
        private const val KEY_SEND_SMS = "send_sms"
        private const val KEY_ENABLE_CALL_AND_VOICE = "enable_call_and_voice"
        private const val KEY_VOICE_SPEED = "voice_speed"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_BLE_ADDRESS = "ble_address"
        private const val KEY_BLE_NAME = "ble_name"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }
}
