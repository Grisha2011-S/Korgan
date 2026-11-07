package com.example

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AlarmSoundPlayer
import com.example.dispatcher.AlertSource
import com.example.dispatcher.DispatchStatus
import com.example.i18n.AppStrings
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusConnected
import kotlinx.coroutines.delay

class EmergencyAlertActivity : ComponentActivity() {

    private var vibrator: Vibrator? = null

    private var alertSource by mutableStateOf(AlertSource.ESP32_FALL)
    private var alertIsTest by mutableStateOf(false)
    private var alertGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val app = application as FallGuardApplication
        readAlertIntent(intent)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                key(alertGeneration) {
                    EmergencyAlertScreen(
                        app = app,
                        source = alertSource,
                        isTest = alertIsTest,
                        onCancel = {
                            stopVibration()
                            app.onAlertHandled()
                            app.robotVoiceSpeaker.stop()
                            cancelAlertNotification(this@EmergencyAlertActivity)
                            finish()
                        },
                        onDispatched = {
                            stopVibration()
                            app.onAlertHandled()
                            cancelAlertNotification(this@EmergencyAlertActivity)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (application as FallGuardApplication).resetDispatchIfFinished()
        readAlertIntent(intent)
        AlarmSoundPlayer.stop()
        startVibration()
        alertGeneration++
    }

    private fun readAlertIntent(intent: Intent?) {
        alertSource = intent?.getStringExtra(EXTRA_SOURCE) ?: AlertSource.ESP32_FALL
        alertIsTest = intent?.getBooleanExtra(EXTRA_IS_TEST, false) ?: false
    }

    private fun startVibration() {
        try {
            val pattern = longArrayOf(0, 400, 200, 400, 200, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        AlarmSoundPlayer.stop()
        startVibration()
    }

    override fun onPause() {
        super.onPause()
        stopVibration()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVibration()
    }

    companion object {
        const val EXTRA_SOURCE = "extra_alert_source"
        const val EXTRA_IS_TEST = "extra_alert_is_test"
        const val ALERT_NOTIFICATION_ID = 999
        const val SOURCE_BLE = AlertSource.ESP32_FALL
        const val SOURCE_SOS = AlertSource.ESP32_SOS

        fun cancelAlertNotification(context: Context) {
            try {
                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(ALERT_NOTIFICATION_ID)
            } catch (_: Exception) {
            }
        }

        fun launch(
            context: Context,
            source: String = AlertSource.ESP32_FALL,
            isTest: Boolean = false
        ) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("DEPRECATION")
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "Qorghan:EmergencyScreenWakeLock"
                )
                wakeLock?.acquire(30_000L)
            } catch (_: Exception) {}

            val app = context.applicationContext as? FallGuardApplication
            app?.resetDispatchIfFinished()

            val intent = Intent(context, EmergencyAlertActivity::class.java).apply {
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_IS_TEST, isTest)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                ALERT_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val strings = AppStrings.get(app?.preferences?.getSettings()?.appLanguage ?: "ru")

                val canUseFullScreenIntent =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        notificationManager?.canUseFullScreenIntent() == true
                    } else {
                        true
                    }

                val isSos = source == AlertSource.ESP32_SOS
                val builder = NotificationCompat.Builder(context, FallGuardApplication.CHANNEL_ALERT_ID)
                    .setSmallIcon(R.drawable.ic_fall_guard_logo)
                    .setContentTitle(
                        when {
                            isTest -> strings.testModeHeader
                            isSos -> strings.sosDetectedHeader
                            else -> strings.fallDetectedHeader
                        }
                    )
                    .setContentText(
                        when {
                            isTest -> strings.testModeSubtitle
                            isSos -> strings.sosDetectedSubtitle
                            else -> strings.sensorDetectedSubtitle
                        }
                    )
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setOngoing(true)
                    .setAutoCancel(false)

                if (canUseFullScreenIntent) {
                    builder.setFullScreenIntent(pendingIntent, true)
                }

                notificationManager?.notify(ALERT_NOTIFICATION_ID, builder.build())
            } catch (_: Exception) {}

            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun EmergencyAlertScreen(
    app: FallGuardApplication,
    source: String,
    isTest: Boolean,
    onCancel: () -> Unit,
    onDispatched: () -> Unit
) {
    val settings by app.preferences.settingsFlow.collectAsStateWithLifecycle()
    val locationInfo by app.locationHelper.locationState.collectAsStateWithLifecycle()
    val dispatchState by app.dispatchState.collectAsStateWithLifecycle()
    val liveAppAlert by app.familyCloud.liveAlert.collectAsStateWithLifecycle()
    val strings = AppStrings.get(settings.appLanguage)
    val familyStrings = com.example.i18n.FamilyText.get(settings.appLanguage)
    val validContacts = settings.validContacts

    var secondsLeft by rememberSaveable { mutableIntStateOf(settings.countdownSeconds) }
    var isDispatched by rememberSaveable {
        mutableStateOf(app.dispatchState.value != null)
    }

    val result = dispatchState?.result
    val resultStatus = result?.status
    val finishedWithoutResult = dispatchState?.let { !it.inProgress && it.result == null } == true
    val outcomeIsError = finishedWithoutResult ||
        resultStatus == DispatchStatus.NO_CONTACT ||
        resultStatus == DispatchStatus.FAILED
    val outcomeIsPartial = resultStatus == DispatchStatus.SMS_PARTIAL
    val ackedBy = result?.appAckedBy ?: liveAppAlert?.ackedByName
    val outcomeText = when {
        finishedWithoutResult -> strings.dispatchFailed
        ackedBy != null -> familyStrings.appAcked.format(ackedBy)
        resultStatus == DispatchStatus.APP_SENT -> familyStrings.appDelivered
        dispatchState?.inProgress == true && liveAppAlert != null -> familyStrings.appWaitingAck
        resultStatus == DispatchStatus.TEST -> strings.dispatchTestComplete
        resultStatus == DispatchStatus.NO_CONTACT -> strings.dispatchNoContact
        resultStatus == DispatchStatus.FAILED -> strings.dispatchFailed
        resultStatus == DispatchStatus.SMS_SENT -> strings.smsSentSuccessCard
        resultStatus == DispatchStatus.SMS_PARTIAL ->
            strings.smsSentPartial.format(result?.sentCount ?: 0, result?.totalCount ?: 0)
        resultStatus == DispatchStatus.CALL_STARTED -> strings.dispatchCallStarted
        isDispatched -> strings.dispatchInProgress
        else -> null
    }
    val isSos = source == AlertSource.ESP32_SOS

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LaunchedEffect(isDispatched) {
        if (!isDispatched) {
            while (secondsLeft > 0) {
                app.robotVoiceSpeaker.playCountdownBeep(highPitch = secondsLeft <= 3)
                delay(1000)
                secondsLeft -= 1
            }
            if (!isDispatched) {
                isDispatched = true
                onDispatched()
                app.startDispatch(source, isTest)
            }
        }
    }

    val liveMessagePreview = remember(
        source,
        settings.userName,
        locationInfo.address,
        locationInfo.latitude,
        locationInfo.longitude,
        locationInfo.accuracy,
        settings.customTemplate,
        settings.appLanguage
    ) {
        app.dispatcher.buildSmsMessage(
            source = source,
            userName = settings.userName,
            address = locationInfo.address,
            latitude = locationInfo.latitude,
            longitude = locationInfo.longitude,
            accuracyMeters = locationInfo.accuracy,
            customTemplate = settings.customTemplate,
            languageCode = settings.appLanguage
        )
    }

    Scaffold(
        containerColor = Color(0xFF0F0505)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .scale(if (!isDispatched) pulseScale else 1f)
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = 0.2f))
                        .border(2.dp, EmergencyRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EmergencyRed,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Text(
                    text = when {
                        isTest -> strings.testModeHeader
                        outcomeIsError -> strings.alertNotSentHeader
                        outcomeIsPartial -> strings.alertPartiallySentHeader
                        isDispatched -> strings.alertSentHeader
                        isSos -> strings.sosDetectedHeader
                        else -> strings.fallDetectedHeader
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = EmergencyRed,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = when {
                        isDispatched -> outcomeText ?: strings.dispatchInProgress
                        isTest -> strings.testModeSubtitle
                        isSos -> strings.sosDetectedSubtitle
                        else -> strings.sensorDetectedSubtitle
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            if (!isDispatched) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    EmergencyRed.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(4.dp, EmergencyRed, CircleShape)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$secondsLeft",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = strings.secondsUnit,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                val cardColor = when {
                    outcomeIsError -> EmergencyRed
                    outcomeIsPartial -> Color(0xFFFFB74D)
                    else -> StatusConnected
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (outcomeIsError || outcomeIsPartial) {
                                Icons.Default.Warning
                            } else {
                                Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = cardColor
                        )
                        Column {
                            Text(
                                text = outcomeText ?: strings.dispatchInProgress,
                                fontWeight = FontWeight.Bold,
                                color = cardColor
                            )
                            if (!outcomeIsError && settings.enableCallAndVoice &&
                                (resultStatus == DispatchStatus.SMS_SENT ||
                                    resultStatus == DispatchStatus.SMS_PARTIAL)
                            ) {
                                Text(
                                    text = strings.callAndVoiceActiveCard,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1414)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
                        Text(
                            text = strings.recipientsLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (validContacts.isEmpty()) {
                        Text(
                            text = strings.recipientNotSet,
                            color = EmergencyRed,
                            fontSize = 12.sp
                        )
                    } else {
                        validContacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 26.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contact.displayName,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = contact.phone,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${strings.locationTitle}: ${locationInfo.address}",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Message, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${strings.smsPreviewLabel}: \"$liveMessagePreview\"",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isDispatched) {
                    Button(
                        onClick = {
                            isDispatched = true
                            onDispatched()
                            app.startDispatch(source, isTest)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("send_now_button")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.sendNowButton, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("cancel_alert_button")
                ) {
                    Text(
                        text = if (isDispatched) strings.closeScreenButton else strings.cancelAlertButton,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
