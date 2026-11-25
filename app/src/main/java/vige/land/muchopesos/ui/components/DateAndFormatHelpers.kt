package vige.land.muchopesos.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import vige.land.muchopesos.domain.logic.MoneyFormatter
import vige.land.muchopesos.domain.model.CurrencySpec
import vige.land.muchopesos.domain.model.SplitType

private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val SHORT_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM", Locale.US)
private val SHORT_MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMM ''yy", Locale.US)
private val TIME_HH_MM_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private val THOUSAND = BigDecimal("1000")
private val TEN_THOUSAND = BigDecimal("10000")

fun formatShortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    return if (date.year == today.year) {
        date.format(SHORT_DATE_FORMATTER)
    } else {
        date.format(FULL_DATE_FORMATTER)
    }
}

fun formatFullDate(date: LocalDate): String = date.format(FULL_DATE_FORMATTER)

fun formatMonthYearHeader(date: LocalDate): String = date.format(MONTH_YEAR_FORMATTER)

fun formatYearMonth(yearMonth: YearMonth): String = yearMonth.format(MONTH_YEAR_FORMATTER)

fun formatShortMonth(
    yearMonth: YearMonth,
    referenceYear: Int = YearMonth.now().year,
): String = if (yearMonth.year == referenceYear) {
    yearMonth.format(SHORT_MONTH_FORMATTER)
} else {
    yearMonth.format(SHORT_MONTH_YEAR_FORMATTER)
}

/**
 * Compact [BigDecimal] currency label for tight bar-chart annotations (e.g. "$516", "$1.2k",
 * or "1,2k kr" in comma-decimal regions).
 */
fun formatCompactMoney(
    amount: BigDecimal,
    currency: CurrencySpec,
    locale: Locale = MoneyFormatter.defaultFormatLocale(),
): String {
    val absAmount = amount.abs()
    if (absAmount.compareTo(BigDecimal.ZERO) == 0) {
        return MoneyFormatter.format(BigDecimal.ZERO, currency.copy(fractionDigits = 0), locale)
    }
    return when {
        absAmount.compareTo(TEN_THOUSAND) >= 0 -> {
            val k = MoneyFormatter.formatNumber(
                absAmount = absAmount.divide(THOUSAND, 0, RoundingMode.HALF_UP),
                fractionDigits = 0,
                locale = locale,
            )
            applyCompactSymbol("${k}k", currency)
        }
        absAmount.compareTo(THOUSAND) >= 0 -> {
            val scaledK = absAmount.divide(THOUSAND, 1, RoundingMode.HALF_UP).stripTrailingZeros()
            val digits = scaledK.scale().coerceAtLeast(0)
            val k = MoneyFormatter.formatNumber(
                absAmount = scaledK,
                fractionDigits = digits,
                locale = locale,
            )
            applyCompactSymbol("${k}k", currency)
        }
        else -> MoneyFormatter.format(absAmount, currency.copy(fractionDigits = 0), locale)
    }
}

private fun applyCompactSymbol(compactNumber: String, currency: CurrencySpec): String {
    val symbol = currency.symbol
    return when {
        symbol == "kr" || symbol == "zł" -> "$compactNumber $symbol"
        symbol.isNotEmpty() && symbol.all { it.isLetter() } -> "$symbol $compactNumber"
        else -> "$symbol$compactNumber"
    }
}

fun formatSyncTimestamp(
    lastSyncEpochMs: Long?,
    nowMs: Long = System.currentTimeMillis(),
): String {
    if (lastSyncEpochMs == null || lastSyncEpochMs <= 0L) {
        return "Not synced yet"
    }
    val diffMs = (nowMs - lastSyncEpochMs).coerceAtLeast(0L)
    val diffMinutes = diffMs / 60_000L
    val syncTime = Instant.ofEpochMilli(lastSyncEpochMs)
        .atZone(ZoneId.systemDefault())
    val timeStr = syncTime.format(TIME_HH_MM_FORMATTER)

    return when {
        diffMs < 45_000L -> "Synced just now"
        diffMinutes < 60L -> "Synced ${diffMinutes}m ago • $timeStr"
        diffMinutes < 24 * 60L -> "Synced ${diffMinutes / 60L}h ago • $timeStr"
        else -> "Synced ${syncTime.toLocalDate().format(SHORT_DATE_FORMATTER)} • $timeStr"
    }
}

fun formatSplitTypeLabel(splitType: SplitType, splitMeta: String = ""): String = when (splitType) {
    SplitType.EQUAL -> "Equal (50/50)"
    SplitType.EXACT -> "Exact amounts"
    SplitType.PERCENTAGE -> if (splitMeta.isNotBlank()) "Percentage ($splitMeta%)" else "Percentage"
    SplitType.FULL -> "Full (100%)"
    SplitType.SETTLEMENT -> "Settlement"
}

fun localDateToUtcEpochMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun utcEpochMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = localDateToUtcEpochMillis(initialDate),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        onDateSelected(utcEpochMillisToLocalDate(selectedMillis))
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
