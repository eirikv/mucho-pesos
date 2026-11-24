package vige.land.muchopesos.data.sheet

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vige.land.muchopesos.domain.model.CategoryCatalog
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.LedgerRow
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.RecordType
import vige.land.muchopesos.domain.model.SplitType

class SheetRowCodecTest {

    @Test
    fun `config round-trip preserves all fields cleanly`() {
        val original = LedgerConfig(
            spreadsheetId = "1AbCdEfGhIjKlMnOpQrStUvWxYz",
            ledgerName = "Our Household",
            homeCurrencyCode = "NOK",
            partnerAEmail = "alex@example.com",
            partnerAName = "Alex",
            partnerBEmail = "sam@example.com",
            partnerBName = "Sam",
            createdAt = "2026-11-24T12:00:00Z",
        )

        val encoded = SheetRowCodec.encodeConfig(original)
        assertEquals(SheetRowCodec.CONFIG_HEADERS, encoded.first())

        val decoded = SheetRowCodec.decodeConfig(original.spreadsheetId, encoded)
        assertEquals(original, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeConfig throws when sheet has no Mucho Pesos config keys`() {
        SheetRowCodec.decodeConfig(
            spreadsheetId = "invalid-sheet",
            rows = listOf(
                listOf("Name", "Score"),
                listOf("Alice", "42"),
            ),
        )
    }

    @Test
    fun `ledger rows round-trip preserves EXPENSE SETTLEMENT and VOID with exact BigDecimal`() {
        val expense = LedgerRow(
            txId = "tx-100",
            recordType = RecordType.EXPENSE,
            refTxId = "",
            date = "2026-11-20",
            createdAt = "2026-11-20T18:30:00Z",
            createdBy = "alex@example.com",
            description = "Weekly Groceries",
            categoryGroup = CategoryGroup.FOOD.name,
            category = CategoryCatalog.GROCERIES.name,
            amount = BigDecimal("123.45"),
            paidBy = PartnerRole.PARTNER_A,
            splitType = SplitType.PERCENTAGE,
            partnerAShare = BigDecimal("74.07"),
            partnerBShare = BigDecimal("49.38"),
            splitMeta = "60:40",
            notes = "Organic veggies",
        )
        val settlement = LedgerRow(
            txId = "tx-101",
            recordType = RecordType.SETTLEMENT,
            refTxId = "",
            date = "2026-11-21",
            createdAt = "2026-11-21T09:00:00Z",
            createdBy = "sam@example.com",
            description = "Settlement payment",
            categoryGroup = CategoryGroup.SETTLEMENT.name,
            category = CategoryCatalog.SETTLEMENT.name,
            amount = BigDecimal("49.38"),
            paidBy = PartnerRole.PARTNER_B,
            splitType = SplitType.SETTLEMENT,
            partnerAShare = BigDecimal("0.00"),
            partnerBShare = BigDecimal("0.00"),
            splitMeta = "",
            notes = "Bank transfer",
        )
        val voidRow = LedgerRow(
            txId = "tx-102",
            recordType = RecordType.VOID,
            refTxId = "tx-100",
            date = "2026-11-22",
            createdAt = "2026-11-22T10:00:00Z",
            createdBy = "alex@example.com",
            description = "Voided transaction",
            categoryGroup = "",
            category = "",
            amount = BigDecimal("0.00"),
            paidBy = PartnerRole.PARTNER_A,
            splitType = SplitType.EQUAL,
            partnerAShare = BigDecimal("0.00"),
            partnerBShare = BigDecimal("0.00"),
            splitMeta = "",
            notes = "",
        )

        val matrix = listOf(SheetRowCodec.LEDGER_HEADERS) +
            SheetRowCodec.encodeLedgerRows(listOf(expense, settlement, voidRow), scale = 2)

        val decoded = SheetRowCodec.decodeLedgerRows(matrix, scale = 2)
        assertEquals(3, decoded.size)
        assertEquals(expense, decoded[0])
        assertEquals(settlement, decoded[1])
        assertEquals(voidRow, decoded[2])
        assertEquals(0, BigDecimal("123.45").compareTo(decoded[0].amount))
        assertEquals(0, BigDecimal("74.07").compareTo(decoded[0].partnerAShare))
        assertEquals(0, BigDecimal("49.38").compareTo(decoded[0].partnerBShare))
    }

    @Test
    fun `decodeLedgerRow handles short trailing cells blank rows and header row gracefully`() {
        assertNull(SheetRowCodec.decodeLedgerRow(emptyList()))
        assertNull(SheetRowCodec.decodeLedgerRow(listOf("", "   ", "")))
        assertNull(SheetRowCodec.decodeLedgerRow(SheetRowCodec.LEDGER_HEADERS))

        // Google Sheets omits trailing empty cells, so a VOID row might only have 7 columns.
        val shortVoidCells = listOf(
            "tx-void-1",
            "VOID",
            "tx-orig-1",
            "2026-11-24",
            "2026-11-24T15:00:00Z",
            "alex@example.com",
            "Deleted entry",
        )
        val decoded = SheetRowCodec.decodeLedgerRow(shortVoidCells, scale = 2)
        assertNotNull(decoded)
        assertEquals("tx-void-1", decoded!!.txId)
        assertEquals(RecordType.VOID, decoded.recordType)
        assertEquals("tx-orig-1", decoded.refTxId)
        assertEquals(0, BigDecimal.ZERO.compareTo(decoded.amount))
        assertEquals("", decoded.notes)
        assertEquals("", decoded.splitMeta)
    }

    @Test
    fun `decodeLedgerRow preserves zero-decimal JPY precision and formatted sheet numbers`() {
        val jpyRow = LedgerRow(
            txId = "tx-jpy-1",
            recordType = RecordType.EXPENSE,
            date = "2026-11-24",
            createdAt = "2026-11-24T12:00:00Z",
            createdBy = "alex@example.com",
            description = "Ramen Dinner",
            categoryGroup = CategoryGroup.FOOD.name,
            category = CategoryCatalog.DINING_OUT.name,
            amount = BigDecimal("3500"),
            paidBy = PartnerRole.PARTNER_A,
            splitType = SplitType.EQUAL,
            partnerAShare = BigDecimal("1750"),
            partnerBShare = BigDecimal("1750"),
        )
        val encoded = SheetRowCodec.encodeLedgerRow(jpyRow, scale = 0)
        assertEquals("3500", encoded[9])
        assertEquals("1750", encoded[12])

        val decoded = SheetRowCodec.decodeLedgerRow(encoded, scale = 0)
        assertEquals(jpyRow, decoded)

        // Also tolerate manually formatted number with thousands comma & symbol.
        val manuallyFormatted = encoded.toMutableList().apply {
            this[9] = "¥3,500"
        }
        val decodedFormatted = SheetRowCodec.decodeLedgerRow(manuallyFormatted, scale = 0)
        assertTrue(BigDecimal("3500").compareTo(decodedFormatted!!.amount) == 0)
    }
}
