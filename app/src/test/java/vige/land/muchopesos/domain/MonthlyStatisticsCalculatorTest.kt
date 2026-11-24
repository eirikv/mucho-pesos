package vige.land.muchopesos.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vige.land.muchopesos.domain.logic.MonthlyStatisticsCalculator
import vige.land.muchopesos.domain.logic.SplitCalculator
import vige.land.muchopesos.domain.logic.SplitRequest
import vige.land.muchopesos.domain.model.ActiveTransaction
import vige.land.muchopesos.domain.model.CategoryCatalog
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.ExpenseCategory
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.RecordType
import vige.land.muchopesos.domain.model.SplitType

class MonthlyStatisticsCalculatorTest {

    @Test
    fun compute_emptyInput_returnsEmptyReportAndZeroedEmptySummary() {
        val report = MonthlyStatisticsCalculator.compute(emptyList(), scale = 2)
        assertTrue(report.months.isEmpty())
        assertEquals(2, report.scale)
        assertNull(report.summaryFor(YearMonth.of(2026, 11)))

        val empty = MonthlyStatisticsCalculator.emptySummary(YearMonth.of(2026, 11), scale = 2)
        assertEquals(YearMonth.of(2026, 11), empty.yearMonth)
        assertEquals("0.00", empty.totalExpenses.toPlainString())
        assertEquals(0, empty.expenseCount)
        assertEquals("0.00", empty.partnerTotals.partnerAShare.toPlainString())
        assertEquals("0.00", empty.partnerTotals.partnerBShare.toPlainString())
        assertEquals("0.00", empty.partnerTotals.partnerAPaid.toPlainString())
        assertEquals("0.00", empty.partnerTotals.partnerBPaid.toPlainString())
        assertEquals("0.0", empty.partnerTotals.partnerASharePercent.toPlainString())
        assertEquals("0.0", empty.partnerTotals.partnerBSharePercent.toPlainString())
        assertEquals("0.00", empty.settlementTotal.toPlainString())
        assertEquals(0, empty.settlementCount)
        assertTrue(empty.byCategory.isEmpty())
        assertTrue(empty.byCategoryGroup.isEmpty())
    }

