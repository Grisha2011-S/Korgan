package com.example.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.FallGuardApplication
import com.example.audio.RobotVoiceSpeaker
import com.example.ble.BleManager
import com.example.ble.BleStatus
import com.example.ble.DiscoveredDevice
import com.example.data.AlertEvent
import com.example.data.AppDatabase
import com.example.data.UserPreferences
import com.example.data.UserSettings
import com.example.dispatcher.EmergencyDispatcher
import com.example.location.LocationHelper
import com.example.location.LocationInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app: FallGuardApplication = application as FallGuardApplication

    val preferences: UserPreferences = app.preferences
    val bleManager: BleManager = app.bleManager
    val locationHelper: LocationHelper = app.locationHelper
    val robotVoiceSpeaker: RobotVoiceSpeaker = app.robotVoiceSpeaker
    val dispatcher: EmergencyDispatcher = app.dispatcher
    private val database: AppDatabase = app.database

    val settings: StateFlow<UserSettings> = preferences.settingsFlow
    val bleStatus: StateFlow<BleStatus> = bleManager.bleStatus
    val connectedDeviceName: StateFlow<String?> = bleManager.connectedDeviceName
    val connectedDeviceAddress: StateFlow<String?> = bleManager.connectedDeviceAddress
    val signalRssi: StateFlow<Int?> = bleManager.signalRssi
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bleManager.discoveredDevices
    val bleLogs: StateFlow<List<String>> = bleManager.logs
    val locationInfo: StateFlow<LocationInfo> = locationHelper.locationState
    val isSpeaking: StateFlow<Boolean> = robotVoiceSpeaker.isSpeaking

    val allEvents: StateFlow<List<AlertEvent>> = database.alertEventDao()
        .getAllEvents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateAppLanguage(lang: String) {
        preferences.updateAppLanguage(lang)
        locationHelper.refreshAddressForLanguage()
    }

    fun markLanguageSetupCompleted() {
        preferences.markLanguageSetupCompleted()
    }

    fun updateUserName(name: String) {
        preferences.updateUserName(name)
    }

    fun updateContactPhone(phone: String) {
        preferences.updateContactPhone(phone)
    }

    fun updateCustomTemplate(template: String) {
        preferences.updateCustomTemplate(template)
    }

    fun updateCountdownSeconds(seconds: Int) {
        preferences.updateCountdownSeconds(seconds)
    }

    fun updateAutoSpeakerphone(enabled: Boolean) {
        preferences.updateAutoSpeakerphone(enabled)
    }

    fun updateSendSms(enabled: Boolean) {
        preferences.updateSendSms(enabled)
    }

    fun updateEnableCallAndVoice(enabled: Boolean) {
        preferences.updateEnableCallAndVoice(enabled)
    }

    fun updateVoiceSettings(speed: Float, pitch: Float) {
        preferences.updateVoiceSettings(speed, pitch)
    }

    fun updateAutoReconnect(enabled: Boolean) {
        preferences.updateAutoReconnect(enabled)
    }

    fun startBleScan() {
        bleManager.startScan()
    }

    fun stopBleScan() {
        bleManager.stopScan()
    }

    fun connectToDevice(device: BluetoothDevice, name: String, address: String) {
        preferences.saveBleDevice(name, address)
        bleManager.connectToDevice(device)
    }

    fun connectToSavedAddress(address: String) {
        bleManager.connectToAddress(address)
    }

    fun disconnectBle() {
        bleManager.disconnect()
    }

    fun clearSavedBle() {
        preferences.clearSavedBleDevice()
        bleManager.disconnect()
    }

    fun refreshLocation() {
        locationHelper.requestSingleUpdate()
    }

    fun clearAlertHistory() {
        viewModelScope.launch {
            database.alertEventDao().clearAll()
        }
    }

    fun clearBleLogs() {
        bleManager.clearLogs()
    }

    companion object {
        fun factory(app: FallGuardApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(app) as T
                }
            }
    }
}
