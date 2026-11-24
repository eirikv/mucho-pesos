package vige.land.muchopesos.data.repository

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vige.land.muchopesos.data.local.LocalLedgerCache
import vige.land.muchopesos.data.local.PreferencesStorage
import vige.land.muchopesos.data.sheet.DemoSheetGateway
import vige.land.muchopesos.data.sheet.DriveSpreadsheetItem
import vige.land.muchopesos.data.sheet.LedgerSheetGateway
import vige.land.muchopesos.domain.logic.BalanceCalculator
import vige.land.muchopesos.domain.logic.LedgerReducer
import vige.land.muchopesos.domain.logic.SplitCalculator
import vige.land.muchopesos.domain.logic.SplitRequest
import vige.land.muchopesos.domain.logic.SpreadsheetIdParser
import vige.land.muchopesos.domain.model.ActiveTransaction
import vige.land.muchopesos.domain.model.CategoryCatalog
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.ExpenseCategory
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.LedgerRow
import vige.land.muchopesos.domain.model.NetBalanceSummary
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.RecordType
import vige.land.muchopesos.domain.model.SplitType
import vige.land.muchopesos.domain.model.UserSession

data class LedgerUiSnapshot(
    val config: LedgerConfig? = null,
    val rawRowsCount: Int = 0,
    val activeTransactions: List<ActiveTransaction> = emptyList(),
    val balanceSummary: NetBalanceSummary = NetBalanceSummary(
        netForPartnerA = BigDecimal("0.00"),
        debtorRole = null,
        creditorRole = null,
        amount = BigDecimal("0.00"),
        isSettledUp = true,
    ),
    val currentUserRole: PartnerRole = PartnerRole.PARTNER_A,
    val isSyncing: Boolean = false,
    val lastSyncEpochMs: Long? = null,
    val errorMessage: String? = null,
    val isDemoMode: Boolean = false,
    val isInitialized: Boolean = false,
)

