package vige.land.muchopesos.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vige.land.muchopesos.domain.logic.MoneyFormatter
import vige.land.muchopesos.domain.logic.SpreadsheetIdParser
import vige.land.muchopesos.domain.model.CategoryCatalog
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.CurrencyCatalog

class MoneyFormatterAndParserTest {

    @Test
    fun parseUserAmount_acceptsDotAndCommaAndNormalizesScale() {
        assertEquals("42.00", MoneyFormatter.parseUserAmount("42")?.toPlainString())
        assertEquals("42.50", MoneyFormatter.parseUserAmount("42.5")?.toPlainString())
        assertEquals("42.55", MoneyFormatter.parseUserAmount("42.55")?.toPlainString())
        assertEquals("42.50", MoneyFormatter.parseUserAmount("42,5")?.toPlainString())
        assertEquals("42.55", MoneyFormatter.parseUserAmount(" 42,55 ")?.toPlainString())
        assertEquals("0.50", MoneyFormatter.parseUserAmount(".5")?.toPlainString())
        assertEquals("0.50", MoneyFormatter.parseUserAmount(",50")?.toPlainString())
        assertEquals("0.00", MoneyFormatter.parseUserAmount("0")?.toPlainString())
        assertEquals("1500", MoneyFormatter.parseUserAmount("1500", scale = 0)?.toPlainString())
    }

    @Test
    fun parseUserAmount_rejectsInvalidNegativeAndExcessDecimals() {
        assertNull(MoneyFormatter.parseUserAmount(""))
        assertNull(MoneyFormatter.parseUserAmount("   "))
        assertNull(MoneyFormatter.parseUserAmount("-5"))
        assertNull(MoneyFormatter.parseUserAmount("-0.01"))
        assertNull(MoneyFormatter.parseUserAmount("+5"))
        assertNull(MoneyFormatter.parseUserAmount("abc"))
        assertNull(MoneyFormatter.parseUserAmount("$12.34"))
        assertNull(MoneyFormatter.parseUserAmount("."))
        assertNull(MoneyFormatter.parseUserAmount(","))
        assertNull(MoneyFormatter.parseUserAmount("12."))
        assertNull(MoneyFormatter.parseUserAmount("12,"))
        assertNull(MoneyFormatter.parseUserAmount("12.345", scale = 2))
        assertNull(MoneyFormatter.parseUserAmount("100.5", scale = 0))
        assertNull(MoneyFormatter.parseUserAmount("1.2.3"))
        assertNull(MoneyFormatter.parseUserAmount("1,2,3"))
        assertNull(MoneyFormatter.parseUserAmount("1,234.56"))
    }

    @Test
    fun format_andFormatSigned_variousCurrencies() {
        assertEquals("$42.50", MoneyFormatter.format(BigDecimal("42.5"), CurrencyCatalog.USD))
        assertEquals("-$42.50", MoneyFormatter.format(BigDecimal("-42.50"), CurrencyCatalog.USD))
        assertEquals("$1,234.56", MoneyFormatter.format(BigDecimal("1234.56"), CurrencyCatalog.USD))
        assertEquals("€1,250.00", MoneyFormatter.format(BigDecimal("1250"), CurrencyCatalog.EUR))
        assertEquals("450.00 kr", MoneyFormatter.format(BigDecimal("450"), CurrencyCatalog.NOK))
        assertEquals("CHF 99.95", MoneyFormatter.format(BigDecimal("99.95"), CurrencyCatalog.CHF))
        assertEquals("¥1,500", MoneyFormatter.format(BigDecimal("1500"), CurrencyCatalog.JPY))

        assertEquals("+$42.50", MoneyFormatter.formatSigned(BigDecimal("42.50"), CurrencyCatalog.USD))
        assertEquals("-$42.50", MoneyFormatter.formatSigned(BigDecimal("-42.50"), CurrencyCatalog.USD))
        assertEquals("$0.00", MoneyFormatter.formatSigned(BigDecimal.ZERO, CurrencyCatalog.USD))
    }

    @Test
    fun toSheetPlainString_outputsCanonicalDecimals() {
        assertEquals("45.50", MoneyFormatter.toSheetPlainString(BigDecimal("45.5")))
        assertEquals("0.00", MoneyFormatter.toSheetPlainString(BigDecimal("-0.00")))
        assertEquals("101", MoneyFormatter.toSheetPlainString(BigDecimal("101"), scale = 0))
        assertEquals("1000000.00", MoneyFormatter.toSheetPlainString(BigDecimal("1E+6"), scale = 2))
    }

