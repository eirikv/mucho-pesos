package vige.land.muchopesos.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vige.land.muchopesos.data.repository.LedgerRepository
import vige.land.muchopesos.data.repository.LedgerUiSnapshot
import vige.land.muchopesos.domain.model.UserSession

data class SettingsFormState(
    val partnerAName: String = "",
    val partnerBName: String = "",
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
)

class SettingsViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    val ledgerState: StateFlow<LedgerUiSnapshot> = repository.uiState
    val userSession: StateFlow<UserSession?> = repository.userSession

    private val _formState = MutableStateFlow(
        SettingsFormState(
            partnerAName = repository.uiState.value.config?.partnerAName.orEmpty(),
            partnerBName = repository.uiState.value.config?.partnerBName.orEmpty(),
        ),
    )
    val formState: StateFlow<SettingsFormState> = _formState.asStateFlow()

    fun onPartnerANameChange(value: String) {
        _formState.update { it.copy(partnerAName = value) }
    }

    fun onPartnerBNameChange(value: String) {
        _formState.update { it.copy(partnerBName = value) }
    }

    fun savePartnerNames() {
        val current = _formState.value
        if (current.partnerAName.isBlank() || current.partnerBName.isBlank()) {
            _formState.update { it.copy(snackbarMessage = "Both partner names are required") }
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            val result = repository.updatePartnerNames(
                partnerAName = current.partnerAName,
                partnerBName = current.partnerBName,
            )
            _formState.update { state ->
                if (result.isSuccess) {
                    val updated = result.getOrThrow()
                    state.copy(
                        partnerAName = updated.partnerAName,
                        partnerBName = updated.partnerBName,
                        isSaving = false,
                        snackbarMessage = "Partner names saved to Config tab",
                    )
                } else {
                    state.copy(
                        isSaving = false,
                        snackbarMessage = result.exceptionOrNull()?.message
                            ?: "Failed to update partner names",
                    )
                }
            }
        }
    }

    fun unlinkLedger() {
        viewModelScope.launch {
            repository.unlinkLedger()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }

    fun showMessage(message: String) {
        _formState.update { it.copy(snackbarMessage = message) }
    }

    fun consumeSnackbar() {
        _formState.update { it.copy(snackbarMessage = null) }
    }
}
