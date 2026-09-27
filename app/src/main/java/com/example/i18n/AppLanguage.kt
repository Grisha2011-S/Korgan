package com.example.i18n

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val flag: String,
    val nativeSubtitle: String
) {
    RUSSIAN("ru", "Русский", "🇷🇺", "Язык интерфейса"),
    KAZAKH("kk", "Қазақша", "🇰🇿", "Интерфейс тілі"),
    ENGLISH("en", "English", "🇬🇧", "Interface language");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: RUSSIAN
        }
    }
}
