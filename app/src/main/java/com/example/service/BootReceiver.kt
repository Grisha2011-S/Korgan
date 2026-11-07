package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Возобновляет мониторинг после перезагрузки телефона или обновления
 * приложения. Без этого после ребута датчик оставался без связи до тех пор,
 * пока пользователь вручную не откроет приложение.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT_POWERON -> {
                FallDetectionService.startService(context)
            }
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
