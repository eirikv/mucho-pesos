package vige.land.muchopesos.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vige.land.muchopesos.data.repository.LedgerRepository
import vige.land.muchopesos.data.repository.LedgerUiSnapshot
import vige.land.muchopesos.domain.logic.MonthlyStatisticsCalculator
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.MonthOverMonthDelta
import vige.land.muchopesos.domain.model.MonthlySummary
import vige.land.muchopesos.domain.model.MonthlyTrendPoint
import vige.land.muchopesos.domain.model.PartnerRole

enum class CategoryBreakdownMode {
    CATEGORIES,
    GROUPS,
}

data class StatisticsUiState(
    val config: LedgerConfig? = null,
    val currentUserRole: PartnerRole = PartnerRole.PARTNER_A,
    val isSyncing: Boolean = false,
    val isDemoMode: Boolean = false,
    val lastSyncEpochMs: Long? = null,
    val errorMessage: String? = null,
    val selectedMonth: YearMonth = YearMonth.now(),
    val latestAvailableMonth: YearMonth = YearMonth.now(),
    val maxSelectableMonth: YearMonth = YearMonth.now(),
    val selectedSummary: MonthlySummary = MonthlyStatisticsCalculator.emptySummary(YearMonth.now()),
    val trendPoints: List<MonthlyTrendPoint> = emptyList(),
    val monthOverMonthDelta: MonthOverMonthDelta? = null,
    val breakdownMode: CategoryBreakdownMode = CategoryBreakdownMode.CATEGORIES,
) {
    val canGoNextMonth: Boolean
        get() = selectedMonth.isBefore(maxSelectableMonth)

    val isViewingLatestMonth: Boolean
        get() = selectedMonth == latestAvailableMonth
}

class StatisticsViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    private val manualSelectedMonth = MutableStateFlow<YearMonth?>(null)
    private val breakdownMode = MutableStateFlow(CategoryBreakdownMode.CATEGORIES)

    val uiState: StateFlow<StatisticsUiState> = combine(
        repository.uiState,
        manualSelectedMonth,
        breakdownMode,
    ) { snapshot, manualMonth, mode ->
        buildState(
            snapshot = snapshot,
            manualMonth = manualMonth,
            mode = mode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildState(
            snapshot = repository.uiState.value,
            manualMonth = null,
            mode = CategoryBreakdownMode.CATEGORIES,
        ),
    )

    fun selectMonth(month: YearMonth) {
        val maxMonth = uiState.value.maxSelectableMonth
        manualSelectedMonth.value = if (month.isAfter(maxMonth)) maxMonth else month
    }

    fun previousMonth() {
        manualSelectedMonth.value = uiState.value.selectedMonth.minusMonths(1)
    }

    fun nextMonth() {
        val current = uiState.value
        if (current.canGoNextMonth) {
            manualSelectedMonth.value = current.selectedMonth.plusMonths(1)
        }
    }

    fun jumpToLatestMonth() {
        manualSelectedMonth.value = null
    }

    fun selectBreakdownMode(mode: CategoryBreakdownMode) {
        breakdownMode.value = mode
    }

    fun refresh(force: Boolean) {
        viewModelScope.launch {
            repository.refreshLedger(force = force)
        }
    }

    fun clearError() {
        repository.clearError()
    }

    private fun buildState(
        snapshot: LedgerUiSnapshot,
        manualMonth: YearMonth?,
        mode: CategoryBreakdownMode,
    ): StatisticsUiState {
        val config = snapshot.config
        val scale = config?.currencySpec?.fractionDigits ?: 2
        val currentMonth = YearMonth.now()

        val report = MonthlyStatisticsCalculator.compute(
            transactions = snapshot.activeTransactions,
            scale = scale,
        )

        val latestDataMonth = report.months.firstOrNull { it.expenseCount > 0 }?.yearMonth
            ?: report.months.firstOrNull()?.yearMonth
            ?: currentMonth
        val maxSelectableMonth = maxOf(latestDataMonth, currentMonth)

        val selectedMonth = when {
            manualMonth == null -> latestDataMonth
            manualMonth.isAfter(maxSelectableMonth) -> maxSelectableMonth
            else -> manualMonth
        }

        val selectedSummary = report.summaryFor(selectedMonth)
            ?: MonthlyStatisticsCalculator.emptySummary(selectedMonth, scale)

        // Keep the 6-month trend window anchored at maxSelectableMonth while tapping within the
        // latest 6 months, and only slide it backward if the user steps further into the past.
        val minStableMonth = maxSelectableMonth.minusMonths((TREND_WINDOW_SIZE - 1).toLong())
        val anchorMonth = if (selectedMonth.isBefore(minStableMonth)) {
            val candidate = selectedMonth.plusMonths((TREND_WINDOW_SIZE - 1).toLong())
            if (candidate.isAfter(maxSelectableMonth)) maxSelectableMonth else candidate
        } else {
            maxSelectableMonth
        }

        val trendPoints = MonthlyStatisticsCalculator.buildTrendWindow(
            report = report,
            anchorMonth = anchorMonth,
            windowSize = TREND_WINDOW_SIZE,
        )

        val momDelta = MonthlyStatisticsCalculator.computeMonthOverMonthDelta(
            report = report,
            month = selectedMonth,
        )

        return StatisticsUiState(
            config = config,
            currentUserRole = snapshot.currentUserRole,
            isSyncing = snapshot.isSyncing,
            isDemoMode = snapshot.isDemoMode,
            lastSyncEpochMs = snapshot.lastSyncEpochMs,
            errorMessage = snapshot.errorMessage,
            selectedMonth = selectedMonth,
            latestAvailableMonth = latestDataMonth,
            maxSelectableMonth = maxSelectableMonth,
            selectedSummary = selectedSummary,
            trendPoints = trendPoints,
            monthOverMonthDelta = momDelta,
            breakdownMode = mode,
        )
    }

    companion object {
        private const val TREND_WINDOW_SIZE = 6
    }
}
