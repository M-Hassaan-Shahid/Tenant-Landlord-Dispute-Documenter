package com.example.tenant_landlorddisputedocumenter.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.InspectionRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.NotificationRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.PropertyRepository
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionRoom
import com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InspectionUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val phase: InspectionPhase = InspectionPhase.MOVE_IN,
    val isFinished: Boolean = false
)

class InspectionViewModel(
    private val authRepository: AuthRepository,
    private val inspectionRepository: InspectionRepository,
    private val propertyRepository: PropertyRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _propertyId = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(InspectionUiState())
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()
    private val noteSaveJobs = mutableMapOf<String, Job>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val rooms: StateFlow<List<InspectionRoom>> = _propertyId
        .filterNotNull()
        .flatMapLatest { inspectionRepository.observeRooms(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allItems: StateFlow<List<ChecklistItem>> = _propertyId
        .filterNotNull()
        .flatMapLatest { inspectionRepository.observeAllItems(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadForProperty(propertyId: String, phase: InspectionPhase) {
        _propertyId.value = propertyId
        _uiState.update { it.copy(phase = phase) }
    }

    fun getItemsForRoom(roomId: String): StateFlow<List<ChecklistItem>> =
        inspectionRepository.observeItems(roomId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRating(itemId: String, rating: ConditionRating) {
        viewModelScope.launch {
            runCatching { inspectionRepository.setRating(itemId, _uiState.value.phase, rating) }
                .onFailure { e -> _uiState.update { s -> s.copy(error = e.localizedMessage) } }
        }
    }

    fun setNote(itemId: String, note: String) {
        noteSaveJobs[itemId]?.cancel()
        noteSaveJobs[itemId] = viewModelScope.launch {
            delay(600)
            runCatching { inspectionRepository.setNote(itemId, _uiState.value.phase, note) }
                .onFailure { e -> _uiState.update { s -> s.copy(error = e.localizedMessage) } }
        }
    }

    fun addItem(roomId: String, name: String) {
        val propertyId = _propertyId.value ?: return
        viewModelScope.launch {
            runCatching { inspectionRepository.addItem(propertyId, roomId, name) }
                .onFailure { _uiState.update { s -> s.copy(error = it.localizedMessage) } }
        }
    }

    fun finishInspection() {
        val propertyId = _propertyId.value ?: return
        val uid = authRepository.currentUserId.value ?: return
        val phase = _uiState.value.phase
        if (rooms.value.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one room in Room Setup before inspecting.") }
            return
        }
        val items = allItems.value
        if (items.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one checklist item before finishing.") }
            return
        }
        val ratedCount = items.count { item ->
            if (phase == InspectionPhase.MOVE_IN) item.moveInRating != null else item.moveOutRating != null
        }
        if (ratedCount == 0) {
            _uiState.update { it.copy(error = "Rate at least one item before finishing the inspection.") }
            return
        }
        viewModelScope.launch {
            when (val submitted = propertyRepository.markInspectionSubmitted(propertyId, phase)) {
                is Outcome.Failure -> _uiState.update { it.copy(error = submitted.userMessage) }
                is Outcome.Success -> Unit
            }
            val notifyResult = runCatching {
                val property = propertyRepository.observeProperty(propertyId).first() ?: return@runCatching
                val recipientUid = when (uid) {
                    property.landlordId -> property.tenantId
                    property.tenantId -> property.landlordId
                    else -> null
                }
                recipientUid?.let {
                    notificationRepository.push(
                        recipientUid = it,
                        type = NotificationType.INSPECTION_SUBMITTED,
                        title = "Inspection ready to sign",
                        body = "Your counterpart finished documenting. Please review and sign.",
                        propertyId = propertyId,
                    )
                }
            }
            if (notifyResult.isFailure) {
                _uiState.update {
                    it.copy(error = notifyResult.exceptionOrNull()?.localizedMessage ?: "Could not notify the other party.")
                }
            }
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearFinished() = _uiState.update { it.copy(isFinished = false) }
}

class InspectionViewModelFactory(private val container: ServiceContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        InspectionViewModel(
            container.authRepository,
            container.inspectionRepository,
            container.propertyRepository,
            container.notificationRepository,
        ) as T
}
