package vige.land.muchopesos.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vige.land.muchopesos.domain.logic.SplitCalculator
import vige.land.muchopesos.domain.logic.SplitRequest
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.SplitType

class SplitCalculatorTest {

    @Test
    fun equalSplit_evenAmount_dividesEvenly() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("10.00"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Equal,
            scale = 2,
        ).getOrThrow()

        assertEquals(SplitType.EQUAL, result.splitType)
        assertEquals("5.00", result.partnerAShare.toPlainString())
        assertEquals("5.00", result.partnerBShare.toPlainString())
        assertEquals("", result.splitMeta)
    }

    @Test
    fun equalSplit_oddAmount_payerAbsorbsRemainder_partnerA() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("10.01"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Equal,
            scale = 2,
        ).getOrThrow()

        assertEquals("5.01", result.partnerAShare.toPlainString())
        assertEquals("5.00", result.partnerBShare.toPlainString())
        assertEquals(0, result.partnerAShare.add(result.partnerBShare).compareTo(BigDecimal("10.01")))
    }

    @Test
    fun equalSplit_oddAmount_payerAbsorbsRemainder_partnerB() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("10.01"),
            paidBy = PartnerRole.PARTNER_B,
            request = SplitRequest.Equal,
            scale = 2,
        ).getOrThrow()

        assertEquals("5.00", result.partnerAShare.toPlainString())
        assertEquals("5.01", result.partnerBShare.toPlainString())
        assertEquals(0, result.partnerAShare.add(result.partnerBShare).compareTo(BigDecimal("10.01")))
    }

    @Test
    fun equalSplit_zeroDecimalCurrencyJpy_oddAmount() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("101"),
            paidBy = PartnerRole.PARTNER_B,
            request = SplitRequest.Equal,
            scale = 0,
        ).getOrThrow()

        assertEquals("50", result.partnerAShare.toPlainString())
        assertEquals("51", result.partnerBShare.toPlainString())
        assertEquals(0, result.partnerAShare.scale())
        assertEquals(0, result.partnerBShare.scale())
    }

    @Test
    fun equalSplit_singleCent_payerAbsorbsCent() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("0.01"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Equal,
            scale = 2,
        ).getOrThrow()

        assertEquals("0.01", result.partnerAShare.toPlainString())
        assertEquals("0.00", result.partnerBShare.toPlainString())
    }

    @Test
    fun exactSplit_validShares_normalizesScale() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("10"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Exact(
                partnerAShare = BigDecimal("3.5"),
                partnerBShare = BigDecimal("6.500"),
            ),
            scale = 2,
        ).getOrThrow()

        assertEquals(SplitType.EXACT, result.splitType)
        assertEquals("3.50", result.partnerAShare.toPlainString())
        assertEquals("6.50", result.partnerBShare.toPlainString())
    }

    @Test
    fun exactSplit_zeroForOnePartner_succeeds() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("42.75"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Exact(
                partnerAShare = BigDecimal.ZERO,
                partnerBShare = BigDecimal("42.75"),
            ),
            scale = 2,
        ).getOrThrow()

        assertEquals("0.00", result.partnerAShare.toPlainString())
        assertEquals("42.75", result.partnerBShare.toPlainString())
    }

    @Test
    fun exactSplit_mismatchedSum_fails() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("10.00"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Exact(
                partnerAShare = BigDecimal("4.00"),
                partnerBShare = BigDecimal("5.99"),
            ),
            scale = 2,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun exactSplit_negativeShare_fails() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("10.00"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Exact(
                partnerAShare = BigDecimal("-1.00"),
                partnerBShare = BigDecimal("11.00"),
            ),
            scale = 2,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun exactSplit_excessPrecision_fails() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("10.00"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Exact(
                partnerAShare = BigDecimal("3.335"),
                partnerBShare = BigDecimal("6.665"),
            ),
            scale = 2,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun percentageSplit_standard60_40() {
        val result = SplitCalculator.calculate(
            totalAmount = BigDecimal("125.00"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Percentage(
                partnerAPercent = BigDecimal("60"),
                partnerBPercent = BigDecimal("40"),
            ),
            scale = 2,
        ).getOrThrow()

        assertEquals(SplitType.PERCENTAGE, result.splitType)
        assertEquals("75.00", result.partnerAShare.toPlainString())
        assertEquals("50.00", result.partnerBShare.toPlainString())
        assertEquals("60:40", result.splitMeta)
    }

    @Test
    fun percentageSplit_repeatingFractions_preservesExactSumInvariant() {
        val total = BigDecimal("99.99")
        val result = SplitCalculator.calculate(
            totalAmount = total,
            paidBy = PartnerRole.PARTNER_B,
            request = SplitRequest.Percentage(
                partnerAPercent = BigDecimal("33.33"),
                partnerBPercent = BigDecimal("66.67"),
            ),
            scale = 2,
        ).getOrThrow()

        assertEquals("33.33", result.partnerAShare.toPlainString())
        assertEquals("66.66", result.partnerBShare.toPlainString())
        assertEquals(0, result.partnerAShare.add(result.partnerBShare).compareTo(total))
        assertEquals("33.33:66.67", result.splitMeta)
    }

    @Test
    fun percentageSplit_zeroDecimalCurrency_preservesSumInvariant() {
        val total = BigDecimal("100")
        val result = SplitCalculator.calculate(
            totalAmount = total,
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Percentage(
                partnerAPercent = BigDecimal("33.33"),
                partnerBPercent = BigDecimal("66.67"),
            ),
            scale = 0,
        ).getOrThrow()

        assertEquals("33", result.partnerAShare.toPlainString())
        assertEquals("67", result.partnerBShare.toPlainString())
        assertEquals(0, result.partnerAShare.add(result.partnerBShare).compareTo(total))
    }

    @Test
    fun percentageSplit_invalidSumsOrNegatives_fail() {
        val wrongSum = SplitCalculator.calculate(
            totalAmount = BigDecimal("50.00"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Percentage(BigDecimal("60"), BigDecimal("39.99")),
        )
        assertTrue(wrongSum.isFailure)

        val negative = SplitCalculator.calculate(
            totalAmount = BigDecimal("50.00"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Percentage(BigDecimal("-10"), BigDecimal("110")),
        )
        assertTrue(negative.isFailure)
    }

    @Test
    fun parsePercentageMeta_validAndInvalidInputs() {
        val parsed = SplitCalculator.parsePercentageMeta("60:40")
        assertNotNull(parsed)
        assertEquals(0, parsed!!.first.compareTo(BigDecimal("60")))
        assertEquals(0, parsed.second.compareTo(BigDecimal("40")))

        val decimalParsed = SplitCalculator.parsePercentageMeta(" 33.33 : 66.67 ")
        assertNotNull(decimalParsed)
        assertEquals(0, decimalParsed!!.first.compareTo(BigDecimal("33.33")))
        assertEquals(0, decimalParsed.second.compareTo(BigDecimal("66.67")))

        assertNull(SplitCalculator.parsePercentageMeta(""))
        assertNull(SplitCalculator.parsePercentageMeta("60:30"))
        assertNull(SplitCalculator.parsePercentageMeta("-10:110"))
        assertNull(SplitCalculator.parsePercentageMeta("abc:40"))
        assertNull(SplitCalculator.parsePercentageMeta("50"))
    }

    @Test
    fun fullSplit_partnerB_and_partnerA() {
        val owedByB = SplitCalculator.calculate(
            totalAmount = BigDecimal("85.40"),
            paidBy = PartnerRole.PARTNER_A,
            request = SplitRequest.Full(owedBy = PartnerRole.PARTNER_B),
            scale = 2,
        ).getOrThrow()

        assertEquals(SplitType.FULL, owedByB.splitType)
        assertEquals("0.00", owedByB.partnerAShare.toPlainString())
        assertEquals("85.40", owedByB.partnerBShare.toPlainString())
        assertEquals("PARTNER_B", owedByB.splitMeta)

        val owedByA = SplitCalculator.calculate(
            totalAmount = BigDecimal("85.40"),
            paidBy = PartnerRole.PARTNER_B,
            request = SplitRequest.Full(owedBy = PartnerRole.PARTNER_A),
            scale = 2,
        ).getOrThrow()

        assertEquals("85.40", owedByA.partnerAShare.toPlainString())
        assertEquals("0.00", owedByA.partnerBShare.toPlainString())
        assertEquals("PARTNER_A", owedByA.splitMeta)
    }

    @Test
    fun calculate_rejectsZeroOrNegativeTotal_andNegativeScale() {
        assertTrue(
            SplitCalculator.calculate(
                totalAmount = BigDecimal.ZERO,
                paidBy = PartnerRole.PARTNER_A,
                request = SplitRequest.Equal,
            ).isFailure,
        )
        assertTrue(
            SplitCalculator.calculate(
                totalAmount = BigDecimal("-5.00"),
                paidBy = PartnerRole.PARTNER_A,
                request = SplitRequest.Equal,
            ).isFailure,
        )
        assertTrue(
            SplitCalculator.calculate(
                totalAmount = BigDecimal("10.00"),
                paidBy = PartnerRole.PARTNER_A,
                request = SplitRequest.Equal,
                scale = -1,
            ).isFailure,
        )
    }
}
