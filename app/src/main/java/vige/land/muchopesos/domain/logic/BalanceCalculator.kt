package vige.land.muchopesos.domain.logic

import java.math.BigDecimal
import java.math.RoundingMode
import vige.land.muchopesos.domain.model.ActiveTransaction
import vige.land.muchopesos.domain.model.NetBalanceSummary
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.RecordType
import vige.land.muchopesos.domain.model.TransactionImpact

/**
 * Pure [BigDecimal] net balance and per-transaction impact calculator.
 */
object BalanceCalculator {

    fun computeNetBalance(
        transactions: List<ActiveTransaction>,
        scale: Int = 2,
    ): NetBalanceSummary {
        var netForPartnerA = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)

        for (tx in transactions) {
            netForPartnerA = netForPartnerA.add(deltaForPartnerA(tx))
        }

        val normalizedNet = normalizeZero(netForPartnerA.setScale(scale, RoundingMode.HALF_UP), scale)
        val cmp = normalizedNet.compareTo(BigDecimal.ZERO)

        return when {
            cmp > 0 -> NetBalanceSummary(
                netForPartnerA = normalizedNet,
                debtorRole = PartnerRole.PARTNER_B,
                creditorRole = PartnerRole.PARTNER_A,
                amount = normalizedNet,
                isSettledUp = false,
            )

            cmp < 0 -> NetBalanceSummary(
                netForPartnerA = normalizedNet,
                debtorRole = PartnerRole.PARTNER_A,
                creditorRole = PartnerRole.PARTNER_B,
                amount = normalizedNet.abs().setScale(scale, RoundingMode.HALF_UP),
                isSettledUp = false,
            )

            else -> NetBalanceSummary(
                netForPartnerA = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP),
                debtorRole = null,
                creditorRole = null,
                amount = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP),
                isSettledUp = true,
            )
        }
    }

    fun computeImpactForViewer(
        tx: ActiveTransaction,
        viewerRole: PartnerRole,
        scale: Int = 2,
    ): TransactionImpact {
        val netForA = deltaForPartnerA(tx)
        val rawViewerNet = when (viewerRole) {
            PartnerRole.PARTNER_A -> netForA
            PartnerRole.PARTNER_B -> netForA.negate()
        }
        val normalized = normalizeZero(rawViewerNet.setScale(scale, RoundingMode.HALF_UP), scale)

        return TransactionImpact(
            netForViewer = normalized,
            isPayer = tx.paidBy == viewerRole,
            isSettlement = tx.recordType == RecordType.SETTLEMENT,
        )
    }

    private fun deltaForPartnerA(tx: ActiveTransaction): BigDecimal = when (tx.recordType) {
        RecordType.EXPENSE -> when (tx.paidBy) {
            PartnerRole.PARTNER_A -> tx.partnerBShare
            PartnerRole.PARTNER_B -> tx.partnerAShare.negate()
        }

        RecordType.SETTLEMENT -> when (tx.paidBy) {
            PartnerRole.PARTNER_A -> tx.amount
            PartnerRole.PARTNER_B -> tx.amount.negate()
        }

        RecordType.VOID -> BigDecimal.ZERO
    }

    private fun normalizeZero(value: BigDecimal, scale: Int): BigDecimal =
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        } else {
            value
        }
}
