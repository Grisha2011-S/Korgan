package com.example.data

import java.util.UUID

data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = ""
) {
    val displayName: String
        get() = name.trim().ifBlank { phone.trim() }
}

fun isValidPhoneNumber(raw: String): Boolean {
    val value = raw.trim()
    if (value.isEmpty()) return false
    if (!value.matches(Regex("^[+0-9()\\-\\s]+$"))) return false
    return value.count { it.isDigit() } in 7..15
}

fun normalizePhoneNumber(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    val digits = value.filter { it.isDigit() }
    return if (value.startsWith("+")) "+$digits" else digits
}
