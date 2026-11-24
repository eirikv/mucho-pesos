package vige.land.muchopesos.data.sheet

import java.math.BigDecimal
import java.math.RoundingMode
import vige.land.muchopesos.domain.logic.MoneyFormatter
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.LedgerRow
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.RecordType
import vige.land.muchopesos.domain.model.SplitType

/**
 * Bidirectional codec between domain models ([LedgerConfig], [LedgerRow]) and raw Google Sheets
 * 2D string cell arrays (`Config` and `Ledger` tabs).
 */
object SheetRowCodec {

    const val CONFIG_TAB_NAME = "Config"
    const val LEDGER_TAB_NAME = "Ledger"

    const val CONFIG_WRITE_RANGE = "$CONFIG_TAB_NAME!A1:B10"
    const val CONFIG_READ_RANGE = "$CONFIG_TAB_NAME!A1:B20"
    const val LEDGER_HEADER_RANGE = "$LEDGER_TAB_NAME!A1:P1"
    const val LEDGER_DATA_RANGE = "$LEDGER_TAB_NAME!A2:P"
    const val LEDGER_APPEND_RANGE = "$LEDGER_TAB_NAME!A1:P1"

    const val SCHEMA_VERSION = "1"

    const val KEY_SCHEMA_VERSION = "schema_version"
    const val KEY_LEDGER_NAME = "ledger_name"
    const val KEY_HOME_CURRENCY = "home_currency"
    const val KEY_PARTNER_A_EMAIL = "partner_a_email"
    const val KEY_PARTNER_A_NAME = "partner_a_name"
    const val KEY_PARTNER_B_EMAIL = "partner_b_email"
    const val KEY_PARTNER_B_NAME = "partner_b_name"
    const val KEY_CREATED_AT = "created_at"

    val CONFIG_HEADERS: List<String> = listOf("key", "value")

    val LEDGER_HEADERS: List<String> = listOf(
        "tx_id",
        "record_type",
        "ref_tx_id",
        "date",
        "created_at",
        "created_by",
        "description",
        "category_group",
        "category",
        "amount",
        "paid_by",
        "split_type",
        "partner_a_share",
        "partner_b_share",
        "split_meta",
        "notes",
    )

    fun encodeConfig(config: LedgerConfig): List<List<String>> = listOf(
        CONFIG_HEADERS,
        listOf(KEY_SCHEMA_VERSION, SCHEMA_VERSION),
        listOf(KEY_LEDGER_NAME, config.ledgerName),
        listOf(KEY_HOME_CURRENCY, config.homeCurrencyCode.uppercase()),
        listOf(KEY_PARTNER_A_EMAIL, config.partnerAEmail),
        listOf(KEY_PARTNER_A_NAME, config.partnerAName),
        listOf(KEY_PARTNER_B_EMAIL, config.partnerBEmail),
        listOf(KEY_PARTNER_B_NAME, config.partnerBName),
        listOf(KEY_CREATED_AT, config.createdAt),
    )

    fun decodeConfig(
        spreadsheetId: String,
        rows: List<List<String>>,
    ): LedgerConfig {
        val map = mutableMapOf<String, String>()
        for (row in rows) {
            val rawKey = row.getOrNull(0)?.trim()?.lowercase().orEmpty()
            if (rawKey.isEmpty() || rawKey == "key") continue
            val value = row.getOrNull(1)?.trim().orEmpty()
            map[rawKey] = value
        }

        val hasRequiredMarker = map.containsKey(KEY_SCHEMA_VERSION) ||
            map.containsKey(KEY_LEDGER_NAME) ||
            map.containsKey(KEY_HOME_CURRENCY) ||
            map.containsKey(KEY_PARTNER_A_EMAIL)

        require(hasRequiredMarker) {
            "This spreadsheet does not appear to be a Mucho Pesos ledger (missing Config metadata)."
        }

        val partnerAEmail = map[KEY_PARTNER_A_EMAIL].orEmpty()
        val partnerBEmail = map[KEY_PARTNER_B_EMAIL].orEmpty()

        return LedgerConfig(
            spreadsheetId = spreadsheetId,
            ledgerName = map[KEY_LEDGER_NAME].orEmpty().ifBlank { "Shared Ledger" },
            homeCurrencyCode = map[KEY_HOME_CURRENCY].orEmpty().uppercase().ifBlank { "USD" },
            partnerAEmail = partnerAEmail,
            partnerAName = map[KEY_PARTNER_A_NAME].orEmpty().ifBlank {
                partnerAEmail.substringBefore('@').ifBlank { "Partner A" }
            },
            partnerBEmail = partnerBEmail,
            partnerBName = map[KEY_PARTNER_B_NAME].orEmpty().ifBlank {
                partnerBEmail.substringBefore('@').ifBlank { "Partner B" }
            },
            createdAt = map[KEY_CREATED_AT].orEmpty(),
        )
    }

