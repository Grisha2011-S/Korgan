package com.example.data

/**
 * Шаблоны основной фразы экстренного SMS.
 *
 * Текст намеренно нейтральный по полу и не содержит утверждений, которые
 * приложение не может проверить. Время, адрес, координаты и карта
 * добавляются автоматически в SmsMessageBuilder.
 */
object DefaultTemplates {
    const val RU = "{имя} не отвечает. Возможно, произошло падение и нужна помощь."
    const val KK = "{имя} жауап бермейді. Құлап қалуы мүмкін, көмек қажет."
    const val EN = "{name} is not responding. A fall may have occurred and help is needed."

    private val oldRu = "Здравствуйте! {имя} упал и не может встать, находится {адрес}!"
    private val oldKk = "Сәлеметсіз бе! {имя} құлап қалды және тұра алмайды, мекенжайы: {адрес}!"
    private val oldEn = "Hello! {name} has fallen and cannot get up, located at {address}!"

    fun forLanguage(languageCode: String): String = when (languageCode.lowercase()) {
        "kk", "kaz", "kz" -> KK
        "en", "eng" -> EN
        else -> RU
    }

    /** Старые и новые значения: если пользователь не менял шаблон, его можно обновить. */
    fun allKnown(): List<String> = listOf(RU, KK, EN, oldRu, oldKk, oldEn)
}
