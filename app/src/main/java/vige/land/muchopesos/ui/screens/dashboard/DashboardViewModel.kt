package vige.land.muchopesos.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vige.land.muchopesos.data.repository.LedgerRepository
import vige.land.muchopesos.data.repository.LedgerUiSnapshot
import vige.land.muchopesos.domain.model.ActiveTransaction
import vige.land.muchopesos.domain.model.CategoryGroup
import vige.land.muchopesos.domain.model.PartnerRole

data class DashboardControlsState(
    val selectedCategoryGroup: CategoryGroup? = null,
    val selectedTransaction: ActiveTransaction? = null,
    val showSettleUpSheet: Boolean = false,
    val isMutating: Boolean = false,
    val snackbarMessage: String? = null,
)

class DashboardViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    val ledgerState: StateFlow<LedgerUiSnapshot> = repository.uiState

    private val _controls = MutableStateFlow(DashboardControlsState())
    val controls: StateFlow<DashboardControlsState> = _controls.asStateFlow()

    fun refresh(force: Boolean) {
        viewModelScope.launch {
            repository.refreshLedger(force = force)
        }
    }

    fun selectCategoryGroup(group: CategoryGroup?) {
        _controls.update { it.copy(selectedCategoryGroup = group) }
    }

    fun openTransactionDetail(transaction: ActiveTransaction) {
        _controls.update { it.copy(selectedTransaction = transaction) }
    }

    fun dismissTransactionDetail() {
        _controls.update { it.copy(selectedTransaction = null) }
    }

    fun openSettleUpSheet() {
        _controls.update { it.copy(showSettleUpSheet = true) }
    }

    fun dismissSettleUpSheet() {
        _controls.update { it.copy(showSettleUpSheet = false) }
    }

    fun deleteTransaction(transaction: ActiveTransaction) {
        viewModelScope.launch {
            _controls.update { it.copy(isMutating = true) }
            val result = repository.deleteExpense(transaction.txId)
            _controls.update { current ->
                if (result.isSuccess) {
                    current.copy(
                        isMutating = false,
                        selectedTransaction = null,
                        snackbarMessage = if (transaction.isSettlement) {
                            "Settlement voided"
                        } else {
                            "\"${transaction.description}\" voided"
                        },
                    )
                } else {
                    current.copy(
                        isMutating = false,
                        snackbarMessage = result.exceptionOrNull()?.message
                            ?: "Could not void transaction",
                    )
                }
            }
        }
    }

    fun recordSettlement(
        paidBy: PartnerRole,
        amount: BigDecimal,
        date: LocalDate,
        notes: String,
    ) {
        viewModelScope.launch {
            _controls.update { it.copy(isMutating = true) }
            val result = repository.recordSettlement(
                paidBy = paidBy,
                amount = amount,
                date = date,
                notes = notes,
            )
            _controls.update { current ->
                if (result.isSuccess) {
                    current.copy(
                        isMutating = false,
                        showSettleUpSheet = false,
                        snackbarMessage = "Settlement recorded",
                    )
                } else {
                    current.copy(
                        isMutating = false,
                        snackbarMessage = result.exceptionOrNull()?.message
                            ?: "Failed to record settlement",
                    )
                }
            }
        }
    }

    fun clearError() {
        repository.clearError()
    }

    fun consumeSnackbar() {
        _controls.update { it.copy(snackbarMessage = null) }
    }
}
