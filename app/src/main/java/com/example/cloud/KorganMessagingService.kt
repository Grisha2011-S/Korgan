package com.example.cloud

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.FallGuardApplication
import com.example.MainActivity
import com.example.R
import com.example.i18n.FamilyText
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class KorganMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        (application as FallGuardApplication).familyCloud.onNewPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as FallGuardApplication
        val data = message.data
        val text = FamilyText.get(app.preferences.getSettings().appLanguage)
        when (data["type"]) {
            "alert" -> {
                if (app.familyCloud.state.value.role != PhoneRole.CAREGIVER) return
                CaregiverAlertActivity.show(
                    this,
                    RemoteAlert(
                        id = data["alert_id"].orEmpty(),
                        wearerName = data["wearer_name"].orEmpty(),
                        kind = data["kind"].orEmpty(),
                        title = data["title"].orEmpty(),
                        body = data["body"].orEmpty(),
                        latitude = data["latitude"]?.toDoubleOrNull(),
                        longitude = data["longitude"]?.toDoubleOrNull(),
                        address = data["address"].orEmpty(),
                        battery = data["battery"]?.toIntOrNull(),
                        createdMs = System.currentTimeMillis(),
                        ackedByName = null
                    )
                )
            }
            "ack" -> {
                val name = data["name"].orEmpty()
                app.familyCloud.onAckPush(data["alert_id"].orEmpty(), name)
                notify(this, ID_ACK, text.ackNotificationTitle, text.ackNotificationBody.format(name))
            }
            "linked" -> notify(this, ID_LINKED, "Қорған", text.linkedNotification.format(data["name"].orEmpty()))
        }
    }

    companion object {
        private const val ID_ACK = 2001
        private const val ID_LINKED = 2002

        private fun notify(context: Context, id: Int, title: String, body: String) {
            val open = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, FallGuardApplication.CHANNEL_INFO_ID)
                .setSmallIcon(R.drawable.ic_fall_guard_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notification)
            }
        }
    }
}
