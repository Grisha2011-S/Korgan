package com.example.data

import java.util.UUID

/**
 * Получатель экстренного SMS.
 *
 * Имя необязательно: если его нет, в интерфейсе показывается номер.
 * Первый контакт в списке используется для экстренного звонка.
 */
data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = ""
) {
    val displayName: String
        get() = name.trim().ifBlank { phone.trim() }
}

/** Проверка номера: 7–15 цифр, допускаются +, пробелы, скобки и дефисы. */
fun isValidPhoneNumber(raw: String): Boolean {
    val value = raw.trim()
    if (value.isEmpty()) return false
    if (!value.matches(Regex("^[+0-9()\\-\\s]+$"))) return false
    return value.count { it.isDigit() } in 7..15
}

/** Оставляет ведущий «+» и только цифры — в таком виде номер уходит в SMS/звонок. */
fun normalizePhoneNumber(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    val digits = value.filter { it.isDigit() }
    return if (value.startsWith("+")) "+$digits" else digits
}
