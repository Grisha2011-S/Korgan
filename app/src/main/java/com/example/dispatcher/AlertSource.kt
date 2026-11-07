package com.example.dispatcher

/** Источники тревоги. Значения ESP32_* должны совпадать с протоколом прошивки. */
object AlertSource {
    const val ESP32_FALL = "ESP32_BLE"
    const val ESP32_SOS = "ESP32_SOS"
    const val MANUAL_TEST = "MANUAL_TEST"
    const val NOTIFICATION_TEST = "NOTIFICATION_TEST"

    fun isSos(source: String): Boolean = source == ESP32_SOS
}
