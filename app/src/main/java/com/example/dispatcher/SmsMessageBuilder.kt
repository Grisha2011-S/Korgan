package com.example.dispatcher

import com.example.data.DefaultTemplates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class SmsMessageBuilder {

    fun defaultTemplate(languageCode: String): String = DefaultTemplates.forLanguage(languageCode)

    fun build(
        source: String,
        userName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        template: String,
        languageCode: String,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val lang = normalize(languageCode)
        val labels = labels(lang)
        val name = userName.trim().ifBlank { labels.fallbackName }
        val place = address.trim().ifBlank { labels.unknownPlace }
        val time = formatTime(timestamp, lang)
        val hasCoordinates = latitude != 0.0 || longitude != 0.0
        val mapUrl = if (hasCoordinates) {
            "https://maps.google.com/?q=" + String.format(Locale.US, "%.5f,%.5f", latitude, longitude)
        } else {
            ""
        }

        val chosenTemplate = template.trim().ifBlank { defaultTemplate(lang) }
        val templateHasMap = chosenTemplate.contains("{карта}") || chosenTemplate.contains("{map}")
        val body = renderTemplate(
            template = chosenTemplate,
            userName = name,
            address = place,
            timestamp = timestamp,
            languageCode = lang,
            mapUrl = mapUrl
        )

        return buildString {
            append(if (AlertSource.isSos(source)) labels.sosHeader else labels.fallHeader)
            append('\n')
            append(body)
            append('\n')
            append(labels.time).append(": ").append(time)
            append('\n')
            append(labels.place).append(": ").append(place)
            if (hasCoordinates) {
                append('\n')
                append(labels.coordinates).append(": ")
                append(String.format(Locale.US, "%.5f, %.5f", latitude, longitude))
                if (accuracyMeters > 0f) {
                    append(" (±").append(accuracyMeters.roundToInt()).append(' ').append(labels.meters).append(')')
                }
                if (!templateHasMap) {
                    append('\n')
                    append(labels.map).append(": ").append(mapUrl)
                }
            }
            append('\n')
            append(labels.footer(name))
        }.trim()
    }

    fun buildVoiceMessage(
        source: String,
        userName: String,
        address: String,
        languageCode: String
    ): String {
        val lang = normalize(languageCode)
        val labels = labels(lang)
        val name = userName.trim().ifBlank { labels.fallbackName }
        val place = address.trim().ifBlank { labels.unknownPlace }
        return if (AlertSource.isSos(source)) labels.voiceSos(name, place) else labels.voiceFall(name, place)
    }

    fun buildCallVoiceMessage(
        source: String,
        userName: String,
        address: String,
        languageCode: String
    ): String {
        val intro = labels(normalize(languageCode)).callIntro
        return "$intro ${buildVoiceMessage(source, userName, address, languageCode)}"
    }

    fun buildTestCallVoiceMessage(userName: String, languageCode: String): String {
        val labels = labels(normalize(languageCode))
        val name = userName.trim().ifBlank { labels.fallbackName }
        return "${labels.callIntro} ${labels.voiceTest(name)}"
    }

    fun renderTemplate(
        template: String,
        userName: String,
        address: String,
        timestamp: Long = System.currentTimeMillis(),
        languageCode: String = "ru",
        mapUrl: String = ""
    ): String {
        val lang = normalize(languageCode)
        val labels = labels(lang)
        val name = userName.trim().ifBlank { labels.fallbackName }
        val place = address.trim().ifBlank { labels.unknownPlace }
        val time = formatTime(timestamp, lang)
        return template
            .replace("{имя пользователя}", name)
            .replace("{имя}", name)
            .replace("{name}", name)
            .replace("{адрес}", place)
            .replace("{address}", place)
            .replace("{улица}", place)
            .replace("{время}", time)
            .replace("{time}", time)
            .replace("{карта}", mapUrl)
            .replace("{map}", mapUrl)
            .trim()
    }

    fun formatTime(timestamp: Long, languageCode: String): String {
        val locale = when (normalize(languageCode)) {
            "kk" -> Locale("kk", "KZ")
            "en" -> Locale.US
            else -> Locale("ru", "RU")
        }
        return SimpleDateFormat("dd.MM.yyyy, HH:mm", locale).format(Date(timestamp))
    }

    private fun normalize(languageCode: String): String = when (languageCode.lowercase(Locale.US)) {
        "kk", "kaz", "kz" -> "kk"
        "en", "eng" -> "en"
        else -> "ru"
    }

    private fun labels(languageCode: String): Labels = when (languageCode) {
        "kk" -> Labels(
            fallHeader = "ДАБЫЛ: құлау мүмкіндігі",
            sosHeader = "ДАБЫЛ: SOS түймесі басылды",
            time = "Уақыты",
            place = "Орны",
            coordinates = "Координаттар",
            map = "Карта",
            meters = "м",
            unknownPlace = "анықталмаған",
            fallbackName = "Пайдаланушы",
            footer = { name -> "«Қорған» қосымшасының автоматты хабарламасы. $name байланысыңыз." },
            voiceFall = { name, place -> "Дабыл. $name жауап бермейді. Құлап қалуы мүмкін. Мекенжайы: $place. Тексеріңіз." },
            voiceSos = { name, place -> "Дабыл. SOS түймесі басылды. $name көмекке мұқтаж болуы мүмкін. Мекенжайы: $place. Тексеріңіз." },
            callIntro = "Бұл «Қорған» қосымшасының автоматты қоңырауы.",
            voiceTest = { name -> "Бұл тексеру қоңырауы. Егер сіз осы хабарламаны естісеңіз, $name үшін дауыстық хабарландыру жұмыс істейді." }
        )
        "en" -> Labels(
            fallHeader = "EMERGENCY: possible fall",
            sosHeader = "EMERGENCY: SOS button pressed",
            time = "Time",
            place = "Location",
            coordinates = "Coordinates",
            map = "Map",
            meters = "m",
            unknownPlace = "not determined",
            fallbackName = "User",
            footer = { name -> "Automatic message from the Qorghan app. Please contact $name." },
            voiceFall = { name, place -> "Emergency. $name is not responding. A fall may have occurred. Location: $place. Please check." },
            voiceSos = { name, place -> "Emergency. The SOS button was pressed. $name may need help. Location: $place. Please check." },
            callIntro = "This is an automatic call from the Qorghan app.",
            voiceTest = { name -> "This is a test call. If you can hear this message, voice alerts for $name are working." }
        )
        else -> Labels(
            fallHeader = "ТРЕВОГА: возможно падение",
            sosHeader = "ТРЕВОГА: нажата кнопка SOS",
            time = "Время",
            place = "Место",
            coordinates = "Координаты",
            map = "Карта",
            meters = "м",
            unknownPlace = "не определено",
            fallbackName = "Пользователь",
            footer = { name -> "Автоматическое сообщение приложения «Қорған». Пожалуйста, свяжитесь с $name." },
            voiceFall = { name, place -> "Тревога. $name не отвечает. Возможно, произошло падение. Адрес: $place. Пожалуйста, проверьте." },
            voiceSos = { name, place -> "Тревога. Нажата кнопка SOS. $name может нуждаться в помощи. Адрес: $place. Пожалуйста, проверьте." },
            callIntro = "Это автоматический звонок приложения «Қорған».",
            voiceTest = { name -> "Это проверочный звонок. Если вы слышите это сообщение, голосовое оповещение для $name работает." }
        )
    }

    private data class Labels(
        val fallHeader: String,
        val sosHeader: String,
        val time: String,
        val place: String,
        val coordinates: String,
        val map: String,
        val meters: String,
        val unknownPlace: String,
        val fallbackName: String,
        val footer: (String) -> String,
        val voiceFall: (String, String) -> String,
        val voiceSos: (String, String) -> String,
        val callIntro: String,
        val voiceTest: (String) -> String
    )
}
