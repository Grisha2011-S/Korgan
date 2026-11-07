package com.example

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.audio.AlarmSoundPlayer
import com.example.audio.RobotVoiceSpeaker
import com.example.ble.BleManager
import com.example.cloud.FamilyCloud
import com.example.cloud.PhoneRole
import com.example.data.AppDatabase
import com.example.data.UserPreferences
import com.example.dispatcher.AlertSource
import com.example.dispatcher.DispatchResult
import com.example.dispatcher.EmergencyDispatcher
import com.example.location.LocationHelper
import com.example.service.FallDetectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EmergencyDispatchState(
    val id: Long,
    val source: String,
    val isTest: Boolean,
    val inProgress: Boolean,
    val result: DispatchResult?
)

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
    lateinit var familyCloud: FamilyCloud
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nextDispatchId = 0L
    private var alertWatchdog: Job? = null
    private var alertHandled = false
    private val _dispatchState = MutableStateFlow<EmergencyDispatchState?>(null)
    val dispatchState: StateFlow<EmergencyDispatchState?> = _dispatchState.asStateFlow()

    fun onFallAlert(source: String, isTest: Boolean = false) {
        resetDispatchIfFinished()
        familyCloud.clearLiveAlert()
        alertHandled = false
        AlarmSoundPlayer.start(this)

        alertWatchdog?.cancel()
        val settings = preferences.getSettings()
        val fallbackDelayMs =
            (settings.countdownSeconds.toLong() + 20L).coerceAtLeast(30L) * 1000L

        alertWatchdog = appScope.launch {
            delay(fallbackDelayMs)
            if (!alertHandled && dispatchState.value == null) {
                android.util.Log.w(
                    "FallGuardApplication",
                    "Экран тревоги не появился за $fallbackDelayMs мс — отправляю тревогу в фоне"
                )
                startDispatch(source, isTest)
            }
        }
    }

    fun onAlertHandled() {
        alertHandled = true
        alertWatchdog?.cancel()
        alertWatchdog = null
        AlarmSoundPlayer.stop()
    }

    fun triggerTestAlert() {
        onFallAlert(AlertSource.NOTIFICATION_TEST, isTest = true)
        EmergencyAlertActivity.launch(
            this,
            source = AlertSource.NOTIFICATION_TEST,
            isTest = true
        )
    }

    fun startDispatch(source: String, isTest: Boolean = false): Long {
        val current = _dispatchState.value
        if (current?.inProgress == true) return current.id

        val id = ++nextDispatchId
        _dispatchState.value = EmergencyDispatchState(id, source, isTest, true, null)
        appScope.launch {
            val result = try {
                dispatcher.executeEmergencyProtocol(source, isTest)
            } catch (e: Exception) {
                android.util.Log.e("FallGuardApplication", "Emergency dispatch failed", e)
                null
            }
            _dispatchState.value = EmergencyDispatchState(id, source, isTest, false, result)
            AlarmSoundPlayer.stop()
            EmergencyAlertActivity.cancelAlertNotification(this@FallGuardApplication)
        }
        return id
    }

    fun resetDispatchIfFinished() {
        if (_dispatchState.value?.inProgress != true) {
            _dispatchState.value = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getDatabase(this)
        preferences = UserPreferences(this)
        locationHelper = LocationHelper(this, preferences)
        robotVoiceSpeaker = RobotVoiceSpeaker(this)
        bleManager = BleManager(this)
        familyCloud = FamilyCloud(this).apply {
            wearerNameProvider = { preferences.getSettings().userName }
            migrateRole(preferences.getSettings().hasCompletedProfileSetup)
        }
        dispatcher = EmergencyDispatcher(
            context = this,
            preferences = preferences,
            locationHelper = locationHelper,
            robotVoiceSpeaker = robotVoiceSpeaker,
            database = database,
            familyCloud = familyCloud,
            batteryProvider = { bleManager.lastBatteryPercent }
        )

        createNotificationChannels()

        bleManager.onFallDetectedListener = { source ->
            val safeSource = if (source == AlertSource.ESP32_SOS) {
                AlertSource.ESP32_SOS
            } else {
                AlertSource.ESP32_FALL
            }
            mainHandler.post {
                onFallAlert(safeSource)
                EmergencyAlertActivity.launch(this, source = safeSource)
            }
        }

        familyCloud.syncInBackground()
        val settings = preferences.getSettings()
        if (settings.hasCompletedProfileSetup && familyCloud.state.value.role == PhoneRole.WEARER) {
            locationHelper.requestSingleUpdate()
            if (settings.serviceEnabled && hasRequiredPermissionsForService()) {
                FallDetectionService.startService(this)
            }
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

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Экстренная тревога (Падение)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Полноэкранная тревога со звуком будильника при падении или SOS"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 700, 300, 700, 300, 1200)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = android.graphics.Color.RED
                if (alarmSound != null) {
                    setSound(alarmSound, alarmAttributes)
                }
            }

            val infoChannel = NotificationChannel(
                CHANNEL_INFO_ID,
                "Ответы родственников",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Родственник получил тревогу и едет; новые связки телефонов"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(infoChannel)
        }
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "fall_service_channel"
        const val CHANNEL_ALERT_ID = "fall_alert_channel_v2"
        const val CHANNEL_INFO_ID = "family_info_channel"

        lateinit var instance: FallGuardApplication
            private set
    }
}
