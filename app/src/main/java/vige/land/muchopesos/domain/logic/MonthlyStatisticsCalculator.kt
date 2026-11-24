package vige.land.muchopesos.domain.logic

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import vige.land.muchopesos.domain.model.ActiveTransaction
import vige.land.muchopesos.domain.model.CategoryGroupMonthlyTotal
import vige.land.muchopesos.domain.model.CategoryMonthlyTotal
import vige.land.muchopesos.domain.model.MonthOverMonthDelta
import vige.land.muchopesos.domain.model.MonthlyPartnerTotals
import vige.land.muchopesos.domain.model.MonthlyStatisticsReport
import vige.land.muchopesos.domain.model.MonthlySummary
import vige.land.muchopesos.domain.model.MonthlyTrendPoint
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.RecordType

/**
 * Pure [BigDecimal] monthly spending statistics calculator.
 *
 * Aggregates [ActiveTransaction] records by [YearMonth], excluding [RecordType.SETTLEMENT]
 * from spending totals, partner shares, and category breakdowns while tracking settlements
 * separately per month.
 */
object MonthlyStatisticsCalculator {
    private const val PERCENT_SCALE = 1
    private val HUNDRED = BigDecimal("100")
    private val HUNDRED_PERCENT = BigDecimal("100.0")
    private val ZERO_PERCENT = BigDecimal("0.0")

    fun compute(
        transactions: List<ActiveTransaction>,
        scale: Int = 2,
    ): MonthlyStatisticsReport {
        require(scale >= 0) { "Currency scale must be non-negative" }

        val validTransactions = transactions.filter { it.recordType != RecordType.VOID }
        if (validTransactions.isEmpty()) {
            return MonthlyStatisticsReport(months = emptyList(), scale = scale)
        }

        val months = validTransactions
            .groupBy { YearMonth.from(it.date) }
            .map { (yearMonth, monthTxs) ->
                summarizeMonth(yearMonth = yearMonth, transactions = monthTxs, scale = scale)
            }
            .sortedByDescending { it.yearMonth }

        return MonthlyStatisticsReport(months = months, scale = scale)
    }

    fun emptySummary(
        yearMonth: YearMonth,
        scale: Int = 2,
    ): MonthlySummary {
        require(scale >= 0) { "Currency scale must be non-negative" }
        val zero = scaledZero(scale)
        return MonthlySummary(
            yearMonth = yearMonth,
            totalExpenses = zero,
            expenseCount = 0,
            partnerTotals = MonthlyPartnerTotals(
                partnerAShare = zero,
                partnerBShare = zero,
                partnerAPaid = zero,
                partnerBPaid = zero,
                partnerASharePercent = ZERO_PERCENT,
                partnerBSharePercent = ZERO_PERCENT,
            ),
            byCategory = emptyList(),
            byCategoryGroup = emptyList(),
            settlementTotal = zero,
            settlementCount = 0,
        )
    }

    /**
     * Returns a contiguous chronological list of [windowSize] months ending at [anchorMonth],
     * filling any missing months with scaled zero totals.
     */
    fun buildTrendWindow(
        report: MonthlyStatisticsReport,
        anchorMonth: YearMonth,
        windowSize: Int = 6,
    ): List<MonthlyTrendPoint> {
        if (windowSize <= 0) return emptyList()
        val zero = scaledZero(report.scale)
        return ((windowSize - 1) downTo 0).map { offset ->
            val ym = anchorMonth.minusMonths(offset.toLong())
            val summary = report.summaryFor(ym)
            MonthlyTrendPoint(
                yearMonth = ym,
                totalExpenses = summary?.totalExpenses ?: zero,
                expenseCount = summary?.expenseCount ?: 0,
            )
        }
    }

    /**
     * Compares [month] against `month.minusMonths(1)` when the previous month has expenses > 0.
     * Returns `null` if the previous month has no recorded expenses (total == 0).
     */
    fun computeMonthOverMonthDelta(
        report: MonthlyStatisticsReport,
        month: YearMonth,
    ): MonthOverMonthDelta? {
        val previousMonth = month.minusMonths(1)
        val previousTotal = report.summaryFor(previousMonth)?.totalExpenses ?: return null
        if (previousTotal.compareTo(BigDecimal.ZERO) <= 0) return null

        val currentTotal = report.summaryFor(month)?.totalExpenses ?: scaledZero(report.scale)
        val amountDelta = normalizeAmount(currentTotal.subtract(previousTotal), report.scale)
        val percentDelta = normalizePercent(
            amountDelta.multiply(HUNDRED).divide(previousTotal, PERCENT_SCALE, RoundingMode.HALF_UP),
        )

        return MonthOverMonthDelta(
            previousMonth = previousMonth,
            amountDelta = amountDelta,
            percentDelta = percentDelta,
        )
    }

