package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class UserSettings(
    val appLanguage: String = "ru",
    val hasCompletedLanguageSetup: Boolean = false,
    val userName: String = "",
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val hasCompletedProfileSetup: Boolean = false,
    val customTemplate: String = DefaultTemplates.RU,
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
) {
    val contactPhone: String
        get() = emergencyContacts.firstOrNull { isValidPhoneNumber(it.phone) }?.phone
            ?: emergencyContacts.firstOrNull()?.phone.orEmpty()

    val validContacts: List<EmergencyContact>
        get() = emergencyContacts.filter { isValidPhoneNumber(it.phone) }
}

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fall_guard_preferences", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<UserSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): UserSettings {
        val language = prefs.getString(KEY_APP_LANGUAGE, "ru") ?: "ru"
        val name = prefs.getString(KEY_USER_NAME, "") ?: ""
        val contacts = loadContacts()
        val storedProfileCompleted = prefs.getBoolean(KEY_PROFILE_SETUP_COMPLETED, false)
        val profileIsUsable = storedProfileCompleted &&
            name.isNotBlank() &&
            contacts.any { isValidPhoneNumber(it.phone) }

        val storedTemplate = prefs.getString(KEY_CUSTOM_TEMPLATE, null)
        val template = if (storedTemplate.isNullOrBlank() || storedTemplate in DefaultTemplates.allKnown()) {
            DefaultTemplates.forLanguage(language)
        } else {
            storedTemplate
        }

        return UserSettings(
            appLanguage = language,
            hasCompletedLanguageSetup = prefs.getBoolean(KEY_LANGUAGE_SETUP_COMPLETED, false),
            userName = name,
            emergencyContacts = contacts,
            hasCompletedProfileSetup = profileIsUsable,
            customTemplate = template,
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

    private fun loadContacts(): List<EmergencyContact> {
        val json = prefs.getString(KEY_CONTACTS_JSON, null)
        if (!json.isNullOrBlank()) {
            val parsed = decodeContacts(json)
            if (parsed.isNotEmpty()) return parsed
        }

        val legacyPhone = (prefs.getString(KEY_CONTACT_PHONE, "") ?: "").trim()
        if (legacyPhone.isNotBlank()) {
            val migrated = listOf(EmergencyContact(name = "", phone = legacyPhone))
            prefs.edit()
                .putString(KEY_CONTACTS_JSON, encodeContacts(migrated))
                .remove(KEY_CONTACT_PHONE)
                .apply()
            return migrated
        }
        return emptyList()
    }

    fun getSettings(): UserSettings = _settingsFlow.value

    fun saveProfile(name: String, contacts: List<EmergencyContact>) {
        val cleanName = name.trim()
        val cleanContacts = contacts
            .map { it.copy(name = it.name.trim(), phone = it.phone.trim()) }
            .filter { it.name.isNotBlank() || it.phone.isNotBlank() }
        val isValid = cleanName.isNotBlank() && cleanContacts.any { isValidPhoneNumber(it.phone) }

        prefs.edit()
            .putString(KEY_USER_NAME, cleanName)
            .putString(KEY_CONTACTS_JSON, encodeContacts(cleanContacts))
            .putBoolean(KEY_PROFILE_SETUP_COMPLETED, isValid)
            .remove(KEY_CONTACT_PHONE)
            .apply()

        _settingsFlow.value = _settingsFlow.value.copy(
            userName = cleanName,
            emergencyContacts = cleanContacts,
            hasCompletedProfileSetup = isValid
        )
    }

    fun updateAppLanguage(lang: String, markSetupCompleted: Boolean = true) {
        val current = _settingsFlow.value
        val newTemplate = if (current.customTemplate.isBlank() ||
            current.customTemplate in DefaultTemplates.allKnown()
        ) {
            DefaultTemplates.forLanguage(lang)
        } else {
            current.customTemplate
        }

        prefs.edit()
            .putString(KEY_APP_LANGUAGE, lang)
            .putBoolean(KEY_LANGUAGE_SETUP_COMPLETED, markSetupCompleted)
            .putString(KEY_CUSTOM_TEMPLATE, newTemplate)
            .apply()

        _settingsFlow.value = current.copy(
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
        prefs.edit().putString(KEY_USER_NAME, name).apply()
        _settingsFlow.value = _settingsFlow.value.copy(userName = name)
    }

    fun updateEmergencyContacts(contacts: List<EmergencyContact>) {
        val clean = contacts.filter { it.name.isNotBlank() || it.phone.isNotBlank() }
        prefs.edit()
            .putString(KEY_CONTACTS_JSON, encodeContacts(clean))
            .remove(KEY_CONTACT_PHONE)
            .apply()
        _settingsFlow.value = _settingsFlow.value.copy(emergencyContacts = clean)
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

    private fun encodeContacts(contacts: List<EmergencyContact>): String {
        val array = JSONArray()
        contacts.forEach { contact ->
            if (contact.name.isBlank() && contact.phone.isBlank()) return@forEach
            array.put(
                JSONObject().apply {
                    put("id", contact.id)
                    put("name", contact.name)
                    put("phone", contact.phone)
                }
            )
        }
        return array.toString()
    }

    private fun decodeContacts(json: String): List<EmergencyContact> {
        return try {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name", "")
                    val phone = item.optString("phone", "")
                    if (name.isBlank() && phone.isBlank()) continue
                    val id = item.optString("id", "").ifBlank { UUID.randomUUID().toString() }
                    add(EmergencyContact(id = id, name = name, phone = phone))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_LANGUAGE_SETUP_COMPLETED = "language_setup_completed"
        private const val KEY_PROFILE_SETUP_COMPLETED = "profile_setup_completed"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_CONTACT_PHONE = "contact_phone"
        private const val KEY_CONTACTS_JSON = "emergency_contacts_json"
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
