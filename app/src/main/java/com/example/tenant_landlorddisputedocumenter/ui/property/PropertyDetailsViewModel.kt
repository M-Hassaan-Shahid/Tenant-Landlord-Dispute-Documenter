package com.example.tenant_landlorddisputedocumenter.ui.property

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.PropertyRepository
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PropertyDetailsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val actionSuccess: String? = null
)

class PropertyDetailsViewModel(
    private val authRepository: AuthRepository,
    private val propertyRepository: PropertyRepository,
) : ViewModel() {

    private val _inspectionPhase = MutableStateFlow(InspectionPhase.MOVE_IN)
    val inspectionPhase = _inspectionPhase.asStateFlow()

    private val _propertyId = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(PropertyDetailsUiState())
    val uiState: StateFlow<PropertyDetailsUiState> = _uiState.asStateFlow()

    @Suppress("OPT_IN_USAGE")
    val property: StateFlow<Property?> = _propertyId
        .filterNotNull()
        .flatMapLatest { propertyRepository.observeProperty(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadProperty(propertyId: String) {
        _propertyId.value = propertyId
        viewModelScope.launch {
            runCatching { propertyRepository.refreshProperty(propertyId) }
        }
    }

    fun approveTenant() {
        val id = _propertyId.value ?: return
        val landlordId = authRepository.currentUserId.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = propertyRepository.approveTenant(id, landlordId)) {
                is Outcome.Success -> _uiState.update { it.copy(isLoading = false, actionSuccess = "Tenant approved!") }
                is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.userMessage) }
            }
        }
    }

    fun rejectTenant() {
        val id = _propertyId.value ?: return
        val landlordId = authRepository.currentUserId.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = propertyRepository.rejectTenant(id, landlordId)) {
                is Outcome.Success -> _uiState.update { it.copy(isLoading = false, actionSuccess = "Tenant request rejected.") }
                is Outcome.Failure -> _uiState.update { it.copy(isLoading = false, error = result.userMessage) }
            }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(error = null, actionSuccess = null) }

    fun startMoveOut(landlordId: String) {
        val id = _propertyId.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = propertyRepository.startMoveOut(id, landlordId)) {
                is Outcome.Success -> {
                    _inspectionPhase.value = InspectionPhase.MOVE_OUT
                    _uiState.update {
                        it.copy(isLoading = false, actionSuccess = "Move-out inspection started.")
                    }
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = result.userMessage ?: result.error.message)
                }
            }
        }
    }

    fun setInspectionPhase(phase: InspectionPhase) {
        _inspectionPhase.value = phase
    }
}

class PropertyDetailsViewModelFactory(private val container: ServiceContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PropertyDetailsViewModel(container.authRepository, container.propertyRepository) as T
}