    @Test
    fun compute_multiMonthGroupingSortingAndTrendWindowGapFilling() {
        val txs = listOf(
            expense(
                txId = "tx-aug",
                date = LocalDate.of(2026, 8, 12),
                amount = "120.00",
                category = CategoryCatalog.GROCERIES,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "60.00",
                shareB = "60.00",
            ),
            expense(
                txId = "tx-oct",
                date = LocalDate.of(2026, 10, 5),
                amount = "80.00",
                category = CategoryCatalog.FUEL,
                paidBy = PartnerRole.PARTNER_B,
                shareA = "40.00",
                shareB = "40.00",
            ),
            expense(
                txId = "tx-nov",
                date = LocalDate.of(2026, 11, 20),
                amount = "200.00",
                category = CategoryCatalog.RENT_MORTGAGE,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "100.00",
                shareB = "100.00",
            ),
        )

        val report = MonthlyStatisticsCalculator.compute(txs, scale = 2)
        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 8)),
            report.months.map { it.yearMonth },
        )

        val trend = MonthlyStatisticsCalculator.buildTrendWindow(
            report = report,
            anchorMonth = YearMonth.of(2026, 11),
            windowSize = 6,
        )
        assertEquals(6, trend.size)
        assertEquals(
            listOf(
                YearMonth.of(2026, 6),
                YearMonth.of(2026, 7),
                YearMonth.of(2026, 8),
                YearMonth.of(2026, 9),
                YearMonth.of(2026, 10),
                YearMonth.of(2026, 11),
            ),
            trend.map { it.yearMonth },
        )
        assertEquals(
            listOf("0.00", "0.00", "120.00", "0.00", "80.00", "200.00"),
            trend.map { it.totalExpenses.toPlainString() },
        )
        assertEquals(listOf(0, 0, 1, 0, 1, 1), trend.map { it.expenseCount })
    }

    @Test
    fun compute_allFourSplitModesWithOddCent_preservesPartnerSharePaidAndPercentInvariants() {
        val equalOdd = SplitCalculator.calculate(
            totalAmount = BigDecimal("10.01"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Equal,
        ).getOrThrow() // A=5.01, B=5.00

        val exact = SplitCalculator.calculate(
            totalAmount = BigDecimal("50.00"),
            paidBy = PartnerRole.PARTNER_B,
            request = SplitRequest.Exact(BigDecimal("15.25"), BigDecimal("34.75")),
        ).getOrThrow() // A=15.25, B=34.75

        val pct = SplitCalculator.calculate(
            totalAmount = BigDecimal("99.99"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Percentage(BigDecimal("33.33"), BigDecimal("66.67")),
        ).getOrThrow() // A=33.33, B=66.66

        val full = SplitCalculator.calculate(
            totalAmount = BigDecimal("40.00"),
            paidBy = PartnerRole.PARTNER_B,
            request = SplitRequest.Full(owedBy = PartnerRole.PARTNER_A),
        ).getOrThrow() // A=40.00, B=0.00

        val txs = listOf(
            expense(
                txId = "e1",
                date = LocalDate.of(2026, 11, 2),
                amount = "10.01",
                category = CategoryCatalog.COFFEE_SNACKS,
                paidBy = PartnerRole.PARTNER_A,
                splitType = equalOdd.splitType,
                shareA = equalOdd.partnerAShare.toPlainString(),
                shareB = equalOdd.partnerBShare.toPlainString(),
            ),
            expense(
                txId = "e2",
                date = LocalDate.of(2026, 11, 5),
                amount = "50.00",
                category = CategoryCatalog.GROCERIES,
                paidBy = PartnerRole.PARTNER_B,
                splitType = exact.splitType,
                shareA = exact.partnerAShare.toPlainString(),
                shareB = exact.partnerBShare.toPlainString(),
            ),
            expense(
                txId = "e3",
                date = LocalDate.of(2026, 11, 10),
                amount = "99.99",
                category = CategoryCatalog.ELECTRICITY_GAS,
                paidBy = PartnerRole.PARTNER_A,
                splitType = pct.splitType,
                shareA = pct.partnerAShare.toPlainString(),
                shareB = pct.partnerBShare.toPlainString(),
            ),
            expense(
                txId = "e4",
                date = LocalDate.of(2026, 11, 12),
                amount = "40.00",
                category = CategoryCatalog.FUEL,
                paidBy = PartnerRole.PARTNER_B,
                splitType = full.splitType,
                shareA = full.partnerAShare.toPlainString(),
                shareB = full.partnerBShare.toPlainString(),
            ),
        )

        val summary = MonthlyStatisticsCalculator.compute(txs).summaryFor(YearMonth.of(2026, 11))!!
        // Total = 10.01 + 50.00 + 99.99 + 40.00 = 200.00
        // ShareA = 5.01 + 15.25 + 33.33 + 40.00 = 93.59
        // ShareB = 5.00 + 34.75 + 66.66 + 0.00 = 106.41
        // PaidA = 10.01 + 99.99 = 110.00
        // PaidB = 50.00 + 40.00 = 90.00
        assertEquals("200.00", summary.totalExpenses.toPlainString())
        assertEquals(4, summary.expenseCount)

        val p = summary.partnerTotals
        assertEquals("93.59", p.shareFor(PartnerRole.PARTNER_A).toPlainString())
        assertEquals("106.41", p.shareFor(PartnerRole.PARTNER_B).toPlainString())
        assertEquals("110.00", p.paidFor(PartnerRole.PARTNER_A).toPlainString())
        assertEquals("90.00", p.paidFor(PartnerRole.PARTNER_B).toPlainString())

        // Exact BigDecimal invariants
        assertEquals(0, p.partnerAShare.add(p.partnerBShare).compareTo(summary.totalExpenses))
        assertEquals(0, p.partnerAPaid.add(p.partnerBPaid).compareTo(summary.totalExpenses))

        // 93.59 / 200.00 * 100 = 46.795 -> HALF_UP = 46.8; B = 100.0 - 46.8 = 53.2
        assertEquals("46.8", p.sharePercentFor(PartnerRole.PARTNER_A).toPlainString())
        assertEquals("53.2", p.sharePercentFor(PartnerRole.PARTNER_B).toPlainString())
        assertEquals(0, p.partnerASharePercent.add(p.partnerBSharePercent).compareTo(BigDecimal("100.0")))
    }

    @Test
    fun compute_excludesSettlementsFromExpensesAndTracksSeparately() {
        val txs = listOf(
            expense(
                txId = "e1",
                date = LocalDate.of(2026, 11, 10),
                amount = "60.00",
                category = CategoryCatalog.DINING_OUT,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "30.00",
                shareB = "30.00",
            ),
            settlement(
                txId = "s1",
                date = LocalDate.of(2026, 11, 15),
                amount = "25.00",
                paidBy = PartnerRole.PARTNER_B,
            ),
            settlement(
                txId = "s2",
                date = LocalDate.of(2026, 11, 18),
                amount = "5.00",
                paidBy = PartnerRole.PARTNER_B,
            ),
            // Settlement-only month
            settlement(
                txId = "s3",
                date = LocalDate.of(2026, 10, 20),
                amount = "45.00",
                paidBy = PartnerRole.PARTNER_A,
            ),
        )

        val report = MonthlyStatisticsCalculator.compute(txs)

        val nov = report.summaryFor(YearMonth.of(2026, 11))!!
        assertEquals("60.00", nov.totalExpenses.toPlainString())
        assertEquals(1, nov.expenseCount)
        assertEquals("30.00", nov.settlementTotal.toPlainString())
        assertEquals(2, nov.settlementCount)
        assertEquals(1, nov.byCategory.size)
        assertEquals(CategoryCatalog.DINING_OUT, nov.byCategory.single().category)
        assertTrue(nov.byCategoryGroup.none { it.group == CategoryGroup.SETTLEMENT })

        val oct = report.summaryFor(YearMonth.of(2026, 10))!!
        assertEquals("0.00", oct.totalExpenses.toPlainString())
        assertEquals(0, oct.expenseCount)
        assertEquals("45.00", oct.settlementTotal.toPlainString())
        assertEquals(1, oct.settlementCount)
        assertEquals("0.0", oct.partnerTotals.partnerASharePercent.toPlainString())
        assertEquals("0.0", oct.partnerTotals.partnerBSharePercent.toPlainString())

        // Previous month (Oct) had 0 expenses (only a settlement), so MoM delta is null
        assertNull(MonthlyStatisticsCalculator.computeMonthOverMonthDelta(report, YearMonth.of(2026, 11)))
    }

    @Test
    fun compute_categoryAndGroupRollups_sortedDescByAmountThenNameAsc_andSumsMatchTotal() {
        val txs = listOf(
            // Food: Groceries = 40 + 60 = 100, Coffee & Snacks = 50 -> Food group = 150
            expense(
                txId = "e1",
                date = LocalDate.of(2026, 11, 1),
                amount = "40.00",
                category = CategoryCatalog.GROCERIES,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "20.00",
                shareB = "20.00",
            ),
            expense(
                txId = "e2",
                date = LocalDate.of(2026, 11, 3),
                amount = "60.00",
                category = CategoryCatalog.GROCERIES,
                paidBy = PartnerRole.PARTNER_B,
                shareA = "30.00",
                shareB = "30.00",
            ),
            expense(
                txId = "e3",
                date = LocalDate.of(2026, 11, 4),
                amount = "50.00",
                category = CategoryCatalog.COFFEE_SNACKS,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "25.00",
                shareB = "25.00",
            ),
            // Transport: Fuel = 50 (tie with Coffee & Snacks -> Coffee & Snacks comes before Fuel alphabetically!)
            expense(
                txId = "e4",
                date = LocalDate.of(2026, 11, 5),
                amount = "50.00",
                category = CategoryCatalog.FUEL,
                paidBy = PartnerRole.PARTNER_B,
                shareA = "10.00",
                shareB = "40.00",
            ),
            // Housing: Household Supplies = 50 (tie with Transport group = 50 -> Housing comes before Transport!)
            expense(
                txId = "e5",
                date = LocalDate.of(2026, 11, 6),
                amount = "50.00",
                category = CategoryCatalog.HOUSEHOLD_SUPPLIES,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "25.00",
                shareB = "25.00",
            ),
        )

        val summary = MonthlyStatisticsCalculator.compute(txs).summaryFor(YearMonth.of(2026, 11))!!
        assertEquals("250.00", summary.totalExpenses.toPlainString())

        // Category order: Groceries (100), Coffee & Snacks (50), Fuel (50), Household Supplies (50)
        assertEquals(
            listOf(
                CategoryCatalog.GROCERIES,
                CategoryCatalog.COFFEE_SNACKS,
                CategoryCatalog.FUEL,
                CategoryCatalog.HOUSEHOLD_SUPPLIES,
            ),
            summary.byCategory.map { it.category },
        )
        assertEquals(2, summary.byCategory[0].expenseCount)
        assertEquals("40.0", summary.byCategory[0].percentOfMonthTotal.toPlainString())
        assertEquals("50.00", summary.byCategory[0].shareFor(PartnerRole.PARTNER_A).toPlainString())
        assertEquals("50.00", summary.byCategory[0].shareFor(PartnerRole.PARTNER_B).toPlainString())
        assertEquals("10.00", summary.byCategory[2].shareFor(PartnerRole.PARTNER_A).toPlainString())
        assertEquals("40.00", summary.byCategory[2].shareFor(PartnerRole.PARTNER_B).toPlainString())

        // Group order: Food (150), Housing (50), Transport (50)
        assertEquals(
            listOf(CategoryGroup.FOOD, CategoryGroup.HOUSING, CategoryGroup.TRANSPORT),
            summary.byCategoryGroup.map { it.group },
        )
        assertEquals(3, summary.byCategoryGroup[0].expenseCount)
        assertEquals("60.0", summary.byCategoryGroup[0].percentOfMonthTotal.toPlainString())
        assertEquals("75.00", summary.byCategoryGroup[0].shareFor(PartnerRole.PARTNER_A).toPlainString())
        assertEquals("75.00", summary.byCategoryGroup[0].shareFor(PartnerRole.PARTNER_B).toPlainString())

        // Sum invariants
        val catSum = summary.byCategory.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.totalAmount) }
        val grpSum = summary.byCategoryGroup.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.totalAmount) }
        assertEquals(0, catSum.compareTo(summary.totalExpenses))
        assertEquals(0, grpSum.compareTo(summary.totalExpenses))
    }

    @Test
    fun computeMonthOverMonthDelta_increaseDecreaseAndMissingPrevious() {
        val txs = listOf(
            expense(
                txId = "sep",
                date = LocalDate.of(2026, 9, 10),
                amount = "200.00",
                category = CategoryCatalog.GROCERIES,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "100.00",
                shareB = "100.00",
            ),
            expense(
                txId = "oct",
                date = LocalDate.of(2026, 10, 10),
                amount = "250.00",
                category = CategoryCatalog.GROCERIES,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "125.00",
                shareB = "125.00",
            ),
            expense(
                txId = "nov",
                date = LocalDate.of(2026, 11, 10),
                amount = "200.00",
                category = CategoryCatalog.GROCERIES,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "100.00",
                shareB = "100.00",
            ),
        )

        val report = MonthlyStatisticsCalculator.compute(txs)

        // Sep has no Aug -> null
        assertNull(MonthlyStatisticsCalculator.computeMonthOverMonthDelta(report, YearMonth.of(2026, 9)))

        // Oct vs Sep: +50.00 (+25.0%)
        val octDelta = MonthlyStatisticsCalculator.computeMonthOverMonthDelta(report, YearMonth.of(2026, 10))
        assertNotNull(octDelta)
        assertEquals(YearMonth.of(2026, 9), octDelta!!.previousMonth)
        assertEquals("50.00", octDelta.amountDelta.toPlainString())
        assertEquals("25.0", octDelta.percentDelta!!.toPlainString())

        // Nov vs Oct: -50.00 (-20.0%)
        val novDelta = MonthlyStatisticsCalculator.computeMonthOverMonthDelta(report, YearMonth.of(2026, 11))
        assertNotNull(novDelta)
        assertEquals(YearMonth.of(2026, 10), novDelta!!.previousMonth)
        assertEquals("-50.00", novDelta.amountDelta.toPlainString())
        assertEquals("-20.0", novDelta.percentDelta!!.toPlainString())

        // Dec (empty) vs Nov (200.00): -200.00 (-100.0%)
        val decDelta = MonthlyStatisticsCalculator.computeMonthOverMonthDelta(report, YearMonth.of(2026, 12))
        assertNotNull(decDelta)
        assertEquals("-200.00", decDelta!!.amountDelta.toPlainString())
        assertEquals("-100.0", decDelta.percentDelta!!.toPlainString())
    }

    @Test
    fun compute_zeroDecimalCurrencyJpy_preservesZeroScale() {
        val txs = listOf(
            expense(
                txId = "jpy-1",
                date = LocalDate.of(2026, 11, 5),
                amount = "101",
                category = CategoryCatalog.DINING_OUT,
                paidBy = PartnerRole.PARTNER_A,
                shareA = "51",
                shareB = "50",
            ),
            expense(
                txId = "jpy-2",
                date = LocalDate.of(2026, 11, 8),
                amount = "299",
                category = CategoryCatalog.PUBLIC_TRANSIT,
                paidBy = PartnerRole.PARTNER_B,
                shareA = "149",
                shareB = "150",
            ),
        )

        val report = MonthlyStatisticsCalculator.compute(txs, scale = 0)
        val nov = report.summaryFor(YearMonth.of(2026, 11))!!

        assertEquals("400", nov.totalExpenses.toPlainString())
        assertEquals(0, nov.totalExpenses.scale())
        assertEquals("200", nov.partnerTotals.partnerAShare.toPlainString())
        assertEquals("200", nov.partnerTotals.partnerBShare.toPlainString())
        assertEquals("101", nov.partnerTotals.partnerAPaid.toPlainString())
        assertEquals("299", nov.partnerTotals.partnerBPaid.toPlainString())
        assertEquals("50.0", nov.partnerTotals.partnerASharePercent.toPlainString())
        assertEquals("50.0", nov.partnerTotals.partnerBSharePercent.toPlainString())
        assertEquals("0", nov.settlementTotal.toPlainString())
        assertEquals(0, nov.settlementTotal.scale())

        val trend = MonthlyStatisticsCalculator.buildTrendWindow(report, YearMonth.of(2026, 11), windowSize = 3)
        assertEquals(listOf("0", "0", "400"), trend.map { it.totalExpenses.toPlainString() })
        assertTrue(trend.all { it.totalExpenses.scale() == 0 })
    }

    private fun expense(
        txId: String,
        date: LocalDate,
        amount: String,
        category: ExpenseCategory,
        paidBy: PartnerRole,
        splitType: SplitType = SplitType.EQUAL,
        shareA: String,
        shareB: String,
    ): ActiveTransaction = ActiveTransaction(
        txId = txId,
        recordType = RecordType.EXPENSE,
        date = date,
        createdAt = "${date}T12:00:00Z",
        createdBy = "alex@demo.local",
        description = category.name,
        category = category,
        amount = BigDecimal(amount),
        paidBy = paidBy,
        splitType = splitType,
        partnerAShare = BigDecimal(shareA),
        partnerBShare = BigDecimal(shareB),
    )

    private fun settlement(
        txId: String,
        date: LocalDate,
        amount: String,
        paidBy: PartnerRole,
    ): ActiveTransaction = ActiveTransaction(
        txId = txId,
        recordType = RecordType.SETTLEMENT,
        date = date,
        createdAt = "${date}T12:00:00Z",
        createdBy = "sam@demo.local",
        description = "Settlement payment",
        category = CategoryCatalog.SETTLEMENT,
        amount = BigDecimal(amount),
        paidBy = paidBy,
        splitType = SplitType.SETTLEMENT,
        partnerAShare = BigDecimal.ZERO.setScale(2),
        partnerBShare = BigDecimal.ZERO.setScale(2),
    )
}
