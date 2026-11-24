package vige.land.muchopesos.data.sheet

import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import vige.land.muchopesos.domain.model.CategoryCatalog
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.LedgerRow
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.RecordType
import vige.land.muchopesos.domain.model.SplitType

@Serializable
private data class DemoSpreadsheetRecord(
    val id: String,
    val modifiedTime: String,
    val configRows: List<List<String>>,
    val ledgerRows: List<List<String>>,
)

@Serializable
private data class DemoSheetStorePayload(
    val spreadsheets: Map<String, DemoSpreadsheetRecord> = emptyMap(),
)

/**
 * Local file-backed simulated Google Sheet exercising the exact same append-only [SheetRowCodec]
 * encoding and [LedgerSheetGateway] contract as [GoogleSheetsGateway].
 */
class DemoSheetGateway(
    private val storageFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val nowIsoProvider: () -> String = { Instant.now().toString() },
) : LedgerSheetGateway {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun createLedger(
        ledgerName: String,
        homeCurrencyCode: String,
        partnerAEmail: String,
        partnerAName: String,
        partnerBEmail: String,
        partnerBName: String,
    ): LedgerConfig = mutex.withLock {
        withContext(ioDispatcher) {
            val store = loadOrSeedStoreLocked()
            val spreadsheetId = "demo-${UUID.randomUUID().toString().take(12)}"
            val now = nowIsoProvider()
            val config = LedgerConfig(
                spreadsheetId = spreadsheetId,
                ledgerName = ledgerName.trim(),
                homeCurrencyCode = homeCurrencyCode.trim().uppercase(),
                partnerAEmail = partnerAEmail.trim(),
                partnerAName = partnerAName.trim(),
                partnerBEmail = partnerBEmail.trim(),
                partnerBName = partnerBName.trim(),
                createdAt = now,
            )

            val record = DemoSpreadsheetRecord(
                id = spreadsheetId,
                modifiedTime = now,
                configRows = SheetRowCodec.encodeConfig(config),
                ledgerRows = listOf(SheetRowCodec.LEDGER_HEADERS),
            )
            saveStoreLocked(store.copy(spreadsheets = store.spreadsheets + (spreadsheetId to record)))
            config
        }
    }

    override suspend fun listAvailableSpreadsheets(query: String?): List<DriveSpreadsheetItem> =
        mutex.withLock {
            withContext(ioDispatcher) {
                val store = loadOrSeedStoreLocked()
                val needle = query?.trim().orEmpty()
                store.spreadsheets.values
                    .mapNotNull { record ->
                        val config = runCatching {
                            SheetRowCodec.decodeConfig(record.id, record.configRows)
                        }.getOrNull() ?: return@mapNotNull null
                        DriveSpreadsheetItem(
                            id = record.id,
                            name = config.ledgerName,
                            modifiedTime = record.modifiedTime,
                            ownerEmail = config.partnerAEmail.ifBlank { null },
                        )
                    }
                    .filter { item ->
                        needle.isEmpty() ||
                            item.name.contains(needle, ignoreCase = true) ||
                            item.id.contains(needle, ignoreCase = true)
                    }
                    .sortedByDescending { it.modifiedTime }
            }
        }

    override suspend fun loadLedger(spreadsheetId: String): Pair<LedgerConfig, List<LedgerRow>> =
        mutex.withLock {
            withContext(ioDispatcher) {
                val store = loadOrSeedStoreLocked()
                val record = store.spreadsheets[spreadsheetId]
                    ?: throw IllegalArgumentException(
                        "Demo spreadsheet '$spreadsheetId' was not found. Create a new ledger or pick one from the list.",
                    )

                val config = SheetRowCodec.decodeConfig(record.id, record.configRows)
                val rows = SheetRowCodec.decodeLedgerRows(
                    rows = record.ledgerRows,
                    scale = config.currencySpec.fractionDigits,
                )
                config to rows
            }
        }

    override suspend fun updateConfig(config: LedgerConfig) {
        mutex.withLock {
            withContext(ioDispatcher) {
                val store = loadOrSeedStoreLocked()
                val existing = store.spreadsheets[config.spreadsheetId]
                    ?: throw IllegalArgumentException(
                        "Demo spreadsheet '${config.spreadsheetId}' was not found.",
                    )
                val updated = existing.copy(
                    modifiedTime = nowIsoProvider(),
                    configRows = SheetRowCodec.encodeConfig(config),
                )
                saveStoreLocked(
                    store.copy(spreadsheets = store.spreadsheets + (config.spreadsheetId to updated)),
                )
            }
        }
    }

    override suspend fun appendRows(
        spreadsheetId: String,
        rows: List<LedgerRow>,
        scale: Int,
    ) {
        if (rows.isEmpty()) return
        mutex.withLock {
            withContext(ioDispatcher) {
                val store = loadOrSeedStoreLocked()
                val existing = store.spreadsheets[spreadsheetId]
                    ?: throw IllegalArgumentException(
                        "Demo spreadsheet '$spreadsheetId' was not found.",
                    )
                val encoded = SheetRowCodec.encodeLedgerRows(rows, scale)
                val updated = existing.copy(
                    modifiedTime = nowIsoProvider(),
                    ledgerRows = existing.ledgerRows + encoded,
                )
                saveStoreLocked(
                    store.copy(spreadsheets = store.spreadsheets + (spreadsheetId to updated)),
                )
            }
        }
    }

    /**
     * Returns the raw append-only cell matrix for [spreadsheetId] (including header row and VOID rows).
     * Useful for inspecting append-only invariants in unit tests.
     */
    suspend fun readRawLedgerMatrix(spreadsheetId: String): List<List<String>> = mutex.withLock {
        withContext(ioDispatcher) {
            val store = loadOrSeedStoreLocked()
            store.spreadsheets[spreadsheetId]?.ledgerRows.orEmpty()
        }
    }

    suspend fun resetDefaultDemoLedger() = mutex.withLock {
        withContext(ioDispatcher) {
            val store = loadOrSeedStoreLocked()
            val seeded = buildDefaultDemoSpreadsheet()
            saveStoreLocked(
                store.copy(spreadsheets = store.spreadsheets + (seeded.id to seeded)),
            )
        }
    }

    private fun loadOrSeedStoreLocked(): DemoSheetStorePayload {
        if (storageFile.exists()) {
            val parsed = runCatching {
                json.decodeFromString<DemoSheetStorePayload>(storageFile.readText())
            }.getOrNull()
            if (parsed != null && parsed.spreadsheets.isNotEmpty()) {
                return parsed
            }
        }

        val seeded = buildDefaultDemoSpreadsheet()
        val initialStore = DemoSheetStorePayload(
            spreadsheets = mapOf(seeded.id to seeded),
        )
        saveStoreLocked(initialStore)
        return initialStore
    }

    private fun saveStoreLocked(payload: DemoSheetStorePayload) {
        storageFile.parentFile?.mkdirs()
        val encoded = json.encodeToString(payload)
        val tmpFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
        tmpFile.writeText(encoded)
        if (!tmpFile.renameTo(storageFile)) {
            storageFile.writeText(encoded)
            tmpFile.delete()
        }
    }

    private fun buildDefaultDemoSpreadsheet(): DemoSpreadsheetRecord {
        val today = todayProvider()
        val currentMonth = YearMonth.from(today)
        val prevMonth = currentMonth.minusMonths(1)
        val twoMonthsAgo = currentMonth.minusMonths(2)
        val nowIso = nowIsoProvider()

        fun currentMonthDate(daysAgo: Long): LocalDate {
            val candidate = today.minusDays(daysAgo)
            return if (YearMonth.from(candidate) == currentMonth) {
                candidate
            } else {
                currentMonth.atDay(1)
            }
        }

        val config = LedgerConfig(
            spreadsheetId = DEFAULT_DEMO_SPREADSHEET_ID,
            ledgerName = DEFAULT_DEMO_LEDGER_NAME,
            homeCurrencyCode = "USD",
            partnerAEmail = DEMO_PARTNER_A_EMAIL,
            partnerAName = DEMO_PARTNER_A_NAME,
            partnerBEmail = DEMO_PARTNER_B_EMAIL,
            partnerBName = DEMO_PARTNER_B_NAME,
            createdAt = nowIso,
        )

        val scale = config.currencySpec.fractionDigits
        // Multi-month pre-seeded ledger (19 rows: 17 expenses + 2 settlements across 3 months):
        // - twoMonthsAgo: 5 expenses ($419.70), netForPartnerA delta = +$32.10
        // - prevMonth:    6 expenses ($606.29) + 1 settlement ($20.00), netForPartnerA delta = -$6.64
        // - currentMonth: 6 expenses ($516.09) + 1 settlement ($25.00), netForPartnerA delta = +$51.12
        // Combined net balance: Sam (PARTNER_B) owes Alex (PARTNER_A) $76.58.
        val seedRows = listOf(
            // =====================================================================
            // Two months ago (5 expenses: $419.70 total, deltaA = +$32.10)
            // =====================================================================
            // Equal 50/50, Alex paid $138.20 -> A: 69.10, B: 69.10 -> deltaA = +69.10
            LedgerRow(
                txId = "demo-tx-201",
                recordType = RecordType.EXPENSE,
                date = twoMonthsAgo.atDay(4).toString(),
                createdAt = "${twoMonthsAgo.atDay(4)}T11:30:00Z",
                createdBy = DEMO_PARTNER_A_EMAIL,
                description = "Farmers Market & Groceries",
                categoryGroup = CategoryGroup.FOOD.name,
                category = CategoryCatalog.GROCERIES.name,
                amount = BigDecimal("138.20"),
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("69.10"),
                partnerBShare = BigDecimal("69.10"),
                notes = "Weekly produce and pantry staples",
            ),
            // Equal 50/50, Sam paid $89.00 -> A: 44.50, B: 44.50 -> deltaA = -44.50
            LedgerRow(
                txId = "demo-tx-202",
                recordType = RecordType.EXPENSE,
                date = twoMonthsAgo.atDay(9).toString(),
                createdAt = "${twoMonthsAgo.atDay(9)}T08:45:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "Fiber Internet & Mobile",
                categoryGroup = CategoryGroup.UTILITIES.name,
                category = CategoryCatalog.INTERNET_PHONE.name,
                amount = BigDecimal("89.00"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("44.50"),
                partnerBShare = BigDecimal("44.50"),
                notes = "Monthly home broadband",
            ),
            // Percentage 55:45, Alex paid $120.00 -> A: 66.00, B: 54.00 -> deltaA = +54.00
            LedgerRow(
                txId = "demo-tx-203",
                recordType = RecordType.EXPENSE,
                date = twoMonthsAgo.atDay(14).toString(),
                createdAt = "${twoMonthsAgo.atDay(14)}T15:10:00Z",
                createdBy = DEMO_PARTNER_A_EMAIL,
                description = "Oil Change & Tire Rotation",
                categoryGroup = CategoryGroup.TRANSPORT.name,
                category = CategoryCatalog.CAR_MAINTENANCE.name,
                amount = BigDecimal("120.00"),
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.PERCENTAGE,
                partnerAShare = BigDecimal("66.00"),
                partnerBShare = BigDecimal("54.00"),
                splitMeta = "55:45",
                notes = "Scheduled hatchback service",
            ),
            // Exact, Sam paid $48.50 -> A: 22.50, B: 26.00 -> deltaA = -22.50
            LedgerRow(
                txId = "demo-tx-204",
                recordType = RecordType.EXPENSE,
                date = twoMonthsAgo.atDay(19).toString(),
                createdAt = "${twoMonthsAgo.atDay(19)}T20:15:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "Indie Cinema & Popcorn",
                categoryGroup = CategoryGroup.ENTERTAINMENT.name,
                category = CategoryCatalog.MOVIES_EVENTS.name,
                amount = BigDecimal("48.50"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.EXACT,
                partnerAShare = BigDecimal("22.50"),
                partnerBShare = BigDecimal("26.00"),
                notes = "Saturday evening screening",
            ),
            // Full (100% owed by Alex), Sam paid $24.00 -> A: 24.00, B: 0.00 -> deltaA = -24.00
            LedgerRow(
                txId = "demo-tx-205",
                recordType = RecordType.EXPENSE,
                date = twoMonthsAgo.atDay(24).toString(),
                createdAt = "${twoMonthsAgo.atDay(24)}T09:20:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "Specialty Coffee Beans for Alex",
                categoryGroup = CategoryGroup.FOOD.name,
                category = CategoryCatalog.COFFEE_SNACKS.name,
                amount = BigDecimal("24.00"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.FULL,
                partnerAShare = BigDecimal("24.00"),
                partnerBShare = BigDecimal("0.00"),
                splitMeta = PartnerRole.PARTNER_A.name,
                notes = "Single-origin espresso roast",
            ),

            // =====================================================================
            // Previous month (6 expenses: $606.29 total + 1 settlement $20.00, deltaA = -$6.64)
            // =====================================================================
            // Equal 50/50, Alex paid $162.80 -> A: 81.40, B: 81.40 -> deltaA = +81.40
            LedgerRow(
                txId = "demo-tx-101",
                recordType = RecordType.EXPENSE,
                date = prevMonth.atDay(3).toString(),
                createdAt = "${prevMonth.atDay(3)}T17:40:00Z",
                createdBy = DEMO_PARTNER_A_EMAIL,
                description = "Costco Bulk Groceries",
                categoryGroup = CategoryGroup.FOOD.name,
                category = CategoryCatalog.GROCERIES.name,
                amount = BigDecimal("162.80"),
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("81.40"),
                partnerBShare = BigDecimal("81.40"),
                notes = "Monthly stock-up trip",
            ),
            // Exact, Sam paid $114.60 -> A: 54.60, B: 60.00 -> deltaA = -54.60
            LedgerRow(
                txId = "demo-tx-102",
                recordType = RecordType.EXPENSE,
                date = prevMonth.atDay(8).toString(),
                createdAt = "${prevMonth.atDay(8)}T13:25:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "IKEA Kitchen Organizer & Lamps",
                categoryGroup = CategoryGroup.HOUSING.name,
                category = CategoryCatalog.HOUSEHOLD_SUPPLIES.name,
                amount = BigDecimal("114.60"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.EXACT,
                partnerAShare = BigDecimal("54.60"),
                partnerBShare = BigDecimal("60.00"),
                notes = "Pantry bins and warm LED bulbs",
            ),
            // Equal 50/50, Sam paid $136.40 -> A: 68.20, B: 68.20 -> deltaA = -68.20
            LedgerRow(
                txId = "demo-tx-103",
                recordType = RecordType.EXPENSE,
                date = prevMonth.atDay(12).toString(),
                createdAt = "${prevMonth.atDay(12)}T09:00:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "Electric & Gas Utility Bill",
                categoryGroup = CategoryGroup.UTILITIES.name,
                category = CategoryCatalog.ELECTRICITY_GAS.name,
                amount = BigDecimal("136.40"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("68.20"),
                partnerBShare = BigDecimal("68.20"),
                notes = "Auto-pay statement",
            ),
            // Equal 50/50 odd cent, Sam paid $34.99 -> A: 17.49, B: 17.50 -> deltaA = -17.49
            LedgerRow(
                txId = "demo-tx-104",
                recordType = RecordType.EXPENSE,
                date = prevMonth.atDay(16).toString(),
                createdAt = "${prevMonth.atDay(16)}T11:00:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "Streaming & Music Bundle",
                categoryGroup = CategoryGroup.ENTERTAINMENT.name,
                category = CategoryCatalog.STREAMING_SUBSCRIPTIONS.name,
                amount = BigDecimal("34.99"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("17.49"),
                partnerBShare = BigDecimal("17.50"),
                notes = "Shared family plan",
            ),
            // Percentage 60:40, Alex paid $65.00 -> A: 39.00, B: 26.00 -> deltaA = +26.00
            LedgerRow(
                txId = "demo-tx-105",
                recordType = RecordType.EXPENSE,
                date = prevMonth.atDay(19).toString(),
                createdAt = "${prevMonth.atDay(19)}T16:15:00Z",
                createdBy = DEMO_PARTNER_A_EMAIL,
                description = "Shell Station Fill-Up",
                categoryGroup = CategoryGroup.TRANSPORT.name,
                category = CategoryCatalog.FUEL.name,
                amount = BigDecimal("65.00"),
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.PERCENTAGE,
                partnerAShare = BigDecimal("39.00"),
                partnerBShare = BigDecimal("26.00"),
                splitMeta = "60:40",
                notes = "Autumn cabin drive",
            ),
            // Equal 50/50, Alex paid $92.50 -> A: 46.25, B: 46.25 -> deltaA = +46.25
            LedgerRow(
                txId = "demo-tx-106",
                recordType = RecordType.EXPENSE,
                date = prevMonth.atDay(23).toString(),
                createdAt = "${prevMonth.atDay(23)}T20:30:00Z",
                createdBy = DEMO_PARTNER_A_EMAIL,
                description = "Neighborhood Sushi Dinner",
                categoryGroup = CategoryGroup.FOOD.name,
                category = CategoryCatalog.DINING_OUT.name,
                amount = BigDecimal("92.50"),
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("46.25"),
                partnerBShare = BigDecimal("46.25"),
                notes = "Date night rolls and ramen",
            ),
            // Settlement, Sam paid Alex $20.00 -> deltaA = -20.00
            LedgerRow(
                txId = "demo-tx-107",
                recordType = RecordType.SETTLEMENT,
                date = prevMonth.atDay(26).toString(),
                createdAt = "${prevMonth.atDay(26)}T19:00:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "Settlement payment",
                categoryGroup = CategoryGroup.SETTLEMENT.name,
                category = CategoryCatalog.SETTLEMENT.name,
                amount = BigDecimal("20.00"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.SETTLEMENT,
                partnerAShare = BigDecimal("0.00"),
                partnerBShare = BigDecimal("0.00"),
                notes = "End-of-month transfer",
            ),

            // =====================================================================
            // Current month (6 expenses: $516.09 total + 1 settlement $25.00, deltaA = +$51.12)
            // =====================================================================
            // 1. Groceries (Equal 50/50, Alex paid $124.50 -> Sam owes +$62.25)
            LedgerRow(
                txId = "demo-tx-001",
                recordType = RecordType.EXPENSE,
                date = currentMonthDate(12).toString(),
                createdAt = "${currentMonthDate(12)}T18:20:00Z",
                createdBy = DEMO_PARTNER_A_EMAIL,
                description = "Trader Joe's Weekly Groceries",
                categoryGroup = CategoryGroup.FOOD.name,
                category = CategoryCatalog.GROCERIES.name,
                amount = BigDecimal("124.50"),
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("62.25"),
                partnerBShare = BigDecimal("62.25"),
                notes = "Produce, pasta, and coffee beans",
            ),
            // 2. Electric Bill (Equal 50/50, Sam paid $148.00 -> Alex owes -$74.00)
            LedgerRow(
                txId = "demo-tx-002",
                recordType = RecordType.EXPENSE,
                date = currentMonthDate(10).toString(),
                createdAt = "${currentMonthDate(10)}T09:15:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "Monthly Electric & Gas Bill",
                categoryGroup = CategoryGroup.UTILITIES.name,
                category = CategoryCatalog.ELECTRICITY_GAS.name,
                amount = BigDecimal("148.00"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("74.00"),
                partnerBShare = BigDecimal("74.00"),
                notes = "Auto-pay statement",
            ),
            // 3. Fuel (Percentage 60:40, Alex paid $58.40 -> Sam owes +$23.36)
            LedgerRow(
                txId = "demo-tx-003",
                recordType = RecordType.EXPENSE,
                date = currentMonthDate(8).toString(),
                createdAt = "${currentMonthDate(8)}T16:45:00Z",
                createdBy = DEMO_PARTNER_A_EMAIL,
                description = "Highway Gas Station Refuel",
                categoryGroup = CategoryGroup.TRANSPORT.name,
                category = CategoryCatalog.FUEL.name,
                amount = BigDecimal("58.40"),
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.PERCENTAGE,
                partnerAShare = BigDecimal("35.04"),
                partnerBShare = BigDecimal("23.36"),
                splitMeta = "60:40",
                notes = "Weekend coastal drive",
            ),
            // 4. Streaming (Equal 50/50 odd cent, Sam paid $34.99 -> Alex owes -$17.49)
            LedgerRow(
                txId = "demo-tx-004",
                recordType = RecordType.EXPENSE,
                date = currentMonthDate(6).toString(),
                createdAt = "${currentMonthDate(6)}T11:00:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "Streaming & Music Bundle",
                categoryGroup = CategoryGroup.ENTERTAINMENT.name,
                category = CategoryCatalog.STREAMING_SUBSCRIPTIONS.name,
                amount = BigDecimal("34.99"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("17.49"),
                partnerBShare = BigDecimal("17.50"),
                notes = "Shared family plan",
            ),
            // 5. Household Supplies (Exact, Alex paid $86.20 -> Sam owes +$50.00)
            LedgerRow(
                txId = "demo-tx-005",
                recordType = RecordType.EXPENSE,
                date = currentMonthDate(4).toString(),
                createdAt = "${currentMonthDate(4)}T14:30:00Z",
                createdBy = DEMO_PARTNER_A_EMAIL,
                description = "Target Household Supplies",
                categoryGroup = CategoryGroup.HOUSING.name,
                category = CategoryCatalog.HOUSEHOLD_SUPPLIES.name,
                amount = BigDecimal("86.20"),
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.EXACT,
                partnerAShare = BigDecimal("36.20"),
                partnerBShare = BigDecimal("50.00"),
                notes = "Detergent, paper towels, and Sam's desk lamp",
            ),
            // 6. Settlement (Sam paid Alex $25.00 -> net delta -$25.00)
            LedgerRow(
                txId = "demo-tx-006",
                recordType = RecordType.SETTLEMENT,
                date = currentMonthDate(3).toString(),
                createdAt = "${currentMonthDate(3)}T20:05:00Z",
                createdBy = DEMO_PARTNER_B_EMAIL,
                description = "Settlement payment",
                categoryGroup = CategoryGroup.SETTLEMENT.name,
                category = CategoryCatalog.SETTLEMENT.name,
                amount = BigDecimal("25.00"),
                paidBy = PartnerRole.PARTNER_B,
                splitType = SplitType.SETTLEMENT,
                partnerAShare = BigDecimal("0.00"),
                partnerBShare = BigDecimal("0.00"),
                notes = "Partial reimbursement",
            ),
            // 7. Dining Out (Equal 50/50, Alex paid $64.00 -> Sam owes +$32.00)
            LedgerRow(
                txId = "demo-tx-007",
                recordType = RecordType.EXPENSE,
                date = currentMonthDate(1).toString(),
                createdAt = "${currentMonthDate(1)}T21:10:00Z",
                createdBy = DEMO_PARTNER_A_EMAIL,
                description = "Taco Night at La Palma",
                categoryGroup = CategoryGroup.FOOD.name,
                category = CategoryCatalog.DINING_OUT.name,
                amount = BigDecimal("64.00"),
                paidBy = PartnerRole.PARTNER_A,
                splitType = SplitType.EQUAL,
                partnerAShare = BigDecimal("32.00"),
                partnerBShare = BigDecimal("32.00"),
                notes = "Friday dinner with guacamole",
            ),
        )

        return DemoSpreadsheetRecord(
            id = DEFAULT_DEMO_SPREADSHEET_ID,
            modifiedTime = nowIso,
            configRows = SheetRowCodec.encodeConfig(config),
            ledgerRows = listOf(SheetRowCodec.LEDGER_HEADERS) +
                SheetRowCodec.encodeLedgerRows(seedRows, scale),
        )
    }

    companion object {
        const val DEFAULT_DEMO_SPREADSHEET_ID = "demo-household-ledger"
        const val DEFAULT_DEMO_LEDGER_NAME = "Our Household"
        const val DEMO_PARTNER_A_EMAIL = "alex@demo.local"
        const val DEMO_PARTNER_A_NAME = "Alex"
        const val DEMO_PARTNER_B_EMAIL = "sam@demo.local"
        const val DEMO_PARTNER_B_NAME = "Sam"
    }
}