    private fun summarizeMonth(
        yearMonth: YearMonth,
        transactions: List<ActiveTransaction>,
        scale: Int,
    ): MonthlySummary {
        val zero = scaledZero(scale)

        var totalExpenses = zero
        var expenseCount = 0
        var partnerAShare = zero
        var partnerBShare = zero
        var partnerAPaid = zero
        var partnerBPaid = zero
        var settlementTotal = zero
        var settlementCount = 0

        val expenses = mutableListOf<ActiveTransaction>()

        for (tx in transactions) {
            when (tx.recordType) {
                RecordType.EXPENSE -> {
                    expenses.add(tx)
                    expenseCount++
                    totalExpenses = totalExpenses.add(tx.amount)
                    partnerAShare = partnerAShare.add(tx.partnerAShare)
                    partnerBShare = partnerBShare.add(tx.partnerBShare)
                    when (tx.paidBy) {
                        PartnerRole.PARTNER_A -> partnerAPaid = partnerAPaid.add(tx.amount)
                        PartnerRole.PARTNER_B -> partnerBPaid = partnerBPaid.add(tx.amount)
                    }
                }

                RecordType.SETTLEMENT -> {
                    settlementCount++
                    settlementTotal = settlementTotal.add(tx.amount)
                }

                RecordType.VOID -> Unit
            }
        }

        val normTotal = normalizeAmount(totalExpenses, scale)
        val normShareA = normalizeAmount(partnerAShare, scale)
        val normShareB = normalizeAmount(partnerBShare, scale)
        val normPaidA = normalizeAmount(partnerAPaid, scale)
        val normPaidB = normalizeAmount(partnerBPaid, scale)
        val normSettlement = normalizeAmount(settlementTotal, scale)

        val (pctA, pctB) = if (normTotal.compareTo(BigDecimal.ZERO) > 0) {
            val rawPctA = normShareA
                .multiply(HUNDRED)
                .divide(normTotal, PERCENT_SCALE, RoundingMode.HALF_UP)
                .coerceIn(ZERO_PERCENT, HUNDRED_PERCENT)
            val normPctA = normalizePercent(rawPctA)
            val normPctB = normalizePercent(HUNDRED_PERCENT.subtract(normPctA))
            normPctA to normPctB
        } else {
            ZERO_PERCENT to ZERO_PERCENT
        }

        val byCategory = expenses
            .groupBy { it.category.group to it.category.id }
            .values
            .map { bucket ->
                val catTotal = normalizeAmount(sumAmounts(bucket, zero) { it.amount }, scale)
                val catShareA = normalizeAmount(sumAmounts(bucket, zero) { it.partnerAShare }, scale)
                val catShareB = normalizeAmount(sumAmounts(bucket, zero) { it.partnerBShare }, scale)
                val pct = computePercentOfTotal(catTotal, normTotal)
                CategoryMonthlyTotal(
                    category = bucket.first().category,
                    totalAmount = catTotal,
                    partnerAShare = catShareA,
                    partnerBShare = catShareB,
                    percentOfMonthTotal = pct,
                    expenseCount = bucket.size,
                )
            }
            .sortedWith(
                compareByDescending<CategoryMonthlyTotal> { it.totalAmount }
                    .thenBy { it.category.name },
            )

        val byCategoryGroup = expenses
            .groupBy { it.category.group }
            .map { (group, bucket) ->
                val grpTotal = normalizeAmount(sumAmounts(bucket, zero) { it.amount }, scale)
                val grpShareA = normalizeAmount(sumAmounts(bucket, zero) { it.partnerAShare }, scale)
                val grpShareB = normalizeAmount(sumAmounts(bucket, zero) { it.partnerBShare }, scale)
                val pct = computePercentOfTotal(grpTotal, normTotal)
                CategoryGroupMonthlyTotal(
                    group = group,
                    totalAmount = grpTotal,
                    partnerAShare = grpShareA,
                    partnerBShare = grpShareB,
                    percentOfMonthTotal = pct,
                    expenseCount = bucket.size,
                )
            }
            .sortedWith(
                compareByDescending<CategoryGroupMonthlyTotal> { it.totalAmount }
                    .thenBy { it.group.displayName },
            )

        return MonthlySummary(
            yearMonth = yearMonth,
            totalExpenses = normTotal,
            expenseCount = expenseCount,
            partnerTotals = MonthlyPartnerTotals(
                partnerAShare = normShareA,
                partnerBShare = normShareB,
                partnerAPaid = normPaidA,
                partnerBPaid = normPaidB,
                partnerASharePercent = pctA,
                partnerBSharePercent = pctB,
            ),
            byCategory = byCategory,
            byCategoryGroup = byCategoryGroup,
            settlementTotal = normSettlement,
            settlementCount = settlementCount,
        )
    }

    private fun computePercentOfTotal(
        part: BigDecimal,
        total: BigDecimal,
    ): BigDecimal {
        if (total.compareTo(BigDecimal.ZERO) <= 0) return ZERO_PERCENT
        return normalizePercent(
            part.multiply(HUNDRED).divide(total, PERCENT_SCALE, RoundingMode.HALF_UP),
        )
    }

    private inline fun sumAmounts(
        items: List<ActiveTransaction>,
        initial: BigDecimal,
        selector: (ActiveTransaction) -> BigDecimal,
    ): BigDecimal {
        var acc = initial
        for (item in items) {
            acc = acc.add(selector(item))
        }
        return acc
    }

    private fun scaledZero(scale: Int): BigDecimal =
        BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)

    private fun normalizeAmount(value: BigDecimal, scale: Int): BigDecimal {
        val scaled = value.setScale(scale, RoundingMode.HALF_UP)
        return if (scaled.compareTo(BigDecimal.ZERO) == 0) scaledZero(scale) else scaled
    }

    private fun normalizePercent(value: BigDecimal): BigDecimal {
        val scaled = value.setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
        return if (scaled.compareTo(BigDecimal.ZERO) == 0) ZERO_PERCENT else scaled
    }
}
