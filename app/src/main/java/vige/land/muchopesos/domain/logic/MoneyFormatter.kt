package vige.land.muchopesos.domain.logic

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import vige.land.muchopesos.domain.model.CurrencySpec

/**
 * Strict user-input-to-[BigDecimal] parser, UI currency formatter, and canonical plain-string
 * serializer for Google Sheets storage.
 */
object MoneyFormatter {

    private val SUFFIX_SYMBOLS = setOf("kr", "zł")

    /**
     * Set of `(language, country)` pairs supported directly by [NumberFormat.getAvailableLocales].
     */
    private val availableNumberLocaleKeys: Set<Pair<String, String>> by lazy {
        NumberFormat.getAvailableLocales()
            .asSequence()
            .filter { it.language.isNotEmpty() && it.country.isNotEmpty() }
            .map { it.language.lowercase(Locale.ROOT) to it.country.uppercase(Locale.ROOT) }
            .toSet()
    }

    /**
     * Primary formatting language for ISO 3166-1 alpha-2 regions, used when the device display
     * language (e.g. `en`) does not define local number formatting for the selected Android region
     * (e.g. `en-NO` or `en-US-u-rg-nozzzz`).
     */
    private val REGION_PRIMARY_LANGUAGE: Map<String, String> = mapOf(
        "AR" to "es",
        "AT" to "de",
        "AU" to "en",
        "BE" to "nl",
        "BG" to "bg",
        "BR" to "pt",
        "CA" to "en",
        "CH" to "de",
        "CL" to "es",
        "CN" to "zh",
        "CO" to "es",
        "CZ" to "cs",
        "DE" to "de",
        "DK" to "da",
        "EE" to "et",
        "ES" to "es",
        "FI" to "fi",
        "FR" to "fr",
        "GB" to "en",
        "GR" to "el",
        "HR" to "hr",
        "HU" to "hu",
        "ID" to "id",
        "IE" to "en",
        "IL" to "he",
        "IN" to "en",
        "IS" to "is",
        "IT" to "it",
        "JP" to "ja",
        "KR" to "ko",
        "LT" to "lt",
        "LU" to "fr",
        "LV" to "lv",
        "MX" to "es",
        "NL" to "nl",
        "NO" to "nb",
        "NZ" to "en",
        "PL" to "pl",
        "PT" to "pt",
        "RO" to "ro",
        "RU" to "ru",
        "SE" to "sv",
        "SG" to "en",
        "SI" to "sl",
        "SK" to "sk",
        "TH" to "th",
        "TR" to "tr",
        "TW" to "zh",
        "UA" to "uk",
        "US" to "en",
        "VN" to "vi",
        "ZA" to "en",
    )

    /**
     * Official/local languages for multilingual regions where the user's language choice within
     * that region intentionally governs number formatting (e.g. `fr-CA` vs `en-CA`).
     */
    private val REGION_OFFICIAL_LANGUAGES: Map<String, Set<String>> = mapOf(
        "BE" to setOf("nl", "fr", "de"),
        "CA" to setOf("en", "fr"),
        "CH" to setOf("de", "fr", "it", "rm"),
        "FI" to setOf("fi", "sv"),
        "IE" to setOf("en", "ga"),
        "LU" to setOf("lb", "fr", "de"),
        "NO" to setOf("nb", "nn", "no", "se"),
        "SG" to setOf("en", "zh", "ms", "ta"),
        "US" to setOf("en", "es"),
        "ZA" to setOf("en", "af", "zu", "xh"),
    )

    /**
     * Returns Android's active formatting locale (which reflects Regional Preferences / Region).
     */
    fun defaultFormatLocale(): Locale = Locale.getDefault(Locale.Category.FORMAT)

    /**
     * Parses a non-negative monetary amount entered by the user.
     * - Accepts either `.` or `,` as the decimal separator (never both in the same value).
     * - Rejects blank, negative, signed, non-numeric, multiple-separator, or excess-decimal inputs.
     * - Normalizes the resulting [BigDecimal] to [scale] decimal places.
     */
    fun parseUserAmount(input: String, scale: Int = 2): BigDecimal? {
        if (scale < 0) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        var dotCount = 0
        var commaCount = 0
        var digitCount = 0

        for (ch in trimmed) {
            when (ch) {
                in '0'..'9' -> digitCount++
                '.' -> dotCount++
                ',' -> commaCount++
                else -> return null
            }
        }

        if (digitCount == 0) return null
        if (dotCount > 0 && commaCount > 0) return null
        if (dotCount > 1 || commaCount > 1) return null

        val canonical = trimmed.replace(',', '.')
        val dotIndex = canonical.indexOf('.')
        if (dotIndex >= 0) {
            if (scale == 0) return null
            val fractionLength = canonical.length - dotIndex - 1
            if (fractionLength == 0 || fractionLength > scale) return null
        }

        val parsed = runCatching { BigDecimal(canonical) }.getOrNull() ?: return null
        if (parsed.compareTo(BigDecimal.ZERO) < 0) return null

        return parsed.setScale(scale, RoundingMode.UNNECESSARY)
    }

    /**
     * Formats [amount] with locale-aware grouping and decimal separators, fixed currency precision,
     * and [currency] symbol. Defaults to Android's active [Locale.Category.FORMAT] locale.
     */
    fun format(
        amount: BigDecimal,
        currency: CurrencySpec,
        locale: Locale = defaultFormatLocale(),
    ): String {
        val scaled = amount.setScale(currency.fractionDigits, RoundingMode.HALF_UP)
        val isNegative = scaled.compareTo(BigDecimal.ZERO) < 0
        val absValue = scaled.abs()
        val formattedNumber = formatNumber(absValue, currency.fractionDigits, locale)
        val sign = if (isNegative) "-" else ""

        return applyCurrencySymbol(sign = sign, number = formattedNumber, currency = currency)
    }

