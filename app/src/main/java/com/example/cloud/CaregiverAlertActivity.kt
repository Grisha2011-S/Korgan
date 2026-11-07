package com.example.cloud

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.FallGuardApplication
import com.example.R
import com.example.audio.AlarmSoundPlayer
import com.example.i18n.FamilyText
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusConnected
import kotlinx.coroutines.launch

class CaregiverAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alert = alertFrom(intent)
        setContent {
            MyApplicationTheme {
                CaregiverAlertScreen(
                    app = application as FallGuardApplication,
                    alert = alert,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AlarmSoundPlayer.stop()
    }

    companion object {
        private const val NOTIFICATION_ID = 1999
        private const val EXTRA_ID = "alert_id"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_NAME = "wearer_name"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_LAT = "latitude"
        private const val EXTRA_LON = "longitude"
        private const val EXTRA_BATTERY = "battery"

        private fun alertFrom(intent: Intent): RemoteAlert = RemoteAlert(
            id = intent.getStringExtra(EXTRA_ID).orEmpty(),
            wearerName = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            kind = intent.getStringExtra(EXTRA_KIND).orEmpty(),
            title = "",
            body = intent.getStringExtra(EXTRA_BODY).orEmpty(),
            latitude = intent.getStringExtra(EXTRA_LAT)?.toDoubleOrNull(),
            longitude = intent.getStringExtra(EXTRA_LON)?.toDoubleOrNull(),
            address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty(),
            battery = intent.getStringExtra(EXTRA_BATTERY)?.toIntOrNull(),
            createdMs = System.currentTimeMillis(),
            ackedByName = null
        )

        fun alertTitle(context: Context, alert: RemoteAlert): String {
            val app = context.applicationContext as FallGuardApplication
            val text = FamilyText.get(app.preferences.getSettings().appLanguage)
            return when (alert.kind) {
                "SOS" -> text.alertNotificationSos
                "TEST" -> text.alertNotificationTest
                else -> text.alertNotificationFall
            }.format(alert.wearerName)
        }

        fun show(context: Context, alert: RemoteAlert) {
            runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Qorghan:CaregiverAlert"
                ).acquire(30_000L)
            }
            AlarmSoundPlayer.start(context)

            val intent = Intent(context, CaregiverAlertActivity::class.java).apply {
                putExtra(EXTRA_ID, alert.id)
                putExtra(EXTRA_KIND, alert.kind)
                putExtra(EXTRA_NAME, alert.wearerName)
                putExtra(EXTRA_BODY, alert.body)
                putExtra(EXTRA_ADDRESS, alert.address)
                putExtra(EXTRA_LAT, alert.latitude?.toString())
                putExtra(EXTRA_LON, alert.longitude?.toString())
                putExtra(EXTRA_BATTERY, alert.battery?.toString())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(context, FallGuardApplication.CHANNEL_ALERT_ID)
                .setSmallIcon(R.drawable.ic_fall_guard_logo)
                .setContentTitle(alertTitle(context, alert))
                .setContentText(alert.address.ifBlank { alert.body })
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pending)
                .setAutoCancel(true)
            val canFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                manager.canUseFullScreenIntent()
            if (canFullScreen) builder.setFullScreenIntent(pending, true)
            runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
            runCatching { context.startActivity(intent) }
        }

        fun cancel(context: Context) {
            AlarmSoundPlayer.stop()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        }

        fun openMap(context: Context, alert: RemoteAlert) {
            val lat = alert.latitude
            val lon = alert.longitude
            val uri = if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
                Uri.parse("geo:$lat,$lon?q=$lat,$lon")
            } else {
                Uri.parse("geo:0,0?q=" + Uri.encode(alert.address))
            }
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                val web = if (lat != null && lon != null) {
                    "https://maps.google.com/?q=$lat,$lon"
                } else {
                    "https://maps.google.com/?q=" + Uri.encode(alert.address)
                }
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

@Composable
private fun CaregiverAlertScreen(app: FallGuardApplication, alert: RemoteAlert, onClose: () -> Unit) {
    val language = app.preferences.getSettings().appLanguage
    val text = FamilyText.get(language)
    val scope = rememberCoroutineScope()
    var ackedBy by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = Color(0xFF0F0505)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                imageVector = if (ackedBy != null) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (ackedBy != null) StatusConnected else EmergencyRed,
                modifier = Modifier.size(84.dp)
            )
            Text(
                text = CaregiverAlertActivity.alertTitle(app, alert),
                color = EmergencyRed,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            if (alert.address.isNotBlank()) {
                Text(alert.address, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            alert.battery?.let {
                Text(text.batteryLabel.format(it), color = Color.White.copy(alpha = 0.7f))
            }
            if (alert.body.isNotBlank()) {
                Text(alert.body, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))

            if (ackedBy == null) {
                Button(
                    onClick = {
                        sending = true
                        AlarmSoundPlayer.stop()
                        scope.launch {
                            app.familyCloud.ack(alert.id)
                                .onSuccess { ackedBy = it.ackedByName ?: app.familyCloud.state.value.myName }
                                .onFailure { error = text.serverError.format(it.message ?: "") }
                            sending = false
                        }
                    },
                    enabled = !sending && alert.id.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusConnected),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Text(if (sending) text.connecting else text.ackButton, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(text.ackedBy.format(ackedBy), color = StatusConnected, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            error?.let { Text(it, color = EmergencyRed, textAlign = TextAlign.Center) }

            OutlinedButton(
                onClick = { CaregiverAlertActivity.openMap(app, alert) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Map, contentDescription = null)
                Text("  " + text.openMap, color = Color.White, fontSize = 16.sp)
            }
            OutlinedButton(
                onClick = {
                    CaregiverAlertActivity.cancel(app)
                    onClose()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text.closeButton, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