class LedgerRepository(
    private val preferences: PreferencesStorage,
    private val localCache: LocalLedgerCache,
    private val googleGateway: LedgerSheetGateway,
    private val demoGateway: DemoSheetGateway,
    private val onGoogleSignOut: suspend () -> Unit = {},
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val nowIsoProvider: () -> String = { Instant.now().toString() },
) {
    private val operationMutex = Mutex()

    private val _userSession = MutableStateFlow<UserSession?>(null)
    val userSession: StateFlow<UserSession?> = _userSession.asStateFlow()

    private val _uiState = MutableStateFlow(LedgerUiSnapshot())
    val uiState: StateFlow<LedgerUiSnapshot> = _uiState.asStateFlow()

    private var cachedRawRows: List<LedgerRow> = emptyList()

    suspend fun initialize() {
        withContext(NonCancellable) {
            val session = preferences.getUserSession()
            val activeSpreadsheetId = preferences.getActiveSpreadsheetId()
            val lastSync = preferences.getLastSyncEpochMs()

            if (session == null) {
                _uiState.value = LedgerUiSnapshot(isInitialized = true)
                _userSession.value = null
                return@withContext
            }

            if (activeSpreadsheetId != null) {
                val cached = localCache.load(expectedSpreadsheetId = activeSpreadsheetId)
                if (cached != null) {
                    applyLedgerData(
                        config = cached.config,
                        rows = cached.rows,
                        session = session,
                        lastSyncEpochMs = lastSync ?: cached.savedAtEpochMs,
                        isSyncing = false,
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            isDemoMode = session.isDemoMode,
                            lastSyncEpochMs = lastSync,
                            isInitialized = true,
                        )
                    }
                }
                _userSession.value = session
                // Refresh silently in place; if cache already hydrated the screen, keep cached data on failure.
                refreshLedger(force = true)
            } else {
                _uiState.value = LedgerUiSnapshot(
                    isDemoMode = session.isDemoMode,
                    isInitialized = true,
                )
                _userSession.value = session
            }
        }
    }

    suspend fun signInDemo(): Result<LedgerConfig> = withContext(NonCancellable) {
        operationMutex.withLock {
            val demoSession = UserSession(
                email = DemoSheetGateway.DEMO_PARTNER_A_EMAIL,
                displayName = DemoSheetGateway.DEMO_PARTNER_A_NAME,
                photoUrl = null,
                isDemoMode = true,
            )

            runCatching {
                val spreadsheetId = DemoSheetGateway.DEFAULT_DEMO_SPREADSHEET_ID
                val (config, rows) = demoGateway.loadLedger(spreadsheetId)
                val now = clockMs()
                preferences.saveUserSession(demoSession)
                preferences.saveActiveSpreadsheetId(config.spreadsheetId)
                preferences.saveLastSyncEpochMs(now)
                localCache.save(config, rows, now)
                // Hydrate ledger UI state BEFORE publishing userSession so AppNavHost routes
                // directly to Dashboard without unmounting SignInScreen mid-load.
                applyLedgerData(
                    config = config,
                    rows = rows,
                    session = demoSession,
                    lastSyncEpochMs = now,
                    isSyncing = false,
                )
                _userSession.value = demoSession
                config
            }.onFailure { error ->
                setError(error.message ?: "Failed to start Demo Mode.")
            }
        }
    }

    suspend fun onGoogleSignedIn(session: UserSession) {
        withContext(NonCancellable) {
            val googleSession = session.copy(isDemoMode = false)
            preferences.saveUserSession(googleSession)

            val existingSpreadsheetId = preferences.getActiveSpreadsheetId()
                ?.takeUnless { it.startsWith("demo-") }

            if (existingSpreadsheetId != null) {
                val cached = localCache.load(expectedSpreadsheetId = existingSpreadsheetId)
                if (cached != null) {
                    applyLedgerData(
                        config = cached.config,
                        rows = cached.rows,
                        session = googleSession,
                        lastSyncEpochMs = preferences.getLastSyncEpochMs() ?: cached.savedAtEpochMs,
                        isSyncing = false,
                    )
                } else {
                    _uiState.value = LedgerUiSnapshot(
                        isDemoMode = false,
                        isInitialized = true,
                    )
                }
                _userSession.value = googleSession
                refreshLedger(force = true)
            } else {
                preferences.clearLedgerLink()
                localCache.clear()
                cachedRawRows = emptyList()
                _uiState.value = LedgerUiSnapshot(
                    isDemoMode = false,
                    isInitialized = true,
                )
                _userSession.value = googleSession
            }
        }
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            operationMutex.withLock {
                val wasDemo = _userSession.value?.isDemoMode == true
                if (!wasDemo) {
                    runCatching { onGoogleSignOut() }
                }
                preferences.clearAll()
                localCache.clear()
                cachedRawRows = emptyList()
                _uiState.value = LedgerUiSnapshot(isInitialized = true)
                _userSession.value = null
            }
        }
    }

    suspend fun createLedger(
        ledgerName: String,
        homeCurrencyCode: String,
        partnerAName: String,
        partnerBEmail: String,
        partnerBName: String,
    ): Result<LedgerConfig> = withContext(NonCancellable) {
        operationMutex.withLock {
            val session = _userSession.value
                ?: return@withLock Result.failure(IllegalStateException("You must sign in before creating a ledger."))

            if (ledgerName.isBlank()) {
                return@withLock Result.failure(IllegalArgumentException("Please enter a ledger name."))
            }
            if (partnerAName.isBlank()) {
                return@withLock Result.failure(IllegalArgumentException("Please enter your display name."))
            }
            if (partnerBEmail.isBlank()) {
                return@withLock Result.failure(IllegalArgumentException("Please enter your partner's email address."))
            }
            if (partnerBName.isBlank()) {
                return@withLock Result.failure(IllegalArgumentException("Please enter your partner's display name."))
            }

            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

            runCatching {
                val config = activeGateway(session).createLedger(
                    ledgerName = ledgerName.trim(),
                    homeCurrencyCode = homeCurrencyCode.trim().uppercase(),
                    partnerAEmail = session.email,
                    partnerAName = partnerAName.trim(),
                    partnerBEmail = partnerBEmail.trim(),
                    partnerBName = partnerBName.trim(),
                )
                val now = clockMs()
                preferences.saveActiveSpreadsheetId(config.spreadsheetId)
                preferences.saveLastSyncEpochMs(now)
                localCache.save(config, emptyList(), now)
                applyLedgerData(
                    config = config,
                    rows = emptyList(),
                    session = session,
                    lastSyncEpochMs = now,
                    isSyncing = false,
                )
                config
            }.onFailure { error ->
                setError(error.message ?: "Failed to create shared ledger.")
            }
        }
    }

    suspend fun linkLedger(urlOrId: String): Result<LedgerConfig> = withContext(NonCancellable) {
        operationMutex.withLock {
            val session = _userSession.value
                ?: return@withLock Result.failure(IllegalStateException("You must sign in before linking a ledger."))

            val spreadsheetId = SpreadsheetIdParser.extractSpreadsheetId(urlOrId)
                ?: return@withLock Result.failure(
                    IllegalArgumentException("Invalid Google Sheets URL or Spreadsheet ID."),
                )

            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

            runCatching {
                val (config, rows) = activeGateway(session).loadLedger(spreadsheetId)
                val now = clockMs()
                preferences.saveActiveSpreadsheetId(config.spreadsheetId)
                preferences.saveLastSyncEpochMs(now)
                localCache.save(config, rows, now)
                applyLedgerData(
                    config = config,
                    rows = rows,
                    session = session,
                    lastSyncEpochMs = now,
                    isSyncing = false,
                )
                config
            }.onFailure { error ->
                setError(error.message ?: "Failed to link spreadsheet. Make sure it has been shared with your account.")
            }
        }
    }

    suspend fun listSpreadsheets(query: String? = null): Result<List<DriveSpreadsheetItem>> {
        val session = _userSession.value
            ?: return Result.failure(IllegalStateException("You must sign in to browse spreadsheets."))
        return runCatching {
            activeGateway(session).listAvailableSpreadsheets(query)
        }
    }

    suspend fun refreshLedger(force: Boolean = true): Result<Unit> = operationMutex.withLock {
        val session = _userSession.value ?: return@withLock Result.success(Unit)
        val spreadsheetId = _uiState.value.config?.spreadsheetId
            ?: preferences.getActiveSpreadsheetId()
            ?: return@withLock Result.success(Unit)

        if (!force) {
            val lastSync = _uiState.value.lastSyncEpochMs
            if (lastSync != null && clockMs() - lastSync < AUTO_REFRESH_THROTTLE_MS) {
                return@withLock Result.success(Unit)
            }
        }

        return@withLock reloadFromGatewayLocked(session, spreadsheetId)
    }

    suspend fun addExpense(
        description: String,
        category: ExpenseCategory,
        amount: BigDecimal,
        date: LocalDate,
        paidBy: PartnerRole,
        splitRequest: SplitRequest,
        notes: String = "",
    ): Result<ActiveTransaction> = operationMutex.withLock {
        val session = _userSession.value
            ?: return@withLock Result.failure(IllegalStateException("Not signed in."))
        val config = _uiState.value.config
            ?: return@withLock Result.failure(IllegalStateException("No active ledger linked."))

        val cleanDescription = description.trim()
        if (cleanDescription.isEmpty()) {
            return@withLock Result.failure(IllegalArgumentException("Description is required."))
        }

        val scale = config.currencySpec.fractionDigits
        val splitResult = SplitCalculator.calculate(
            totalAmount = amount,
            paidBy = paidBy,
            request = splitRequest,
            scale = scale,
        ).getOrElse { return@withLock Result.failure(it) }

        val normalizedAmount = amount.setScale(scale, RoundingMode.HALF_UP)
        val newTxId = UUID.randomUUID().toString()
        val row = LedgerRow(
            txId = newTxId,
            recordType = RecordType.EXPENSE,
            refTxId = "",
            date = date.toString(),
            createdAt = nowIsoProvider(),
            createdBy = session.email,
            description = cleanDescription,
            categoryGroup = category.group.name,
            category = category.name,
            amount = normalizedAmount,
            paidBy = paidBy,
            splitType = splitResult.splitType,
            partnerAShare = splitResult.partnerAShare,
            partnerBShare = splitResult.partnerBShare,
            splitMeta = splitResult.splitMeta,
            notes = notes.trim(),
        )

        _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

        return@withLock runCatching {
            activeGateway(session).appendRows(config.spreadsheetId, listOf(row), scale)
            reloadFromGatewayLocked(session, config.spreadsheetId).getOrThrow()
            _uiState.value.activeTransactions.first { it.txId == newTxId }
        }.onFailure { error ->
            setError(error.message ?: "Failed to save expense.")
        }
    }

    suspend fun editExpense(
        originalTxId: String,
        description: String,
        category: ExpenseCategory,
        amount: BigDecimal,
        date: LocalDate,
        paidBy: PartnerRole,
        splitRequest: SplitRequest,
        notes: String = "",
    ): Result<ActiveTransaction> = operationMutex.withLock {
        val session = _userSession.value
            ?: return@withLock Result.failure(IllegalStateException("Not signed in."))
        val config = _uiState.value.config
            ?: return@withLock Result.failure(IllegalStateException("No active ledger linked."))

        val cleanOriginalId = originalTxId.trim()
        if (cleanOriginalId.isEmpty()) {
            return@withLock Result.failure(IllegalArgumentException("Original transaction ID is missing."))
        }
        val cleanDescription = description.trim()
        if (cleanDescription.isEmpty()) {
            return@withLock Result.failure(IllegalArgumentException("Description is required."))
        }

        val scale = config.currencySpec.fractionDigits
        val splitResult = SplitCalculator.calculate(
            totalAmount = amount,
            paidBy = paidBy,
            request = splitRequest,
            scale = scale,
        ).getOrElse { return@withLock Result.failure(it) }

        val zero = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        val normalizedAmount = amount.setScale(scale, RoundingMode.HALF_UP)
        val nowIso = nowIsoProvider()
        val replacementTxId = UUID.randomUUID().toString()

        val voidRow = LedgerRow(
            txId = UUID.randomUUID().toString(),
            recordType = RecordType.VOID,
            refTxId = cleanOriginalId,
            date = todayProvider().toString(),
            createdAt = nowIso,
            createdBy = session.email,
            description = "Voided for edit: $cleanDescription",
            categoryGroup = "",
            category = "",
            amount = zero,
            paidBy = paidBy,
            splitType = SplitType.EQUAL,
            partnerAShare = zero,
            partnerBShare = zero,
            splitMeta = "",
            notes = "",
        )

        val replacementRow = LedgerRow(
            txId = replacementTxId,
            recordType = RecordType.EXPENSE,
            refTxId = cleanOriginalId,
            date = date.toString(),
            createdAt = nowIso,
            createdBy = session.email,
            description = cleanDescription,
            categoryGroup = category.group.name,
            category = category.name,
            amount = normalizedAmount,
            paidBy = paidBy,
            splitType = splitResult.splitType,
            partnerAShare = splitResult.partnerAShare,
            partnerBShare = splitResult.partnerBShare,
            splitMeta = splitResult.splitMeta,
            notes = notes.trim(),
        )

        _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

        return@withLock runCatching {
            // Single atomic values:append call containing both [VOID, replacement EXPENSE].
            activeGateway(session).appendRows(
                spreadsheetId = config.spreadsheetId,
                rows = listOf(voidRow, replacementRow),
                scale = scale,
            )
            reloadFromGatewayLocked(session, config.spreadsheetId).getOrThrow()
            _uiState.value.activeTransactions.first { it.txId == replacementTxId }
        }.onFailure { error ->
            setError(error.message ?: "Failed to update expense.")
        }
    }

    suspend fun deleteExpense(txId: String): Result<Unit> = operationMutex.withLock {
        val session = _userSession.value
            ?: return@withLock Result.failure(IllegalStateException("Not signed in."))
        val config = _uiState.value.config
            ?: return@withLock Result.failure(IllegalStateException("No active ledger linked."))

        val cleanTxId = txId.trim()
        if (cleanTxId.isEmpty()) {
            return@withLock Result.failure(IllegalArgumentException("Transaction ID is missing."))
        }

        val scale = config.currencySpec.fractionDigits
        val zero = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        val voidRow = LedgerRow(
            txId = UUID.randomUUID().toString(),
            recordType = RecordType.VOID,
            refTxId = cleanTxId,
            date = todayProvider().toString(),
            createdAt = nowIsoProvider(),
            createdBy = session.email,
            description = "Voided transaction",
            categoryGroup = "",
            category = "",
            amount = zero,
            paidBy = config.roleForEmail(session.email),
            splitType = SplitType.EQUAL,
            partnerAShare = zero,
            partnerBShare = zero,
            splitMeta = "",
            notes = "",
        )

        _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

        return@withLock runCatching {
            activeGateway(session).appendRows(
                spreadsheetId = config.spreadsheetId,
                rows = listOf(voidRow),
                scale = scale,
            )
            reloadFromGatewayLocked(session, config.spreadsheetId).getOrThrow()
        }.onFailure { error ->
            setError(error.message ?: "Failed to void transaction.")
        }
    }

    suspend fun recordSettlement(
        paidBy: PartnerRole,
        amount: BigDecimal,
        date: LocalDate = todayProvider(),
        notes: String = "",
    ): Result<ActiveTransaction> = operationMutex.withLock {
        val session = _userSession.value
            ?: return@withLock Result.failure(IllegalStateException("Not signed in."))
        val config = _uiState.value.config
            ?: return@withLock Result.failure(IllegalStateException("No active ledger linked."))

        val scale = config.currencySpec.fractionDigits
        val normalizedAmount = amount.setScale(scale, RoundingMode.HALF_UP)
        if (normalizedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return@withLock Result.failure(
                IllegalArgumentException("Settlement amount must be greater than zero."),
            )
        }

        val zero = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
        val settlementTxId = UUID.randomUUID().toString()
        val settlementRow = LedgerRow(
            txId = settlementTxId,
            recordType = RecordType.SETTLEMENT,
            refTxId = "",
            date = date.toString(),
            createdAt = nowIsoProvider(),
            createdBy = session.email,
            description = "Settlement payment",
            categoryGroup = CategoryGroup.SETTLEMENT.name,
            category = CategoryCatalog.SETTLEMENT.name,
            amount = normalizedAmount,
            paidBy = paidBy,
            splitType = SplitType.SETTLEMENT,
            partnerAShare = zero,
            partnerBShare = zero,
            splitMeta = "",
            notes = notes.trim(),
        )

        _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

        return@withLock runCatching {
            activeGateway(session).appendRows(
                spreadsheetId = config.spreadsheetId,
                rows = listOf(settlementRow),
                scale = scale,
            )
            reloadFromGatewayLocked(session, config.spreadsheetId).getOrThrow()
            _uiState.value.activeTransactions.first { it.txId == settlementTxId }
        }.onFailure { error ->
            setError(error.message ?: "Failed to record settlement.")
        }
    }

    suspend fun updatePartnerNames(
        partnerAName: String,
        partnerBName: String,
    ): Result<LedgerConfig> = operationMutex.withLock {
        val session = _userSession.value
            ?: return@withLock Result.failure(IllegalStateException("Not signed in."))
        val currentConfig = _uiState.value.config
            ?: return@withLock Result.failure(IllegalStateException("No active ledger linked."))

        val cleanA = partnerAName.trim()
        val cleanB = partnerBName.trim()
        if (cleanA.isEmpty() || cleanB.isEmpty()) {
            return@withLock Result.failure(IllegalArgumentException("Both partner names are required."))
        }

        val updatedConfig = currentConfig.copy(
            partnerAName = cleanA,
            partnerBName = cleanB,
        )

        _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

        return@withLock runCatching {
            activeGateway(session).updateConfig(updatedConfig)
            val now = clockMs()
            preferences.saveLastSyncEpochMs(now)
            localCache.save(updatedConfig, cachedRawRows, now)
            applyLedgerData(
                config = updatedConfig,
                rows = cachedRawRows,
                session = session,
                lastSyncEpochMs = now,
                isSyncing = false,
            )
            updatedConfig
        }.onFailure { error ->
            setError(error.message ?: "Failed to update partner names.")
        }
    }

    suspend fun unlinkLedger() {
        withContext(NonCancellable) {
            operationMutex.withLock {
                val isDemo = _userSession.value?.isDemoMode == true
                preferences.clearLedgerLink()
                localCache.clear()
                cachedRawRows = emptyList()
                _uiState.value = LedgerUiSnapshot(
                    isDemoMode = isDemo,
                    isInitialized = true,
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun findActiveTransaction(txId: String): ActiveTransaction? =
        _uiState.value.activeTransactions.firstOrNull { it.txId == txId }

    private suspend fun reloadFromGatewayLocked(
        session: UserSession,
        spreadsheetId: String,
    ): Result<Unit> {
        _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
        return runCatching {
            val (config, rows) = activeGateway(session).loadLedger(spreadsheetId)
            val now = clockMs()
            preferences.saveActiveSpreadsheetId(config.spreadsheetId)
            preferences.saveLastSyncEpochMs(now)
            localCache.save(config, rows, now)
            applyLedgerData(
                config = config,
                rows = rows,
                session = session,
                lastSyncEpochMs = now,
                isSyncing = false,
            )
        }.onFailure { error ->
            setError(error.message ?: "Unable to sync ledger from Google Sheets.")
        }
    }

    private fun applyLedgerData(
        config: LedgerConfig,
        rows: List<LedgerRow>,
        session: UserSession,
        lastSyncEpochMs: Long?,
        isSyncing: Boolean,
    ) {
        cachedRawRows = rows
        val activeTransactions = LedgerReducer.reduce(rows)
        val balanceSummary = BalanceCalculator.computeNetBalance(
            transactions = activeTransactions,
            scale = config.currencySpec.fractionDigits,
        )
        val role = config.roleForEmail(session.email)

        _uiState.value = LedgerUiSnapshot(
            config = config,
            rawRowsCount = rows.size,
            activeTransactions = activeTransactions,
            balanceSummary = balanceSummary,
            currentUserRole = role,
            isSyncing = isSyncing,
            lastSyncEpochMs = lastSyncEpochMs,
            errorMessage = null,
            isDemoMode = session.isDemoMode,
            isInitialized = true,
        )
    }

    private fun setError(message: String) {
        _uiState.update { it.copy(isSyncing = false, errorMessage = message) }
    }

    private fun activeGateway(session: UserSession): LedgerSheetGateway =
        if (session.isDemoMode) demoGateway else googleGateway

    companion object {
        private const val AUTO_REFRESH_THROTTLE_MS = 25_000L
    }
}