    fun encodeLedgerRow(
        row: LedgerRow,
        scale: Int = 2,
    ): List<String> = listOf(
        row.txId,
        row.recordType.name,
        row.refTxId,
        row.date,
        row.createdAt,
        row.createdBy,
        row.description,
        row.categoryGroup,
        row.category,
        MoneyFormatter.toSheetPlainString(row.amount, scale),
        row.paidBy.name,
        row.splitType.name,
        MoneyFormatter.toSheetPlainString(row.partnerAShare, scale),
        MoneyFormatter.toSheetPlainString(row.partnerBShare, scale),
        row.splitMeta,
        row.notes,
    )

    fun encodeLedgerRows(
        rows: List<LedgerRow>,
        scale: Int = 2,
    ): List<List<String>> = rows.map { encodeLedgerRow(it, scale) }

    fun decodeLedgerRow(
        cells: List<String>,
        scale: Int = 2,
    ): LedgerRow? {
        if (cells.isEmpty() || cells.all { it.isBlank() }) return null

        val txId = cells.cell(0)
        if (txId.isEmpty() || txId.equals("tx_id", ignoreCase = true)) return null

        val recordType = parseRecordType(cells.cell(1)) ?: return null
        val refTxId = cells.cell(2)
        val date = cells.cell(3)
        val createdAt = cells.cell(4)
        val createdBy = cells.cell(5)
        val description = cells.cell(6)
        val categoryGroup = cells.cell(7)
        val category = cells.cell(8)
        val amount = parseSheetDecimal(cells.cell(9), scale)
        val paidBy = parsePartnerRole(cells.cell(10))
        val splitType = parseSplitType(cells.cell(11), recordType)
        val partnerAShare = parseSheetDecimal(cells.cell(12), scale)
        val partnerBShare = parseSheetDecimal(cells.cell(13), scale)
        val splitMeta = cells.cell(14)
        val notes = cells.cell(15)

        return LedgerRow(
            txId = txId,
            recordType = recordType,
            refTxId = refTxId,
            date = date,
            createdAt = createdAt,
            createdBy = createdBy,
            description = description,
            categoryGroup = categoryGroup,
            category = category,
            amount = amount,
            paidBy = paidBy,
            splitType = splitType,
            partnerAShare = partnerAShare,
            partnerBShare = partnerBShare,
            splitMeta = splitMeta,
            notes = notes,
        )
    }

    fun decodeLedgerRows(
        rows: List<List<String>>,
        scale: Int = 2,
    ): List<LedgerRow> = rows.mapNotNull { decodeLedgerRow(it, scale) }

    private fun List<String>.cell(index: Int): String =
        getOrNull(index)?.trim().orEmpty()

    private fun parseRecordType(raw: String): RecordType? =
        RecordType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

    private fun parsePartnerRole(raw: String): PartnerRole =
        PartnerRole.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: PartnerRole.PARTNER_A

    private fun parseSplitType(raw: String, recordType: RecordType): SplitType =
        SplitType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: when (recordType) {
                RecordType.SETTLEMENT -> SplitType.SETTLEMENT
                RecordType.EXPENSE, RecordType.VOID -> SplitType.EQUAL
            }

    private fun parseSheetDecimal(raw: String, scale: Int): BigDecimal {
        val zero = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return zero

        // Try direct canonical BigDecimal first (fast path for app-written plain strings).
        runCatching { BigDecimal(trimmed) }.getOrNull()?.let { parsed ->
            return normalizeScale(parsed, scale)
        }

        // Resilient fallback if a cell was formatted or edited manually in Google Sheets.
        val cleaned = trimmed
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace(Regex("[^0-9.,-]"), "")

        if (cleaned.isEmpty()) return zero

        val normalizedNumber = when {
            cleaned.contains(',') && cleaned.contains('.') -> {
                // Assume whichever separator appears last is the decimal separator.
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                    cleaned.replace(".", "").replace(',', '.')
                } else {
                    cleaned.replace(",", "")
                }
            }
            cleaned.count { it == ',' } == 1 -> {
                val digitsAfterComma = cleaned.substringAfter(',').length
                if (scale == 0 || (digitsAfterComma == 3 && digitsAfterComma > scale)) {
                    cleaned.replace(",", "")
                } else {
                    cleaned.replace(',', '.')
                }
            }
            cleaned.count { it == ',' } > 1 -> cleaned.replace(",", "")
            else -> cleaned
        }

        val parsed = runCatching { BigDecimal(normalizedNumber) }.getOrNull() ?: return zero
        return normalizeScale(parsed, scale)
    }

    private fun normalizeScale(value: BigDecimal, scale: Int): BigDecimal {
        val scaled = value.setScale(scale, RoundingMode.HALF_UP)
        return if (scaled.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        } else {
            scaled
        }
    }
}
