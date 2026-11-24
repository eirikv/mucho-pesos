package vige.land.muchopesos.domain.model

import java.math.BigDecimal
import java.time.YearMonth

data class MonthlyPartnerTotals(
    val partnerAShare: BigDecimal,
    val partnerBShare: BigDecimal,
    val partnerAPaid: BigDecimal,
    val partnerBPaid: BigDecimal,
    val partnerASharePercent: BigDecimal,
    val partnerBSharePercent: BigDecimal,
) {
    fun shareFor(role: PartnerRole): BigDecimal = when (role) {
        PartnerRole.PARTNER_A -> partnerAShare
        PartnerRole.PARTNER_B -> partnerBShare
    }

    fun paidFor(role: PartnerRole): BigDecimal = when (role) {
        PartnerRole.PARTNER_A -> partnerAPaid
        PartnerRole.PARTNER_B -> partnerBPaid
    }

    fun sharePercentFor(role: PartnerRole): BigDecimal = when (role) {
        PartnerRole.PARTNER_A -> partnerASharePercent
        PartnerRole.PARTNER_B -> partnerBSharePercent
    }
}

data class CategoryMonthlyTotal(
    val category: ExpenseCategory,
    val totalAmount: BigDecimal,
    val partnerAShare: BigDecimal,
    val partnerBShare: BigDecimal,
    val percentOfMonthTotal: BigDecimal,
    val expenseCount: Int,
) {
    fun shareFor(role: PartnerRole): BigDecimal = when (role) {
        PartnerRole.PARTNER_A -> partnerAShare
        PartnerRole.PARTNER_B -> partnerBShare
    }
}

data class CategoryGroupMonthlyTotal(
    val group: CategoryGroup,
    val totalAmount: BigDecimal,
    val partnerAShare: BigDecimal,
    val partnerBShare: BigDecimal,
    val percentOfMonthTotal: BigDecimal,
    val expenseCount: Int,
) {
    fun shareFor(role: PartnerRole): BigDecimal = when (role) {
        PartnerRole.PARTNER_A -> partnerAShare
        PartnerRole.PARTNER_B -> partnerBShare
    }
}

data class MonthOverMonthDelta(
    val previousMonth: YearMonth,
    val amountDelta: BigDecimal,
    val percentDelta: BigDecimal?,
)

data class MonthlySummary(
    val yearMonth: YearMonth,
    val totalExpenses: BigDecimal,
    val expenseCount: Int,
    val partnerTotals: MonthlyPartnerTotals,
    val byCategory: List<CategoryMonthlyTotal>,
    val byCategoryGroup: List<CategoryGroupMonthlyTotal>,
    val settlementTotal: BigDecimal,
    val settlementCount: Int,
)

data class MonthlyTrendPoint(
    val yearMonth: YearMonth,
    val totalExpenses: BigDecimal,
    val expenseCount: Int,
)

data class MonthlyStatisticsReport(
    val months: List<MonthlySummary>,
    val scale: Int = 2,
) {
    private val summariesByMonth: Map<YearMonth, MonthlySummary> =
        months.associateBy { it.yearMonth }

    fun summaryFor(yearMonth: YearMonth): MonthlySummary? = summariesByMonth[yearMonth]
}
