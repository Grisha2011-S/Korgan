package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.audio.RobotVoiceSpeaker
import com.example.ble.BleManager
import com.example.data.AppDatabase
import com.example.data.UserPreferences
import com.example.dispatcher.EmergencyDispatcher
import com.example.location.LocationHelper
import com.example.service.FallDetectionService

class FallGuardApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var preferences: UserPreferences
        private set
    lateinit var locationHelper: LocationHelper
        private set
    lateinit var robotVoiceSpeaker: RobotVoiceSpeaker
        private set
    lateinit var bleManager: BleManager
        private set
    lateinit var dispatcher: EmergencyDispatcher
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getDatabase(this)
        preferences = UserPreferences(this)
        locationHelper = LocationHelper(this, preferences)
        robotVoiceSpeaker = RobotVoiceSpeaker(this)
        bleManager = BleManager(this)
        dispatcher = EmergencyDispatcher(
            context = this,
            preferences = preferences,
            locationHelper = locationHelper,
            robotVoiceSpeaker = robotVoiceSpeaker,
            database = database
        )

        createNotificationChannels()

        bleManager.onFallDetectedListener = {
            EmergencyAlertActivity.launch(this, source = "ESP32_BLE")
        }

        locationHelper.requestSingleUpdate()

        if (preferences.getSettings().serviceEnabled && hasRequiredPermissionsForService()) {
            FallDetectionService.startService(this)
        }
    }

    private fun hasRequiredPermissionsForService(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Мониторинг падений ESP32-C3",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Постоянное фоновое отслеживание BLE связи с датчиком ESP32-C3"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Экстренная тревога (Падение)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Срочные уведомления при обнаружении падения"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setBypassDnd(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "fall_service_channel"
        const val CHANNEL_ALERT_ID = "fall_alert_channel"

        lateinit var instance: FallGuardApplication
            private set
    }
}
