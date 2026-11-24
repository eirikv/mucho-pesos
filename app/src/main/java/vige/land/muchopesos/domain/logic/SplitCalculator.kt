package vige.land.muchopesos.domain.logic

import java.math.BigDecimal
import java.math.RoundingMode
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.SplitType

sealed interface SplitRequest {
    data object Equal : SplitRequest
    data class Exact(
        val partnerAShare: BigDecimal,
        val partnerBShare: BigDecimal,
    ) : SplitRequest

    data class Percentage(
        val partnerAPercent: BigDecimal,
        val partnerBPercent: BigDecimal,
    ) : SplitRequest

    data class Full(val owedBy: PartnerRole) : SplitRequest
}

data class SplitResult(
    val partnerAShare: BigDecimal,
    val partnerBShare: BigDecimal,
    val splitType: SplitType,
    val splitMeta: String = "",
)

/**
 * Pure [BigDecimal] split calculator guaranteeing deterministic rounding and the invariant
 * `partnerAShare + partnerBShare == totalAmount` (at currency [scale]).
 */
object SplitCalculator {
    private val TWO = BigDecimal("2")
    private val HUNDRED = BigDecimal("100")

    fun calculate(
        totalAmount: BigDecimal,
        paidBy: PartnerRole,
        request: SplitRequest,
        scale: Int = 2,
    ): Result<SplitResult> {
        if (scale < 0) {
            return Result.failure(IllegalArgumentException("Currency scale must be non-negative"))
        }
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.failure(IllegalArgumentException("Total amount must be greater than zero"))
        }

        val total = totalAmount.setScale(scale, RoundingMode.HALF_UP)
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.failure(IllegalArgumentException("Total amount must be greater than zero"))
        }

        return when (request) {
            is SplitRequest.Equal -> calculateEqual(total, paidBy, scale)
            is SplitRequest.Exact -> calculateExact(total, request, scale)
            is SplitRequest.Percentage -> calculatePercentage(total, request, scale)
            is SplitRequest.Full -> calculateFull(total, request, scale)
        }
    }

    private fun calculateEqual(
        total: BigDecimal,
        paidBy: PartnerRole,
        scale: Int,
    ): Result<SplitResult> {
        val nonPayerShare = total.divide(TWO, scale, RoundingMode.DOWN)
        val payerShare = total.subtract(nonPayerShare).setScale(scale, RoundingMode.HALF_UP)

        val (shareA, shareB) = when (paidBy) {
            PartnerRole.PARTNER_A -> payerShare to nonPayerShare
            PartnerRole.PARTNER_B -> nonPayerShare to payerShare
        }

        return Result.success(
            SplitResult(
                partnerAShare = shareA,
                partnerBShare = shareB,
                splitType = SplitType.EQUAL,
                splitMeta = "",
            ),
        )
    }

    private fun calculateExact(
        total: BigDecimal,
        request: SplitRequest.Exact,
        scale: Int,
    ): Result<SplitResult> {
        if (request.partnerAShare.compareTo(BigDecimal.ZERO) < 0 ||
            request.partnerBShare.compareTo(BigDecimal.ZERO) < 0
        ) {
            return Result.failure(IllegalArgumentException("Split shares cannot be negative"))
        }

        val normA = request.partnerAShare.setScale(scale, RoundingMode.HALF_UP)
        val normB = request.partnerBShare.setScale(scale, RoundingMode.HALF_UP)

        if (normA.compareTo(request.partnerAShare) != 0 ||
            normB.compareTo(request.partnerBShare) != 0
        ) {
            return Result.failure(
                IllegalArgumentException("Split shares cannot exceed $scale decimal places"),
            )
        }

        if (normA.add(normB).compareTo(total) != 0) {
            return Result.failure(
                IllegalArgumentException(
                    "Exact shares (${normA.toPlainString()} + ${normB.toPlainString()}) must equal total (${total.toPlainString()})",
                ),
            )
        }

        return Result.success(
            SplitResult(
                partnerAShare = normA,
                partnerBShare = normB,
                splitType = SplitType.EXACT,
                splitMeta = "",
            ),
        )
    }

    private fun calculatePercentage(
        total: BigDecimal,
        request: SplitRequest.Percentage,
        scale: Int,
    ): Result<SplitResult> {
        val pctA = request.partnerAPercent
        val pctB = request.partnerBPercent

        if (pctA.compareTo(BigDecimal.ZERO) < 0 || pctB.compareTo(BigDecimal.ZERO) < 0) {
            return Result.failure(IllegalArgumentException("Percentages cannot be negative"))
        }

        if (pctA.add(pctB).compareTo(HUNDRED) != 0) {
            return Result.failure(
                IllegalArgumentException("Percentages must add up to 100%"),
            )
        }

        val shareA = total.multiply(pctA).divide(HUNDRED, scale, RoundingMode.HALF_UP)
        val shareB = total.subtract(shareA).setScale(scale, RoundingMode.HALF_UP)

        return Result.success(
            SplitResult(
                partnerAShare = shareA,
                partnerBShare = shareB,
                splitType = SplitType.PERCENTAGE,
                splitMeta = "${formatPercent(pctA)}:${formatPercent(pctB)}",
            ),
        )
    }

    private fun calculateFull(
        total: BigDecimal,
        request: SplitRequest.Full,
        scale: Int,
    ): Result<SplitResult> {
        val zero = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        val (shareA, shareB) = when (request.owedBy) {
            PartnerRole.PARTNER_A -> total to zero
            PartnerRole.PARTNER_B -> zero to total
        }

        return Result.success(
            SplitResult(
                partnerAShare = shareA,
                partnerBShare = shareB,
                splitType = SplitType.FULL,
                splitMeta = request.owedBy.name,
            ),
        )
    }

    /**
     * Parses percentage split metadata (e.g. `"60:40"` or `"33.33:66.67"`) back into exact
     * `(partnerAPercent, partnerBPercent)` values for editing.
     */
    fun parsePercentageMeta(splitMeta: String): Pair<BigDecimal, BigDecimal>? {
        val trimmed = splitMeta.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(':')
        if (parts.size != 2) return null

        val pctA = parts[0].trim().toBigDecimalOrNull() ?: return null
        val pctB = parts[1].trim().toBigDecimalOrNull() ?: return null

        if (pctA.compareTo(BigDecimal.ZERO) < 0 || pctB.compareTo(BigDecimal.ZERO) < 0) {
            return null
        }
        if (pctA.add(pctB).compareTo(HUNDRED) != 0) {
            return null
        }
        return pctA to pctB
    }

    private fun formatPercent(value: BigDecimal): String =
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            "0"
        } else {
            value.stripTrailingZeros().toPlainString()
        }
}
