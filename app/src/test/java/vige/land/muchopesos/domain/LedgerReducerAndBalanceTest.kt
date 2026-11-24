package vige.land.muchopesos.domain

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vige.land.muchopesos.domain.logic.BalanceCalculator
import vige.land.muchopesos.domain.logic.LedgerReducer
import vige.land.muchopesos.domain.model.CategoryCatalog
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.LedgerRow
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.RecordType
import vige.land.muchopesos.domain.model.SplitType

class LedgerReducerAndBalanceTest {

    @Test
    fun reduce_ordersChronologicallyByDateDescThenCreatedAtDesc() {
        val rows = listOf(
            expenseRow(
                txId = "tx-old",
                date = "2026-11-10",
                createdAt = "2026-11-10T10:00:00Z",
                amount = "20.00",
                paidBy = PartnerRole.PARTNER_A,
                shareA = "10.00",
                shareB = "10.00",
            ),
            expenseRow(
                txId = "tx-same-day-early",
                date = "2026-11-20",
                createdAt = "2026-11-20T09:00:00Z",
                amount = "30.00",
                paidBy = PartnerRole.PARTNER_A,
                shareA = "15.00",
                shareB = "15.00",
            ),
            expenseRow(
                txId = "tx-same-day-late",
                date = "2026-11-20",
                createdAt = "2026-11-20T09:00:00.500Z",
                amount = "40.00",
                paidBy = PartnerRole.PARTNER_B,
                shareA = "20.00",
                shareB = "20.00",
            ),
            expenseRow(
                txId = "tx-newest",
                date = "2026-11-23",
                createdAt = "2026-11-21T08:00:00Z",
                amount = "50.00",
                paidBy = PartnerRole.PARTNER_A,
                shareA = "25.00",
                shareB = "25.00",
            ),
        )

        val active = LedgerReducer.reduce(rows)

        assertEquals(
            listOf("tx-newest", "tx-same-day-late", "tx-same-day-early", "tx-old"),
            active.map { it.txId },
        )
        assertEquals(LocalDate.of(2026, 11, 23), active[0].date)
    }

    @Test
    fun reduce_appliesVoidDeletionAndChainedEditsCleanly() {
        val rows = listOf(
            // Original tx-1
            expenseRow(
                txId = "tx-1",
                date = "2026-11-15",
                createdAt = "2026-11-15T12:00:00Z",
                description = "Groceries v1",
                amount = "100.00",
                paidBy = PartnerRole.PARTNER_A,
                shareA = "50.00",
                shareB = "50.00",
            ),
            // tx-to-delete
            expenseRow(
                txId = "tx-del",
                date = "2026-11-16",
                createdAt = "2026-11-16T12:00:00Z",
                description = "Mistake",
                amount = "500.00",
                paidBy = PartnerRole.PARTNER_B,
                shareA = "250.00",
                shareB = "250.00",
            ),
            // Edit 1: VOID tx-1 + add tx-2
            voidRow(txId = "void-1", refTxId = "tx-1"),
            expenseRow(
                txId = "tx-2",
                refTxId = "tx-1",
                date = "2026-11-15",
                createdAt = "2026-11-15T13:00:00Z",
                description = "Groceries v2",
                amount = "110.00",
                paidBy = PartnerRole.PARTNER_A,
                shareA = "55.00",
                shareB = "55.00",
            ),
            // Edit 2 (chained): VOID tx-2 + add tx-3
            voidRow(txId = "void-2", refTxId = "tx-2"),
            expenseRow(
                txId = "tx-3",
                refTxId = "tx-2",
                date = "2026-11-15",
                createdAt = "2026-11-15T14:00:00Z",
                description = "Groceries v3",
                amount = "80.00",
                paidBy = PartnerRole.PARTNER_A,
                shareA = "40.00",
                shareB = "40.00",
            ),
            // Delete tx-del
            voidRow(txId = "void-3", refTxId = "tx-del"),
            // Orphan VOID for unknown txId
            voidRow(txId = "void-orphan", refTxId = "tx-missing"),
        )

        val active = LedgerReducer.reduce(rows)

        assertEquals(1, active.size)
        assertEquals("tx-3", active[0].txId)
        assertEquals("Groceries v3", active[0].description)
        assertEquals("80.00", active[0].amount.toPlainString())

        val balance = BalanceCalculator.computeNetBalance(active)
        assertEquals("40.00", balance.netForPartnerA.toPlainString())
        assertEquals(PartnerRole.PARTNER_B, balance.debtorRole)
        assertEquals(PartnerRole.PARTNER_A, balance.creditorRole)
        assertEquals("40.00", balance.amount.toPlainString())
        assertFalse(balance.isSettledUp)
    }

