package vige.land.muchopesos.domain.logic

import java.time.Instant
import java.time.LocalDate
import vige.land.muchopesos.domain.model.ActiveTransaction
import vige.land.muchopesos.domain.model.CategoryCatalog
import vige.land.muchopesos.domain.model.LedgerRow
import vige.land.muchopesos.domain.model.RecordType

/**
 * Reduces an append-only list of [LedgerRow] records into active transactions by filtering
 * out `VOID` tombstones and every transaction referenced by a `VOID`, then sorting
 * chronologically by `date DESC, createdAt DESC`.
 */
object LedgerReducer {

    fun reduce(rows: List<LedgerRow>): List<ActiveTransaction> {
        if (rows.isEmpty()) return emptyList()

        val voidedIds: Set<String> = rows
            .asSequence()
            .filter { it.recordType == RecordType.VOID }
            .map { it.refTxId.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        return rows
            .asSequence()
            .filter { it.recordType != RecordType.VOID }
            .filter { row ->
                val id = row.txId.trim()
                id.isNotEmpty() && id !in voidedIds
            }
            .map { row -> row.toActiveTransaction() }
            .sortedWith(ACTIVE_TX_COMPARATOR)
            .toList()
    }

    private fun LedgerRow.toActiveTransaction(): ActiveTransaction {
        val resolvedCategory = if (recordType == RecordType.SETTLEMENT) {
            CategoryCatalog.SETTLEMENT
        } else {
            CategoryCatalog.resolve(category, categoryGroup)
        }
        return ActiveTransaction(
            txId = txId.trim(),
            recordType = recordType,
            date = parseDateOrFallback(date, createdAt),
            createdAt = createdAt.trim(),
            createdBy = createdBy.trim(),
            description = description,
            category = resolvedCategory,
            amount = amount,
            paidBy = paidBy,
            splitType = splitType,
            partnerAShare = partnerAShare,
            partnerBShare = partnerBShare,
            splitMeta = splitMeta,
            notes = notes,
        )
    }

    private fun parseDateOrFallback(rawDate: String, createdAt: String): LocalDate {
        val trimmedDate = rawDate.trim()
        if (trimmedDate.isNotEmpty()) {
            runCatching { return LocalDate.parse(trimmedDate) }
            if (trimmedDate.length >= 10) {
                runCatching { return LocalDate.parse(trimmedDate.substring(0, 10)) }
            }
        }
        val trimmedCreatedAt = createdAt.trim()
        if (trimmedCreatedAt.length >= 10) {
            runCatching { return LocalDate.parse(trimmedCreatedAt.substring(0, 10)) }
        }
        return LocalDate.EPOCH
    }

    private val ACTIVE_TX_COMPARATOR = Comparator<ActiveTransaction> { a, b ->
        val dateCmp = b.date.compareTo(a.date)
        if (dateCmp != 0) return@Comparator dateCmp

        val createdCmp = compareCreatedAtDesc(a.createdAt, b.createdAt)
        if (createdCmp != 0) return@Comparator createdCmp

        b.txId.compareTo(a.txId)
    }

    private fun compareCreatedAtDesc(a: String, b: String): Int {
        val instantA = runCatching { Instant.parse(a) }.getOrNull()
        val instantB = runCatching { Instant.parse(b) }.getOrNull()
        if (instantA != null && instantB != null) {
            return instantB.compareTo(instantA)
        }
        return b.compareTo(a)
    }
}
