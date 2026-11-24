package vige.land.muchopesos.domain.logic

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import vige.land.muchopesos.domain.model.CurrencySpec

/**
 * Strict user-input-to-[BigDecimal] parser, UI currency formatter, and canonical plain-string
 * serializer for Google Sheets storage.
 */
object MoneyFormatter {

    private val SUFFIX_SYMBOLS = setOf("kr", "zł")

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
     * Formats [amount] with grouping separators, fixed currency precision, and [currency] symbol.
     */
    fun format(amount: BigDecimal, currency: CurrencySpec): String {
        val scaled = amount.setScale(currency.fractionDigits, RoundingMode.HALF_UP)
        val isNegative = scaled.compareTo(BigDecimal.ZERO) < 0
        val absValue = scaled.abs()
        val formattedNumber = formatNumber(absValue, currency.fractionDigits)
        val sign = if (isNegative) "-" else ""

        return applyCurrencySymbol(sign = sign, number = formattedNumber, currency = currency)
    }

    /**
     * Formats [amount] with an explicit `+` / `-` sign prefix when non-zero.
     */
    fun formatSigned(amount: BigDecimal, currency: CurrencySpec): String {
        val scaled = amount.setScale(currency.fractionDigits, RoundingMode.HALF_UP)
        val cmp = scaled.compareTo(BigDecimal.ZERO)
        val sign = when {
            cmp > 0 -> "+"
            cmp < 0 -> "-"
            else -> ""
        }
        val formattedNumber = formatNumber(scaled.abs(), currency.fractionDigits)
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

    private fun formatNumber(absAmount: BigDecimal, fractionDigits: Int): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = if (fractionDigits <= 0) {
            "#,##0"
        } else {
            "#,##0." + "0".repeat(fractionDigits)
        }
        val formatter = DecimalFormat(pattern, symbols).apply {
            roundingMode = RoundingMode.HALF_UP
        }
        return formatter.format(absAmount)
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
