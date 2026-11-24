package vige.land.muchopesos.ui.screens.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vige.land.muchopesos.data.repository.LedgerRepository
import vige.land.muchopesos.domain.logic.MoneyFormatter
import vige.land.muchopesos.domain.logic.SplitCalculator
import vige.land.muchopesos.domain.logic.SplitRequest
import vige.land.muchopesos.domain.logic.SplitResult
import vige.land.muchopesos.domain.model.CategoryCatalog
import vige.land.muchopesos.domain.model.CurrencySpec
import vige.land.muchopesos.domain.model.ExpenseCategory
import vige.land.muchopesos.domain.model.LedgerConfig
import vige.land.muchopesos.domain.model.PartnerRole
import vige.land.muchopesos.domain.model.SplitType

data class ExpenseEditorUiState(
    val isEditMode: Boolean = false,
    val originalTxId: String? = null,
    val config: LedgerConfig? = null,
    val viewerRole: PartnerRole = PartnerRole.PARTNER_A,
    val amountInput: String = "",
    val description: String = "",
    val category: ExpenseCategory = CategoryCatalog.GROCERIES,
    val date: LocalDate = LocalDate.now(),
    val paidBy: PartnerRole = PartnerRole.PARTNER_A,
    val splitType: SplitType = SplitType.EQUAL,
    val exactPartnerAInput: String = "",
    val exactPartnerBInput: String = "",
    val percentPartnerAInput: String = "50",
    val percentPartnerBInput: String = "50",
    val fullOwedBy: PartnerRole = PartnerRole.PARTNER_B,
    val notes: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val currency: CurrencySpec
        get() = config?.currencySpec ?: vige.land.muchopesos.domain.model.CurrencyCatalog.default

    val parsedAmount: BigDecimal?
        get() = MoneyFormatter.parseUserAmount(amountInput, currency.fractionDigits)
            ?.takeIf { it.compareTo(BigDecimal.ZERO) > 0 }

    val parsedExactA: BigDecimal?
        get() = if (exactPartnerAInput.isBlank()) {
            BigDecimal.ZERO.setScale(currency.fractionDigits, RoundingMode.HALF_UP)
        } else {
            MoneyFormatter.parseUserAmount(exactPartnerAInput, currency.fractionDigits)
        }

    val parsedExactB: BigDecimal?
        get() = if (exactPartnerBInput.isBlank()) {
            BigDecimal.ZERO.setScale(currency.fractionDigits, RoundingMode.HALF_UP)
        } else {
            MoneyFormatter.parseUserAmount(exactPartnerBInput, currency.fractionDigits)
        }

    val exactRemaining: BigDecimal?
        get() {
            val total = parsedAmount ?: return null
            val a = parsedExactA ?: return null
            val b = parsedExactB ?: return null
            return total.subtract(a).subtract(b)
                .setScale(currency.fractionDigits, RoundingMode.HALF_UP)
        }

    val parsedPercentA: BigDecimal?
        get() = parsePercentageInput(percentPartnerAInput)

    val parsedPercentB: BigDecimal?
        get() = parsePercentageInput(percentPartnerBInput)

    val percentageTotal: BigDecimal?
        get() {
            val a = parsedPercentA ?: return null
            val b = parsedPercentB ?: return null
            return a.add(b)
        }

    fun buildSplitRequest(): SplitRequest? = when (splitType) {
        SplitType.EQUAL -> SplitRequest.Equal
        SplitType.EXACT -> {
            val a = parsedExactA ?: return null
            val b = parsedExactB ?: return null
            SplitRequest.Exact(partnerAShare = a, partnerBShare = b)
        }
        SplitType.PERCENTAGE -> {
            val a = parsedPercentA ?: return null
            val b = parsedPercentB ?: return null
            SplitRequest.Percentage(partnerAPercent = a, partnerBPercent = b)
        }
        SplitType.FULL -> SplitRequest.Full(owedBy = fullOwedBy)
        SplitType.SETTLEMENT -> SplitRequest.Equal
    }

    val liveSplitCalculation: Result<SplitResult>?
        get() {
            val total = parsedAmount ?: return null
            val request = buildSplitRequest() ?: return Result.failure(
                IllegalArgumentException("Enter valid split values"),
            )
            return SplitCalculator.calculate(
                totalAmount = total,
                paidBy = paidBy,
                request = request,
                scale = currency.fractionDigits,
            )
        }

    val canSave: Boolean
        get() = !isSaving &&
            config != null &&
            description.isNotBlank() &&
            parsedAmount != null &&
            liveSplitCalculation?.isSuccess == true
}

