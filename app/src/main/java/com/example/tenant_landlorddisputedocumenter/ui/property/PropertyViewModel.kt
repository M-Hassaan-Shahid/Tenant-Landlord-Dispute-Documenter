package com.example.tenant_landlorddisputedocumenter.ui.property

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.PropertyRepository
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PropertyUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successProperty: Property? = null
)

class PropertyViewModel(
    private val authRepository: AuthRepository,
    private val propertyRepository: PropertyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PropertyUiState())
    val uiState: StateFlow<PropertyUiState> = _uiState.asStateFlow()

    fun createProperty(
        address: String,
        rentStr: String,
        depositStr: String,
        leaseStartMillis: Long,
        leaseEndMillis: Long,
    ) {
        val uid = authRepository.currentUserId.value
        if (uid == null) {
            _uiState.update { it.copy(error = "Must be logged in.") }
            return
        }
        val rent = rentStr.toDoubleOrNull()
        val deposit = depositStr.toDoubleOrNull()
        if (address.isBlank() || rent == null || deposit == null) {
            _uiState.update { it.copy(error = "Invalid input fields.") }
            return
        }
        if (leaseEndMillis <= leaseStartMillis) {
            _uiState.update { it.copy(error = "Lease end must be after lease start.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successProperty = null) }

            when (val result = propertyRepository.createProperty(
                uid, address, rent, deposit, leaseStartMillis, leaseEndMillis,
            )) {
                is Outcome.Success -> _uiState.update { it.copy(isLoading = false, successProperty = result.value) }
                is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.userMessage) }
            }
        }
    }

    fun joinProperty(code: String) {
        val uid = authRepository.currentUserId.value
        if (uid == null) {
            _uiState.update { it.copy(error = "Must be logged in.") }
            return
        }
        if (code.isBlank() || code.length != 6) {
            _uiState.update { it.copy(error = "Invite code must be 6 characters.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successProperty = null) }
            when (val result = propertyRepository.joinPropertyByCode(uid, code)) {
                is Outcome.Success -> _uiState.update { it.copy(isLoading = false, successProperty = result.value) }
                is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.userMessage) }
            }
        }
    }

    fun clearState() {
        _uiState.update { PropertyUiState() }
    }
}

class PropertyViewModelFactory(private val container: ServiceContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PropertyViewModel(container.authRepository, container.propertyRepository) as T
}