    @Test
    fun balanceCalculator_mixedExpensesAndPartialThenFullSettlement() {
        val baseRows = listOf(
            // A pays $100 equal -> B owes A +50.00
            expenseRow(
                txId = "e1",
                date = "2026-11-01",
                createdAt = "2026-11-01T10:00:00Z",
                amount = "100.00",
                paidBy = PartnerRole.PARTNER_A,
                shareA = "50.00",
                shareB = "50.00",
            ),
            // B pays $60 exact (A owes 20, B owes 40) -> netForA = 50 - 20 = +30.00
            expenseRow(
                txId = "e2",
                date = "2026-11-02",
                createdAt = "2026-11-02T10:00:00Z",
                amount = "60.00",
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.EXACT,
                shareA = "20.00",
                shareB = "40.00",
            ),
            // A pays $45.50 full owed by B -> netForA = 30 + 45.50 = +75.50
            expenseRow(
                txId = "e3",
                date = "2026-11-03",
                createdAt = "2026-11-03T10:00:00Z",
                amount = "45.50",
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.FULL,
                shareA = "0.00",
                shareB = "45.50",
            ),
        )

        val beforeSettle = BalanceCalculator.computeNetBalance(LedgerReducer.reduce(baseRows))
        assertEquals("75.50", beforeSettle.netForPartnerA.toPlainString())
        assertEquals("75.50", beforeSettle.netFor(PartnerRole.PARTNER_A).toPlainString())
        assertEquals("-75.50", beforeSettle.netFor(PartnerRole.PARTNER_B).toPlainString())
        assertEquals(PartnerRole.PARTNER_B, beforeSettle.debtorRole)
        assertEquals(PartnerRole.PARTNER_A, beforeSettle.creditorRole)

        // Partial settlement: B pays A $25.50 -> remaining +50.00
        val withPartial = baseRows + settlementRow(
            txId = "s1",
            date = "2026-11-04",
            createdAt = "2026-11-04T10:00:00Z",
            amount = "25.50",
            paidBy = PartnerRole.PARTNER_B,
        )
        val afterPartial = BalanceCalculator.computeNetBalance(LedgerReducer.reduce(withPartial))
        assertEquals("50.00", afterPartial.netForPartnerA.toPlainString())
        assertFalse(afterPartial.isSettledUp)

        // Final settlement: B pays A $50.00 -> settled up!
        val withFinal = withPartial + settlementRow(
            txId = "s2",
            date = "2026-11-05",
            createdAt = "2026-11-05T10:00:00Z",
            amount = "50.00",
            paidBy = PartnerRole.PARTNER_B,
        )
        val afterFinal = BalanceCalculator.computeNetBalance(LedgerReducer.reduce(withFinal))
        assertTrue(afterFinal.isSettledUp)
        assertEquals("0.00", afterFinal.netForPartnerA.toPlainString())
        assertEquals("0.00", afterFinal.netFor(PartnerRole.PARTNER_B).toPlainString())
        assertEquals("0.00", afterFinal.amount.toPlainString())
        assertNull(afterFinal.debtorRole)
        assertNull(afterFinal.creditorRole)
    }

