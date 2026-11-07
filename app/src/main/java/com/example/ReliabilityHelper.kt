package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dispatcher.AlertSource
import java.util.Locale

/**
 * Набор системных разрешений, без которых экран тревоги не откроется на
 * заблокированном телефоне. Начиная с Android 14 полноэкранные уведомления
 * выдаются отдельно и могут быть отозваны пользователем.
 */
data class AlarmReliability(
    val notificationsEnabled: Boolean,
    val alertChannelOk: Boolean,
    val fullScreenAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val dndAccess: Boolean
) {
    val criticalOk: Boolean
        get() = notificationsEnabled && alertChannelOk && fullScreenAllowed && batteryUnrestricted
}

object ReliabilityHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingTest: Runnable? = null

    fun check(context: Context): AlarmReliability {
        val app = context.applicationContext
        val notificationManager =
            app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val notificationsEnabled = NotificationManagerCompat.from(app).areNotificationsEnabled()

        val alertChannelOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager?.getNotificationChannel(
                FallGuardApplication.CHANNEL_ALERT_ID
            )
            channel == null || channel.importance >= NotificationManager.IMPORTANCE_HIGH
        } else {
            true
        }

        val fullScreenAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notificationManager?.canUseFullScreenIntent() == true
        } else {
            true
        }

        val batteryUnrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(app.packageName) == true
        } else {
            true
        }

        val dndAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager?.isNotificationPolicyAccessGranted == true
        } else {
            true
        }

        return AlarmReliability(
            notificationsEnabled = notificationsEnabled,
            alertChannelOk = alertChannelOk,
            fullScreenAllowed = fullScreenAllowed,
            batteryUnrestricted = batteryUnrestricted,
            dndAccess = dndAccess
        )
    }

    fun openNotificationSettings(context: Context) {
        val app = context.applicationContext
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startSafely(context, intent, appDetailsIntent(app))
    }

    fun openAlertChannelSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openNotificationSettings(context)
            return
        }
        val app = context.applicationContext
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, FallGuardApplication.CHANNEL_ALERT_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startSafely(context, intent, appDetailsIntent(app))
    }

    fun openFullScreenIntentSettings(context: Context) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startSafely(context, intent, appNotificationIntent(app))
        } else {
            openNotificationSettings(context)
        }
    }

    fun openBatterySettings(context: Context) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startSafely(context, request, fallback)
        } else {
            openAppDetails(context)
        }
    }

    fun openDndAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            openNotificationSettings(context)
            return
        }
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startSafely(context, intent, appDetailsIntent(context.applicationContext))
    }

    fun openAppDetails(context: Context) {
        startSafely(
            context,
            appDetailsIntent(context.applicationContext),
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun needsOemAutostartHint(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return listOf(
            "xiaomi", "redmi", "huawei", "honor", "samsung",
            "oppo", "vivo", "oneplus", "realme", "meizu"
        ).any { manufacturer.contains(it) }
    }

    /**
     * Тестовая тревога с задержкой: пользователь успевает заблокировать экран
     * и проверить именно тот путь, которым идёт реальное падение.
     */
    fun scheduleTestAlarm(context: Context, delayMs: Long = 10_000L) {
        val appContext = context.applicationContext
        pendingTest?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            pendingTest = null
            val app = appContext as? FallGuardApplication
            if (app != null) {
                app.triggerTestAlert()
            } else {
                EmergencyAlertActivity.launch(
                    appContext,
                    source = AlertSource.NOTIFICATION_TEST,
                    isTest = true
                )
            }
        }
        pendingTest = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun appDetailsIntent(app: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${app.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun appNotificationIntent(app: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun startSafely(context: Context, intent: Intent, fallback: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(fallback)
            } catch (_: Exception) {
                // Настройки недоступны на некоторых прошивках — ничего не делаем.
            }
        }
    }
}

@Composable
fun rememberAlarmReliability(): AlarmReliability {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(ReliabilityHelper.check(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = ReliabilityHelper.check(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return status
}