    /**
     * Formats [amount] with an explicit `+` / `-` sign prefix when non-zero, using [locale]'s
     * regional number formatting conventions.
     */
    fun formatSigned(
        amount: BigDecimal,
        currency: CurrencySpec,
        locale: Locale = defaultFormatLocale(),
    ): String {
        val scaled = amount.setScale(currency.fractionDigits, RoundingMode.HALF_UP)
        val cmp = scaled.compareTo(BigDecimal.ZERO)
        val sign = when {
            cmp > 0 -> "+"
            cmp < 0 -> "-"
            else -> ""
        }
        val formattedNumber = formatNumber(scaled.abs(), currency.fractionDigits, locale)
        return applyCurrencySymbol(sign = sign, number = formattedNumber, currency = currency)
    }

    /**
     * Serializes [amount] to a canonical `.`-decimal plain string for Google Sheets storage.
     */
    fun toSheetPlainString(amount: BigDecimal, scale: Int = 2): String {
        val scaled = amount.setScale(scale, RoundingMode.HALF_UP)
        val normalized = if (scaled.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        } else {
            scaled
        }
        return normalized.toPlainString()
    }

    /**
     * Formats a non-negative [absAmount] with [fractionDigits] decimal places according to
     * [locale]'s effective region formatting conventions.
     */
    internal fun formatNumber(
        absAmount: BigDecimal,
        fractionDigits: Int,
        locale: Locale = defaultFormatLocale(),
    ): String {
        val digits = fractionDigits.coerceAtLeast(0)
        val resolvedLocale = resolveFormattingLocale(locale)
        val formatter = NumberFormat.getNumberInstance(resolvedLocale).apply {
            isGroupingUsed = true
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            roundingMode = RoundingMode.HALF_UP
        }
        return formatter.format(absAmount)
    }

    /**
     * Resolves the effective formatting [Locale] from Android's locale and regional preferences.
     *
     * Handles BCP-47 `-u-rg-` region overrides (e.g. `en-US-u-rg-nozzzz`) and English-in-region
     * locales (e.g. `en-NO`, `en-FR`) so number grouping and decimal separators follow the
     * selected Android region even when CLDR does not define separate symbols for that
     * language-region combination.
     */
    internal fun resolveFormattingLocale(locale: Locale): Locale {
        val effectiveRegion = resolveEffectiveRegion(locale)
        if (effectiveRegion.isEmpty()) return locale

        val language = locale.language.lowercase(Locale.ROOT)
        val originalRegion = locale.country.uppercase(Locale.ROOT)

        val regionAdjusted = if (effectiveRegion != originalRegion && language.isNotEmpty()) {
            buildLocale(language = language, region = effectiveRegion, source = locale)
        } else {
            locale
        }

        val officialLanguages = REGION_OFFICIAL_LANGUAGES[effectiveRegion]
        val primaryLanguage = REGION_PRIMARY_LANGUAGE[effectiveRegion]
            ?: effectiveRegion.lowercase(Locale.ROOT)

        val isOfficialForRegion = language.isNotEmpty() && (
            language == primaryLanguage ||
                (officialLanguages != null && language in officialLanguages)
            )
        val hasDirectSupport = language.isNotEmpty() &&
            (language to effectiveRegion) in availableNumberLocaleKeys

        if (isOfficialForRegion && hasDirectSupport) {
            return regionAdjusted
        }

        if (hasDirectSupport && !matchesBaseLanguageSymbols(regionAdjusted, language)) {
            return regionAdjusted
        }

        val fallbackKey = primaryLanguage to effectiveRegion
        if (fallbackKey in availableNumberLocaleKeys) {
            return buildLocale(language = primaryLanguage, region = effectiveRegion, source = locale)
        }

        return regionAdjusted
    }

    private fun resolveEffectiveRegion(locale: Locale): String {
        val rg = locale.getUnicodeLocaleType("rg")
        if (!rg.isNullOrEmpty()) {
            if (rg.length >= 2) {
                val alpha2 = rg.substring(0, 2).uppercase(Locale.ROOT)
                if (alpha2.all { it in 'A'..'Z' }) {
                    return alpha2
                }
            }
            if (rg.length >= 3) {
                val numeric = rg.substring(0, 3)
                if (numeric.all { it.isDigit() }) {
                    return numeric
                }
            }
        }
        return locale.country.uppercase(Locale.ROOT)
    }

    private fun matchesBaseLanguageSymbols(locale: Locale, language: String): Boolean {
        val baseSymbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag(language))
        val targetSymbols = DecimalFormatSymbols.getInstance(locale)
        return baseSymbols.decimalSeparator == targetSymbols.decimalSeparator &&
            baseSymbols.groupingSeparator == targetSymbols.groupingSeparator
    }

    private fun buildLocale(language: String, region: String, source: Locale): Locale {
        return runCatching {
            val builder = Locale.Builder()
                .setLanguage(language)
                .setRegion(region)
            val numberingSystem = source.getUnicodeLocaleType("nu")
            if (!numberingSystem.isNullOrEmpty()) {
                builder.setUnicodeLocaleKeyword("nu", numberingSystem)
            }
            builder.build()
        }.getOrDefault(source)
    }

    private fun applyCurrencySymbol(
        sign: String,
        number: String,
        currency: CurrencySpec,
    ): String {
        val symbol = currency.symbol
        return when {
            symbol in SUFFIX_SYMBOLS -> "$sign$number $symbol"
            symbol.isNotEmpty() && symbol.all { it.isLetter() } -> "$sign$symbol $number"
            else -> "$sign$symbol$number"
        }
    }
}