private val HUNDRED = BigDecimal("100")

private fun parsePercentageInput(raw: String): BigDecimal? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = trimmed.replace(',', '.')
    if (normalized.count { it == '.' } > 1) return null
    val parsed = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
    if (parsed.compareTo(BigDecimal.ZERO) < 0 || parsed.compareTo(HUNDRED) > 0) return null
    return parsed
}

private fun formatPercentText(value: BigDecimal): String =
    if (value.compareTo(BigDecimal.ZERO) == 0) {
        "0"
    } else {
        value.stripTrailingZeros().toPlainString()
    }

class ExpenseEditorViewModel(
    private val repository: LedgerRepository,
    private val editTxId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<ExpenseEditorUiState> = _uiState.asStateFlow()

    private fun buildInitialState(): ExpenseEditorUiState {
        val snapshot = repository.uiState.value
        val config = snapshot.config
        val viewerRole = snapshot.currentUserRole
        val scale = config?.currencySpec?.fractionDigits ?: 2

        val existing = editTxId?.let { repository.findActiveTransaction(it) }
        if (existing != null && !existing.isSettlement) {
            val parsedPcts = SplitCalculator.parsePercentageMeta(existing.splitMeta)
            val fullOwedBy = when {
                existing.splitType == SplitType.FULL &&
                    existing.partnerAShare.compareTo(BigDecimal.ZERO) > 0 -> PartnerRole.PARTNER_A
                existing.splitType == SplitType.FULL -> PartnerRole.PARTNER_B
                else -> existing.paidBy.other()
            }
            return ExpenseEditorUiState(
                isEditMode = true,
                originalTxId = existing.txId,
                config = config,
                viewerRole = viewerRole,
                amountInput = MoneyFormatter.toSheetPlainString(existing.amount, scale),
                description = existing.description,
                category = existing.category,
                date = existing.date,
                paidBy = existing.paidBy,
                splitType = if (existing.splitType == SplitType.SETTLEMENT) SplitType.EQUAL else existing.splitType,
                exactPartnerAInput = MoneyFormatter.toSheetPlainString(existing.partnerAShare, scale),
                exactPartnerBInput = MoneyFormatter.toSheetPlainString(existing.partnerBShare, scale),
                percentPartnerAInput = parsedPcts?.first?.let(::formatPercentText) ?: "50",
                percentPartnerBInput = parsedPcts?.second?.let(::formatPercentText) ?: "50",
                fullOwedBy = fullOwedBy,
                notes = existing.notes,
            )
        }

        return ExpenseEditorUiState(
            isEditMode = false,
            originalTxId = null,
            config = config,
            viewerRole = viewerRole,
            paidBy = viewerRole,
            fullOwedBy = viewerRole.other(),
        )
    }

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amountInput = value, errorMessage = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value, errorMessage = null) }
    }

    fun onCategorySelected(category: ExpenseCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun onPaidByChange(role: PartnerRole) {
        _uiState.update { current ->
            val updatedFullOwedBy = if (current.splitType == SplitType.FULL) {
                role.other()
            } else {
                current.fullOwedBy
            }
            current.copy(
                paidBy = role,
                fullOwedBy = updatedFullOwedBy,
            )
        }
    }

    fun onSplitTypeChange(splitType: SplitType) {
        _uiState.update { current ->
            var nextExactA = current.exactPartnerAInput
            var nextExactB = current.exactPartnerBInput
            val scale = current.currency.fractionDigits

            // Pre-populate exact shares with a helpful 50/50 split if empty when switching to Exact
            if (splitType == SplitType.EXACT &&
                nextExactA.isBlank() &&
                nextExactB.isBlank()
            ) {
                val total = current.parsedAmount
                if (total != null) {
                    val eq = SplitCalculator.calculate(
                        totalAmount = total,
                        paidBy = current.paidBy,
                        request = SplitRequest.Equal,
                        scale = scale,
                    ).getOrNull()
                    if (eq != null) {
                        nextExactA = MoneyFormatter.toSheetPlainString(eq.partnerAShare, scale)
                        nextExactB = MoneyFormatter.toSheetPlainString(eq.partnerBShare, scale)
                    }
                }
            }

            val nextFullOwedBy = if (splitType == SplitType.FULL && current.splitType != SplitType.FULL) {
                current.paidBy.other()
            } else {
                current.fullOwedBy
            }

            current.copy(
                splitType = splitType,
                exactPartnerAInput = nextExactA,
                exactPartnerBInput = nextExactB,
                fullOwedBy = nextFullOwedBy,
                errorMessage = null,
            )
        }
    }

    fun onExactPartnerAChange(value: String) {
        _uiState.update { it.copy(exactPartnerAInput = value, errorMessage = null) }
    }

    fun onExactPartnerBChange(value: String) {
        _uiState.update { it.copy(exactPartnerBInput = value, errorMessage = null) }
    }

    fun fillRemainingFor(role: PartnerRole) {
        _uiState.update { current ->
            val total = current.parsedAmount ?: return@update current
            val scale = current.currency.fractionDigits
            when (role) {
                PartnerRole.PARTNER_A -> {
                    val b = current.parsedExactB ?: BigDecimal.ZERO
                    val rem = total.subtract(b).max(BigDecimal.ZERO)
                    current.copy(exactPartnerAInput = MoneyFormatter.toSheetPlainString(rem, scale))
                }
                PartnerRole.PARTNER_B -> {
                    val a = current.parsedExactA ?: BigDecimal.ZERO
                    val rem = total.subtract(a).max(BigDecimal.ZERO)
                    current.copy(exactPartnerBInput = MoneyFormatter.toSheetPlainString(rem, scale))
                }
            }
        }
    }

    fun onPercentPartnerAChange(value: String) {
        _uiState.update { current ->
            val parsedA = parsePercentageInput(value)
            val updatedB = if (parsedA != null) {
                formatPercentText(HUNDRED.subtract(parsedA))
            } else {
                current.percentPartnerBInput
            }
            current.copy(
                percentPartnerAInput = value,
                percentPartnerBInput = updatedB,
                errorMessage = null,
            )
        }
    }

    fun onPercentPartnerBChange(value: String) {
        _uiState.update { current ->
            val parsedB = parsePercentageInput(value)
            val updatedA = if (parsedB != null) {
                formatPercentText(HUNDRED.subtract(parsedB))
            } else {
                current.percentPartnerAInput
            }
            current.copy(
                percentPartnerAInput = updatedA,
                percentPartnerBInput = value,
                errorMessage = null,
            )
        }
    }

    fun onFullOwedByChange(role: PartnerRole) {
        _uiState.update { it.copy(fullOwedBy = role, errorMessage = null) }
    }

    fun onNotesChange(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun save(onSuccess: () -> Unit) {
        val state = _uiState.value
        val amount = state.parsedAmount ?: run {
            _uiState.update { it.copy(errorMessage = "Enter a valid positive amount.") }
            return
        }
        val splitRequest = state.buildSplitRequest() ?: run {
            _uiState.update { it.copy(errorMessage = "Enter valid split amounts.") }
            return
        }
        val validation = state.liveSplitCalculation
        if (validation == null || validation.isFailure) {
            _uiState.update {
                it.copy(
                    errorMessage = validation?.exceptionOrNull()?.message
                        ?: "Check split values before saving.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = if (state.isEditMode && state.originalTxId != null) {
                repository.editExpense(
                    originalTxId = state.originalTxId,
                    description = state.description,
                    category = state.category,
                    amount = amount,
                    date = state.date,
                    paidBy = state.paidBy,
                    splitRequest = splitRequest,
                    notes = state.notes,
                )
            } else {
                repository.addExpense(
                    description = state.description,
                    category = state.category,
                    amount = amount,
                    date = state.date,
                    paidBy = state.paidBy,
                    splitRequest = splitRequest,
                    notes = state.notes,
                )
            }

            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to save expense.",
                    )
                }
            }
        }
    }

    fun delete(onSuccess: () -> Unit) {
        val txId = _uiState.value.originalTxId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.deleteExpense(txId)
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to void expense.",
                    )
                }
            }
        }
    }
}
