package vige.land.muchopesos.data

import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vige.land.muchopesos.data.local.InMemoryPreferencesStore
import vige.land.muchopesos.data.local.LocalLedgerCache
import vige.land.muchopesos.data.repository.LedgerRepository
import vige.land.muchopesos.data.sheet.DemoSheetGateway
import vige.land.muchopesos.domain.logic.SplitRequest
import vige.land.muchopesos.domain.model.CategoryCatalog
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.RecordType
import vige.land.muchopesos.domain.model.SplitType

class DemoSheetGatewayAndRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var demoFile: File
    private lateinit var cacheFile: File
    private lateinit var demoGateway: DemoSheetGateway
    private lateinit var preferences: InMemoryPreferencesStore
    private lateinit var localCache: LocalLedgerCache
    private lateinit var repository: LedgerRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("muchopesos-data-test").toFile()
        demoFile = File(tempDir, "demo_sheet.json")
        cacheFile = File(tempDir, "ledger_cache.json")
        demoGateway = DemoSheetGateway(
            storageFile = demoFile,
            todayProvider = { LocalDate.of(2026, 11, 24) },
        )
        preferences = InMemoryPreferencesStore()
        localCache = LocalLedgerCache(cacheFile)
        repository = LedgerRepository(
            preferences = preferences,
            localCache = localCache,
            googleGateway = demoGateway,
            demoGateway = demoGateway,
            todayProvider = { LocalDate.of(2026, 11, 24) },
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `demo sign-in loads pre-seeded realistic household ledger and expected balance`() = runTest {
        val result = repository.signInDemo()
        assertTrue(result.isSuccess)

        val state = repository.uiState.value
        assertTrue(state.isDemoMode)
        val config = state.config
        assertNotNull(config)
        assertEquals("Our Household", config!!.ledgerName)
        assertEquals("USD", config.homeCurrencyCode)
        assertEquals(19, state.activeTransactions.size)
        assertEquals(PartnerRole.PARTNER_A, state.currentUserRole)

        // Verify transactions span 3 distinct YearMonths (Nov, Oct, Sep 2026) and every expense
        // obeys partnerAShare + partnerBShare == amount.
        val distinctMonths = state.activeTransactions
            .map { YearMonth.from(it.date) }
            .distinct()
        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9)),
            distinctMonths,
        )
        state.activeTransactions
            .filter { it.recordType == RecordType.EXPENSE }
            .forEach { tx ->
                assertEquals(
                    "Shares must sum to amount for ${tx.txId}",
                    0,
                    tx.partnerAShare.add(tx.partnerBShare).compareTo(tx.amount),
                )
            }

        // Net balance across all 3 seeded months:
        // Sep (+32.10) + Oct (-6.64) + Nov (+51.12) = +76.58 (Sam owes Alex $76.58)
        assertFalse(state.balanceSummary.isSettledUp)
        assertEquals(PartnerRole.PARTNER_B, state.balanceSummary.debtorRole)
        assertEquals(PartnerRole.PARTNER_A, state.balanceSummary.creditorRole)
        assertEquals(0, BigDecimal("76.58").compareTo(state.balanceSummary.amount))
    }

    @Test
    fun `end-to-end create add-all-4-splits edit delete and settle-up preserves append-only log`() =
        runTest {
            repository.signInDemo().getOrThrow()

            // 1. Create a fresh demo ledger
            val createdConfig = repository.createLedger(
                ledgerName = "Test Flat",
                homeCurrencyCode = "USD",
                partnerAName = "Alex",
                partnerBEmail = "sam@demo.local",
                partnerBName = "Sam",
            ).getOrThrow()
            assertEquals("Test Flat", createdConfig.ledgerName)
            assertTrue(repository.uiState.value.activeTransactions.isEmpty())
            assertTrue(repository.uiState.value.balanceSummary.isSettledUp)

            // 2. Add expenses across all 4 split modes
            // (a) Equal: Alex pays $100.01 -> A: 50.01, B: 50.00 -> netForA = +50.00
            val eqTx = repository.addExpense(
                description = "Groceries",
                category = CategoryCatalog.GROCERIES,
                amount = BigDecimal("100.01"),
                date = LocalDate.of(2026, 11, 18),
                paidBy = PartnerRole.PARTNER_A,
                splitRequest = SplitRequest.Equal,
            ).getOrThrow()
            assertEquals(SplitType.EQUAL, eqTx.splitType)
            assertEquals(0, BigDecimal("50.01").compareTo(eqTx.partnerAShare))
            assertEquals(0, BigDecimal("50.00").compareTo(eqTx.partnerBShare))

            // (b) Exact: Sam pays $80.00 (A: 30.00, B: 50.00) -> netForA = +50.00 - 30.00 = +20.00
            val exactTx = repository.addExpense(
                description = "Household Supplies",
                category = CategoryCatalog.HOUSEHOLD_SUPPLIES,
                amount = BigDecimal("80.00"),
                date = LocalDate.of(2026, 11, 19),
                paidBy = PartnerRole.PARTNER_B,
                splitRequest = SplitRequest.Exact(
                    partnerAShare = BigDecimal("30.00"),
                    partnerBShare = BigDecimal("50.00"),
                ),
            ).getOrThrow()
            assertEquals(SplitType.EXACT, exactTx.splitType)

            // (c) Percentage 60:40: Alex pays $50.00 (A: 30.00, B: 20.00) -> netForA = +20.00 + 20.00 = +40.00
            val pctTx = repository.addExpense(
                description = "Fuel",
                category = CategoryCatalog.FUEL,
                amount = BigDecimal("50.00"),
                date = LocalDate.of(2026, 11, 20),
                paidBy = PartnerRole.PARTNER_A,
                splitRequest = SplitRequest.Percentage(
                    partnerAPercent = BigDecimal("60"),
                    partnerBPercent = BigDecimal("40"),
                ),
            ).getOrThrow()
            assertEquals(SplitType.PERCENTAGE, pctTx.splitType)
            assertEquals("60:40", pctTx.splitMeta)

            // (d) Full (100%): Alex pays $25.00 owed 100% by Sam -> netForA = +40.00 + 25.00 = +65.00
            val fullTx = repository.addExpense(
                description = "Sam's Pharmacy Pickup",
                category = CategoryCatalog.HEALTHCARE_PHARMACY,
                amount = BigDecimal("25.00"),
                date = LocalDate.of(2026, 11, 21),
                paidBy = PartnerRole.PARTNER_A,
                splitRequest = SplitRequest.Full(owedBy = PartnerRole.PARTNER_B),
            ).getOrThrow()
            assertEquals(SplitType.FULL, fullTx.splitType)
            assertEquals(0, BigDecimal("65.00").compareTo(repository.uiState.value.balanceSummary.netForPartnerA))

            // 3. Edit `pctTx` from $50.00 (60:40 -> B owes 20) to $100.00 (50:50 -> B owes 50)
            // Expected new netForA = +65.00 - 20.00 + 50.00 = +95.00
            val editedTx = repository.editExpense(
                originalTxId = pctTx.txId,
                description = "Fuel (Corrected)",
                category = CategoryCatalog.FUEL,
                amount = BigDecimal("100.00"),
                date = LocalDate.of(2026, 11, 20),
                paidBy = PartnerRole.PARTNER_A,
                splitRequest = SplitRequest.Equal,
                notes = "Updated receipt",
            ).getOrThrow()

            assertEquals("Fuel (Corrected)", editedTx.description)
            assertEquals(4, repository.uiState.value.activeTransactions.size)
            assertTrue(repository.uiState.value.activeTransactions.none { it.txId == pctTx.txId })
            assertEquals(0, BigDecimal("95.00").compareTo(repository.uiState.value.balanceSummary.netForPartnerA))

            // Verify raw append-only sheet log: 1 header + 4 original + 1 VOID + 1 replacement = 7 rows
            val rawAfterEdit = demoGateway.readRawLedgerMatrix(createdConfig.spreadsheetId)
            assertEquals(7, rawAfterEdit.size)
            assertTrue("Original row must remain untouched in append-only log", rawAfterEdit.any { it[0] == pctTx.txId && it[1] == RecordType.EXPENSE.name })
            assertTrue("VOID row referencing original must exist", rawAfterEdit.any { it[1] == RecordType.VOID.name && it[2] == pctTx.txId })
            assertTrue("Replacement row referencing original must exist", rawAfterEdit.any { it[0] == editedTx.txId && it[1] == RecordType.EXPENSE.name && it[2] == pctTx.txId })

            // 4. Delete `fullTx` ($25.00 owed by Sam) -> netForA = +95.00 - 25.00 = +70.00
            repository.deleteExpense(fullTx.txId).getOrThrow()
            assertEquals(3, repository.uiState.value.activeTransactions.size)
            assertTrue(repository.uiState.value.activeTransactions.none { it.txId == fullTx.txId })
            assertEquals(0, BigDecimal("70.00").compareTo(repository.uiState.value.balanceSummary.netForPartnerA))

            // Raw log now has 1 header + 7 data rows = 8 rows (original fullTx still intact + VOID appended)
            val rawAfterDelete = demoGateway.readRawLedgerMatrix(createdConfig.spreadsheetId)
            assertEquals(8, rawAfterDelete.size)
            assertTrue(rawAfterDelete.any { it[0] == fullTx.txId && it[1] == RecordType.EXPENSE.name })
            assertTrue(rawAfterDelete.any { it[1] == RecordType.VOID.name && it[2] == fullTx.txId })

            // 5. Settle up: Sam (PARTNER_B) pays Alex $70.00 -> settled up ($0.00)!
            val settlementTx = repository.recordSettlement(
                paidBy = PartnerRole.PARTNER_B,
                amount = BigDecimal("70.00"),
                date = LocalDate.of(2026, 11, 24),
                notes = "Paid via Zelle",
            ).getOrThrow()
            assertEquals(RecordType.SETTLEMENT, settlementTx.recordType)

            val settledSummary = repository.uiState.value.balanceSummary
            assertTrue(settledSummary.isSettledUp)
            assertEquals(0, BigDecimal.ZERO.compareTo(settledSummary.amount))
            assertEquals(0, BigDecimal.ZERO.compareTo(settledSummary.netForPartnerA))

            // 6. Update partner display names and verify persistence + listSpreadsheets
            val renamed = repository.updatePartnerNames("Alexander", "Samantha").getOrThrow()
            assertEquals("Alexander", renamed.partnerAName)
            assertEquals("Samantha", renamed.partnerBName)

            val listed = repository.listSpreadsheets("Test Flat").getOrThrow()
            assertEquals(1, listed.size)
            assertEquals(createdConfig.spreadsheetId, listed.first().id)

            // 7. Re-initializing a fresh repository instance restores session & cached ledger cleanly
            val restartedRepo = LedgerRepository(
                preferences = preferences,
                localCache = localCache,
                googleGateway = demoGateway,
                demoGateway = demoGateway,
            )
            restartedRepo.initialize()
            assertEquals(createdConfig.spreadsheetId, restartedRepo.uiState.value.config?.spreadsheetId)
            assertEquals("Alexander", restartedRepo.uiState.value.config?.partnerAName)
            assertTrue(restartedRepo.uiState.value.balanceSummary.isSettledUp)
        }
}
