package vige.land.muchopesos.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vige.land.muchopesos.data.repository.LedgerRepository
import vige.land.muchopesos.data.sheet.DriveSpreadsheetItem
import vige.land.muchopesos.domain.logic.SpreadsheetIdParser
import vige.land.muchopesos.domain.model.CurrencyCatalog
import vige.land.muchopesos.domain.model.CurrencySpec

enum class OnboardingMode {
    CREATE,
    LINK,
}

data class OnboardingUiState(
    val mode: OnboardingMode = OnboardingMode.CREATE,
    val ledgerName: String = "Our Household",
    val selectedCurrency: CurrencySpec = CurrencyCatalog.default,
    val partnerAName: String = "",
    val partnerBEmail: String = "",
    val partnerBName: String = "",
    val linkInput: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val showDrivePicker: Boolean = false,
    val isLoadingDriveFiles: Boolean = false,
    val driveFiles: List<DriveSpreadsheetItem> = emptyList(),
    val driveSearchQuery: String = "",
    val driveErrorMessage: String? = null,
) {
    val canCreate: Boolean
        get() = !isSubmitting &&
            ledgerName.isNotBlank() &&
            partnerAName.isNotBlank() &&
            partnerBEmail.trim().contains('@') &&
            partnerBName.isNotBlank()

    val extractedSpreadsheetId: String?
        get() = SpreadsheetIdParser.extractSpreadsheetId(linkInput)

    val canLink: Boolean
        get() = !isSubmitting && extractedSpreadsheetId != null
}

class OnboardingViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            partnerAName = repository.userSession.value?.displayName.orEmpty(),
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    val userSession = repository.userSession

    fun onModeChange(mode: OnboardingMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
    }

    fun onLedgerNameChange(value: String) {
        _uiState.update { it.copy(ledgerName = value, errorMessage = null) }
    }

    fun onCurrencySelected(currency: CurrencySpec) {
        _uiState.update { it.copy(selectedCurrency = currency) }
    }

    fun onPartnerANameChange(value: String) {
        _uiState.update { it.copy(partnerAName = value, errorMessage = null) }
    }

    fun onPartnerBEmailChange(value: String) {
        _uiState.update { it.copy(partnerBEmail = value, errorMessage = null) }
    }

    fun onPartnerBNameChange(value: String) {
        _uiState.update { it.copy(partnerBName = value, errorMessage = null) }
    }

    fun onLinkInputChange(value: String) {
        _uiState.update { it.copy(linkInput = value, errorMessage = null) }
    }

    fun createLedger() {
        val state = _uiState.value
        if (!state.canCreate) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = repository.createLedger(
                ledgerName = state.ledgerName,
                homeCurrencyCode = state.selectedCurrency.code,
                partnerAName = state.partnerAName,
                partnerBEmail = state.partnerBEmail,
                partnerBName = state.partnerBName,
            )
            _uiState.update { current ->
                if (result.isSuccess) {
                    current.copy(isSubmitting = false)
                } else {
                    current.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message
                            ?: "Could not create shared ledger.",
                    )
                }
            }
        }
    }

    fun linkLedger(overrideUrlOrId: String? = null) {
        val target = overrideUrlOrId ?: _uiState.value.linkInput
        if (SpreadsheetIdParser.extractSpreadsheetId(target) == null) {
            _uiState.update {
                it.copy(errorMessage = "Enter a valid Google Sheets URL or Spreadsheet ID.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    errorMessage = null,
                    showDrivePicker = false,
                )
            }
            val result = repository.linkLedger(target)
            _uiState.update { current ->
                if (result.isSuccess) {
                    current.copy(isSubmitting = false)
                } else {
                    current.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message
                            ?: "Could not link spreadsheet. Make sure it is shared with you.",
                    )
                }
            }
        }
    }

    fun openDrivePicker() {
        _uiState.update { it.copy(showDrivePicker = true, driveErrorMessage = null) }
        loadDriveSpreadsheets(_uiState.value.driveSearchQuery)
    }

    fun dismissDrivePicker() {
        _uiState.update { it.copy(showDrivePicker = false) }
    }

    fun onDriveSearchQueryChange(query: String) {
        _uiState.update { it.copy(driveSearchQuery = query) }
    }

    fun loadDriveSpreadsheets(query: String? = _uiState.value.driveSearchQuery) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDriveFiles = true, driveErrorMessage = null) }
            val result = repository.listSpreadsheets(query = query?.takeIf { it.isNotBlank() })
            _uiState.update { current ->
                if (result.isSuccess) {
                    current.copy(
                        isLoadingDriveFiles = false,
                        driveFiles = result.getOrDefault(emptyList()),
                    )
                } else {
                    current.copy(
                        isLoadingDriveFiles = false,
                        driveErrorMessage = result.exceptionOrNull()?.message
                            ?: "Could not load Google Drive spreadsheets.",
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }
}
