package com.example.tenant_landlorddisputedocumenter.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tenant_landlorddisputedocumenter.data.repository.InspectionRepository
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomSetupUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

class RoomSetupViewModel(
    private val inspectionRepository: InspectionRepository,
) : ViewModel() {

    private val _propertyId = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(RoomSetupUiState())
    val uiState: StateFlow<RoomSetupUiState> = _uiState.asStateFlow()

    @Suppress("OPT_IN_USAGE")
    val rooms: StateFlow<List<InspectionRoom>> = _propertyId
        .filterNotNull()
        .flatMapLatest { inspectionRepository.observeRooms(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadForProperty(propertyId: String) {
        _propertyId.value = propertyId
    }

    fun addRoom(name: String) {
        val propertyId = _propertyId.value ?: return
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Room name cannot be empty.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { inspectionRepository.addRoom(propertyId, name) }
                .onFailure { _uiState.update { s -> s.copy(error = it.localizedMessage) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            runCatching { inspectionRepository.deleteRoom(roomId) }
                .onFailure { _uiState.update { s -> s.copy(error = it.localizedMessage) } }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

class RoomSetupViewModelFactory(private val container: ServiceContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RoomSetupViewModel(container.inspectionRepository) as T
}