    @Test
    fun spreadsheetIdParser_extractsFromDesktopMobileQueryAndRawInputs() {
        val expectedId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms"

        assertEquals(
            expectedId,
            SpreadsheetIdParser.extractSpreadsheetId(
                "https://docs.google.com/spreadsheets/d/$expectedId/edit#gid=0",
            ),
        )
        assertEquals(
            expectedId,
            SpreadsheetIdParser.extractSpreadsheetId(
                "https://docs.google.com/spreadsheets/u/1/d/$expectedId/edit?usp=sharing",
            ),
        )
        assertEquals(
            expectedId,
            SpreadsheetIdParser.extractSpreadsheetId(
                "https://m.docs.google.com/spreadsheets/d/$expectedId/htmlview",
            ),
        )
        assertEquals(
            expectedId,
            SpreadsheetIdParser.extractSpreadsheetId(
                "docs.google.com/spreadsheets/d/$expectedId",
            ),
        )
        assertEquals(
            expectedId,
            SpreadsheetIdParser.extractSpreadsheetId(
                "https://drive.google.com/open?id=$expectedId",
            ),
        )
        assertEquals(
            expectedId,
            SpreadsheetIdParser.extractSpreadsheetId(
                "https://drive.google.com/file/d/$expectedId/view?usp=drive_link",
            ),
        )
        assertEquals(
            expectedId,
            SpreadsheetIdParser.extractSpreadsheetId(
                "https://docs.google.com/spreadsheets/ccc?key=$expectedId",
            ),
        )
        assertEquals(
            expectedId,
            SpreadsheetIdParser.extractSpreadsheetId("  $expectedId  "),
        )
        assertEquals(
            "https://docs.google.com/spreadsheets/d/$expectedId/edit",
            SpreadsheetIdParser.buildSheetUrl(expectedId),
        )
    }

    @Test
    fun spreadsheetIdParser_rejectsInvalidUrlsAndGarbage() {
        assertNull(SpreadsheetIdParser.extractSpreadsheetId(""))
        assertNull(SpreadsheetIdParser.extractSpreadsheetId("   "))
        assertNull(SpreadsheetIdParser.extractSpreadsheetId("short"))
        assertNull(SpreadsheetIdParser.extractSpreadsheetId("https://example.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit"))
        assertNull(SpreadsheetIdParser.extractSpreadsheetId("https://docs.google.com/spreadsheets/d/e/2PACX-1vRealPublishedKey12345/pubhtml"))
        assertNull(SpreadsheetIdParser.extractSpreadsheetId("has spaces in id 1234567890"))
    }

    @Test
    fun categoryCatalog_has21HouseholdCategoriesAndResilientResolution() {
        assertEquals(21, CategoryCatalog.expenseCategories.size)
        assertEquals(6, CategoryCatalog.groupedExpenseCategories.size)
        assertEquals(22, CategoryCatalog.allCategories.size)

        assertEquals(CategoryCatalog.GROCERIES, CategoryCatalog.resolve("groceries"))
        assertEquals(CategoryCatalog.GROCERIES, CategoryCatalog.resolve("Groceries"))
        assertEquals(CategoryCatalog.STREAMING_SUBSCRIPTIONS, CategoryCatalog.resolve("Streaming"))
        assertEquals(CategoryCatalog.CAR_MAINTENANCE, CategoryCatalog.resolve("car"))
        assertEquals(CategoryCatalog.SETTLEMENT, CategoryCatalog.resolve("Settlement", "SETTLEMENT"))
        assertEquals(CategoryCatalog.SETTLEMENT, CategoryCatalog.resolve("", "SETTLEMENT"))
        assertEquals(CategoryCatalog.OTHER, CategoryCatalog.resolve(null, null))

        val custom = CategoryCatalog.resolve("Solar Panels", "UTILITIES")
        assertEquals("Solar Panels", custom.name)
        assertEquals(CategoryGroup.UTILITIES, custom.group)
    }

    @Test
    fun currencyCatalog_resolvesKnownAndFallbackCurrencies() {
        assertEquals(CurrencyCatalog.USD, CurrencyCatalog.findByCode("usd"))
        assertEquals(CurrencyCatalog.JPY, CurrencyCatalog.findByCode(" JPY "))
        assertNull(CurrencyCatalog.findByCode("ZZZ"))

        assertEquals(CurrencyCatalog.USD, CurrencyCatalog.resolve(""))
        val unknown = CurrencyCatalog.resolve("zar")
        assertEquals("ZAR", unknown.code)
        assertEquals("ZAR", unknown.symbol)
        assertEquals(2, unknown.fractionDigits)
    }
}