    @Test
    fun balanceCalculator_partnerAOwesPartnerB_andOverpaymentFlipsDirection() {
        val rows = listOf(
            expenseRow(
                txId = "e1",
                date = "2026-11-01",
                createdAt = "2026-11-01T10:00:00Z",
                amount = "80.00",
                paidBy = PartnerRole.PARTNER_B,
                shareA = "40.00",
                shareB = "40.00",
            ),
        )
        val summary = BalanceCalculator.computeNetBalance(LedgerReducer.reduce(rows))
        assertEquals("-40.00", summary.netForPartnerA.toPlainString())
        assertEquals(PartnerRole.PARTNER_A, summary.debtorRole)
        assertEquals(PartnerRole.PARTNER_B, summary.creditorRole)
        assertEquals("40.00", summary.amount.toPlainString())
        assertEquals("40.00", summary.netFor(PartnerRole.PARTNER_B).toPlainString())

        // Partner A pays Partner B $60.00 -> B now owes A $20.00
        val flipped = BalanceCalculator.computeNetBalance(
            LedgerReducer.reduce(
                rows + settlementRow(
                    txId = "s1",
                    date = "2026-11-02",
                    createdAt = "2026-11-02T10:00:00Z",
                    amount = "60.00",
                    paidBy = PartnerRole.PARTNER_A,
                ),
            ),
        )
        assertEquals("20.00", flipped.netForPartnerA.toPlainString())
        assertEquals(PartnerRole.PARTNER_B, flipped.debtorRole)
        assertEquals(PartnerRole.PARTNER_A, flipped.creditorRole)
    }

    @Test
    fun computeImpactForViewer_sumsToExactNetBalanceForBothPartners() {
        val active = LedgerReducer.reduce(
            listOf(
                expenseRow(
                    txId = "e1",
                    date = "2026-11-01",
                    createdAt = "2026-11-01T10:00:00Z",
                    amount = "90.00",
                    paidBy = PartnerRole.PARTNER_A,
                    shareA = "30.00",
                    shareB = "60.00",
                ),
                expenseRow(
                    txId = "e2",
                    date = "2026-11-02",
                    createdAt = "2026-11-02T10:00:00Z",
                    amount = "45.25",
                    paidBy = PartnerRole.PARTNER_B,
                    shareA = "22.63",
                    shareB = "22.62",
                ),
                settlementRow(
                    txId = "s1",
                    date = "2026-11-03",
                    createdAt = "2026-11-03T10:00:00Z",
                    amount = "15.00",
                    paidBy = PartnerRole.PARTNER_B,
                ),
            ),
        )

        val summary = BalanceCalculator.computeNetBalance(active)

        for (role in PartnerRole.entries) {
            val sumImpact = active.fold(BigDecimal.ZERO.setScale(2)) { acc, tx ->
                acc.add(BalanceCalculator.computeImpactForViewer(tx, role).netForViewer)
            }
            assertEquals(0, sumImpact.compareTo(summary.netFor(role)))
        }

        val e1 = active.first { it.txId == "e1" }
        val impactA = BalanceCalculator.computeImpactForViewer(e1, PartnerRole.PARTNER_A)
        assertTrue(impactA.isPayer)
        assertFalse(impactA.isSettlement)
        assertEquals("60.00", impactA.netForViewer.toPlainString())

        val impactB = BalanceCalculator.computeImpactForViewer(e1, PartnerRole.PARTNER_B)
        assertFalse(impactB.isPayer)
        assertFalse(impactB.isSettlement)
        assertEquals("-60.00", impactB.netForViewer.toPlainString())

        val s1 = active.first { it.txId == "s1" }
        val settleImpactB = BalanceCalculator.computeImpactForViewer(s1, PartnerRole.PARTNER_B)
        assertTrue(settleImpactB.isPayer)
        assertTrue(settleImpactB.isSettlement)
        assertEquals("15.00", settleImpactB.netForViewer.toPlainString())
        assertEquals(CategoryCatalog.SETTLEMENT, s1.category)
    }

    @Test
    fun ledgerConfig_roleMappingAndDisplayNames() {
        val config = LedgerConfig(
            spreadsheetId = "sheet-1234567890",
            ledgerName = "Our Household",
            homeCurrencyCode = "nok",
            partnerAEmail = "Alex@Gmail.com",
            partnerAName = "Alex",
            partnerBEmail = "sam@gmail.com",
            partnerBName = "Sam",
            createdAt = "2026-11-23T20:00:00Z",
        )

        assertEquals("NOK", config.currencySpec.code)
        assertEquals("kr", config.currencySpec.symbol)
        assertEquals(PartnerRole.PARTNER_A, config.roleForEmail("alex@gmail.com"))
        assertEquals(PartnerRole.PARTNER_B, config.roleForEmail(" SAM@GMAIL.COM "))
        assertEquals(PartnerRole.PARTNER_A, config.roleForEmail("unknown@example.com"))
        assertEquals("Alex", config.displayNameFor(PartnerRole.PARTNER_A))
        assertEquals("Sam", config.displayNameFor(PartnerRole.PARTNER_B))
        assertEquals("sam@gmail.com", config.emailFor(PartnerRole.PARTNER_B))
        assertEquals(PartnerRole.PARTNER_B, PartnerRole.PARTNER_A.other())
        assertEquals(PartnerRole.PARTNER_A, PartnerRole.PARTNER_B.other())
    }

