package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.EmergencyAlertActivity
import com.example.FallGuardApplication
import com.example.MainActivity
import com.example.R
import com.example.ble.BleStatus
import com.example.dispatcher.AlertSource
import com.example.i18n.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FallDetectionService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var reconnectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as FallGuardApplication
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FallGuard::ServiceWakeLock"
        )
        try {
            wakeLock?.acquire()
        } catch (_: Exception) {}

        startForegroundNotification(app.bleManager.bleStatus.value)

        serviceScope.launch {
            app.bleManager.bleStatus.collectLatest { status ->
                updateNotification(status)
                handleAutoReconnect(status)
            }
        }

        serviceScope.launch {
            while (true) {
                delay(30_000)
                val settings = app.preferences.getSettings()
                val status = app.bleManager.bleStatus.value
                if (settings.autoReconnect &&
                    settings.savedBleAddress.isNotBlank() &&
                    status == BleStatus.DISCONNECTED &&
                    app.bleManager.hasBluetoothPermission() &&
                    app.bleManager.isBluetoothEnabled()
                ) {
                    app.bleManager.addLog("Плановая проверка связи: переподключение...")
                    app.bleManager.connectToAddress(settings.savedBleAddress)
                }
            }
        }
    }

    private fun handleAutoReconnect(status: BleStatus) {
        val app = application as FallGuardApplication
        val settings = app.preferences.getSettings()

        if (status == BleStatus.DISCONNECTED && settings.autoReconnect && settings.savedBleAddress.isNotBlank()) {
            reconnectJob?.cancel()
            reconnectJob = serviceScope.launch {
                delay(4000)
                if (app.bleManager.bleStatus.value == BleStatus.DISCONNECTED) {
                    app.bleManager.addLog("Автоматическое переподключение к ${settings.savedBleAddress}...")
                    app.bleManager.connectToAddress(settings.savedBleAddress)
                }
            }
        }
    }

    private fun startForegroundNotification(status: BleStatus) {
        val notification = buildNotification(status)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        val serviceType =
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

        try {
            startForeground(NOTIFICATION_ID, notification, serviceType)
            return
        } catch (e: Exception) {
            android.util.Log.w("FallDetectionService", "startForeground(location) failed", e)
        }

        try {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            return
        } catch (e: Exception) {
            android.util.Log.w("FallDetectionService", "startForeground(connectedDevice) failed", e)
        }

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNotification(status: BleStatus) {
        val notification = buildNotification(status)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(status: BleStatus): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTestIntent = Intent(this, EmergencyAlertActivity::class.java).apply {
            putExtra(EmergencyAlertActivity.EXTRA_SOURCE, AlertSource.NOTIFICATION_TEST)
            putExtra(EmergencyAlertActivity.EXTRA_IS_TEST, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val testPendingIntent = PendingIntent.getActivity(
            this,
            1,
            triggerTestIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val app = application as FallGuardApplication
        val strings = AppStrings.get(app.preferences.getSettings().appLanguage)

        val statusText = when (status) {
            BleStatus.CONNECTED, BleStatus.MONITORING -> strings.statusEsp32Connected
            BleStatus.CONNECTING -> strings.statusEsp32Connecting
            BleStatus.SCANNING -> strings.statusEsp32Scanning
            BleStatus.DISCONNECTED -> strings.statusEsp32Disconnected
        }

        return NotificationCompat.Builder(this, FallGuardApplication.CHANNEL_SERVICE_ID)
            .setContentTitle("${strings.appTitle} • ${strings.detectorStatusTitle}")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_fall_guard_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_dialog_alert,
                strings.testAlertButton,
                testPendingIntent
            )
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as FallGuardApplication
        val settings = app.preferences.getSettings()
        if (!settings.hasCompletedProfileSetup || !settings.serviceEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
            }
        } catch (_: Exception) {}

        val status = app.bleManager.bleStatus.value
        if (status == BleStatus.DISCONNECTED &&
            settings.autoReconnect &&
            settings.savedBleAddress.isNotBlank() &&
            app.bleManager.hasBluetoothPermission() &&
            app.bleManager.isBluetoothEnabled()
        ) {
            app.bleManager.connectToAddress(settings.savedBleAddress)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val app = context.applicationContext as? FallGuardApplication ?: return
            val settings = app.preferences.getSettings()
            if (!settings.hasCompletedProfileSetup || !settings.serviceEnabled) return
            if (app.familyCloud.state.value.role == com.example.cloud.PhoneRole.CAREGIVER) return
            if (!app.bleManager.hasBluetoothPermission()) return
            try {
                val intent = Intent(context, FallDetectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java)
            context.stopService(intent)
        }
    }
}
