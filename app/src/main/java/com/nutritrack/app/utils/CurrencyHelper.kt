package com.nutritrack.app.utils

/**
 * Валюты приложения. Символ подставляется в цены и PDF.
 * "auto" — символ выбирается по текущему языку приложения.
 */
object CurrencyHelper {

    data class Currency(val code: String, val symbol: String, val label: String)

 /** Доступные валюты (code символ + подпись для диалога). */
    val currencies: List<Currency> = listOf(
        Currency("auto", "",   "🌐"),       // подпись дополняется в диалоге
        Currency("RUB", "₽",   "🇷🇺 ₽ RUB"),
        Currency("USD", "$",   "🇺🇸 $ USD"),
        Currency("EUR", "€",   "🇪🇺 € EUR"),
        Currency("GBP", "£",   "🇬🇧 £ GBP"),
        Currency("CNY", "¥",   "🇨🇳 ¥ CNY"),
        Currency("JPY", "¥",   "🇯🇵 ¥ JPY"),
        Currency("KRW", "₩",   "🇰🇷 ₩ KRW"),
        Currency("TRY", "₺",   "🇹🇷 ₺ TRY"),
        Currency("INR", "₹",   "🇮🇳 ₹ INR"),
        Currency("PLN", "zł",  "🇵🇱 zł PLN"),
        Currency("SEK", "kr",  "🇸🇪 kr SEK"),
        Currency("SAR", "﷼",   "🇸🇦 ﷼ SAR"),
        Currency("BRL", "R$",  "🇧🇷 R$ BRL")
    )

 /** Символ валюты по коду языка (для режима "auto"). */
    private fun symbolForLanguage(lang: String): String = when (lang) {
        "ru" -> "₽"
        "en" -> "$"
        "es", "de", "fr", "it", "nl", "fi" -> "€"
        "pt" -> "R$"
        "zh", "ja" -> "¥"
        "ko" -> "₩"
        "tr" -> "₺"
        "hi" -> "₹"
        "pl" -> "zł"
        "sv" -> "kr"
        "ar" -> "﷼"
        else -> "$"
    }

 /**
 * Итоговый символ для отображения.
 * Если выбран "auto" — берётся по текущему языку приложения.
 */
    fun symbol(prefs: PrefsManager): String {
        val code = prefs.currency
        if (code == "auto") {
            val lang = prefs.language
            val effective = if (lang == "auto") java.util.Locale.getDefault().language else lang
            return symbolForLanguage(effective)
        }
        return currencies.firstOrNull { it.code == code }?.symbol ?: "$"
    }

    fun symbolForCode(code: String): String =
        currencies.firstOrNull { it.code == code }?.symbol ?: "$"
}