    @Test
    fun ledgerRow_bigDecimalSerializerRoundTrip() {
        val original = expenseRow(
            txId = "tx-json",
            date = "2026-11-23",
            createdAt = "2026-11-23T18:30:00Z",
            amount = "1234.56",
            paidBy = PartnerRole.PARTNER_A,
            shareA = "617.28",
            shareB = "617.28",
        )
        val encoded = Json.encodeToString(original)
        assertTrue(encoded.contains("\"1234.56\""))
        val decoded = Json.decodeFromString<LedgerRow>(encoded)
        assertEquals(original, decoded)
        assertEquals("1234.56", decoded.amount.toPlainString())
    }

    @Test
    fun reduce_preservesCustomSheetCategoryWithGroup() {
        val rows = listOf(
            expenseRow(
                txId = "tx-custom",
                date = "2026-11-22",
                createdAt = "2026-11-22T10:00:00Z",
                categoryGroup = "HOUSING",
                category = "Garden Tools",
                amount = "25.00",
                paidBy = PartnerRole.PARTNER_A,
                shareA = "12.50",
                shareB = "12.50",
            ),
        )
        val active = LedgerReducer.reduce(rows)
        assertEquals(1, active.size)
        assertEquals("Garden Tools", active[0].category.name)
        assertEquals(CategoryGroup.HOUSING, active[0].category.group)
    }

    private fun expenseRow(
        txId: String,
        refTxId: String = "",
        date: String,
        createdAt: String,
        description: String = "Expense",
        categoryGroup: String = "FOOD",
        category: String = "Groceries",
        amount: String,
        paidBy: PartnerRole,
        splitType: SplitType = SplitType.EQUAL,
        shareA: String,
        shareB: String,
    ): LedgerRow = LedgerRow(
        txId = txId,
        recordType = RecordType.EXPENSE,
        refTxId = refTxId,
        date = date,
        createdAt = createdAt,
        createdBy = "alex@demo.local",
        description = description,
        categoryGroup = categoryGroup,
        category = category,
        amount = BigDecimal(amount),
        paidBy = paidBy,
        splitType = splitType,
        partnerAShare = BigDecimal(shareA),
        partnerBShare = BigDecimal(shareB),
    )

    private fun settlementRow(
        txId: String,
        date: String,
        createdAt: String,
        amount: String,
        paidBy: PartnerRole,
    ): LedgerRow = LedgerRow(
        txId = txId,
        recordType = RecordType.SETTLEMENT,
        refTxId = "",
        date = date,
        createdAt = createdAt,
        createdBy = "sam@demo.local",
        description = "Settlement payment",
        categoryGroup = "SETTLEMENT",
        category = "Settlement",
        amount = BigDecimal(amount),
        paidBy = paidBy,
        splitType = SplitType.SETTLEMENT,
        partnerAShare = BigDecimal.ZERO.setScale(2),
        partnerBShare = BigDecimal.ZERO.setScale(2),
    )

    private fun voidRow(
        txId: String,
        refTxId: String,
    ): LedgerRow = LedgerRow(
        txId = txId,
        recordType = RecordType.VOID,
        refTxId = refTxId,
        date = "2026-11-23",
        createdAt = "2026-11-23T15:00:00Z",
        createdBy = "alex@demo.local",
        description = "Voided transaction",
        categoryGroup = "",
        category = "",
        amount = BigDecimal.ZERO.setScale(2),
        paidBy = PartnerRole.PARTNER_A,
        splitType = SplitType.EQUAL,
        partnerAShare = BigDecimal.ZERO.setScale(2),
        partnerBShare = BigDecimal.ZERO.setScale(2),
    )
}
