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
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionFinishPolicy
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
    private val pendingNotes = mutableMapOf<String, String>()

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
        val changed = _propertyId.value != null && _propertyId.value != propertyId
        if (changed) {
            noteSaveJobs.values.forEach { it.cancel() }
            noteSaveJobs.clear()
            pendingNotes.clear()
            _uiState.value = InspectionUiState(phase = phase)
        }
        _propertyId.value = propertyId
        _uiState.update { it.copy(phase = phase, error = null, isFinished = false) }
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
        pendingNotes[itemId] = note
        noteSaveJobs[itemId]?.cancel()
        noteSaveJobs[itemId] = viewModelScope.launch {
            delay(600)
            flushNote(itemId)
        }
    }

    suspend fun flushAllNotes() {
        noteSaveJobs.values.forEach { it.cancel() }
        noteSaveJobs.clear()
        val snapshot = pendingNotes.toMap()
        pendingNotes.clear()
        val phase = _uiState.value.phase
        snapshot.forEach { (itemId, note) ->
            runCatching { inspectionRepository.setNote(itemId, phase, note) }
                .onFailure { e -> _uiState.update { s -> s.copy(error = e.localizedMessage) } }
        }
    }

    private suspend fun flushNote(itemId: String) {
        val note = pendingNotes.remove(itemId) ?: return
        runCatching { inspectionRepository.setNote(itemId, _uiState.value.phase, note) }
            .onFailure { e -> _uiState.update { s -> s.copy(error = e.localizedMessage) } }
    }

    fun addItem(roomId: String, name: String) {
        val propertyId = _propertyId.value ?: return
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Item name cannot be empty.") }
            return
        }
        viewModelScope.launch {
            runCatching { inspectionRepository.addItem(propertyId, roomId, name) }
                .onFailure { _uiState.update { s -> s.copy(error = it.localizedMessage) } }
        }
    }

    fun finishInspection() {
        val propertyId = _propertyId.value ?: return
        val uid = authRepository.currentUserId.value ?: return
        val phase = _uiState.value.phase
        viewModelScope.launch {
            val property = propertyRepository.observeProperty(propertyId).first()
            if (property == null || property.landlordId != uid) {
                val message = when (phase) {
                    InspectionPhase.MOVE_IN -> "Only the landlord can submit the move-in inspection."
                    InspectionPhase.MOVE_OUT -> "Only the landlord can submit the move-out inspection."
                }
                _uiState.update { it.copy(error = message) }
                return@launch
            }
            submitInspection(propertyId, uid, phase)
        }
    }

    private fun submitInspection(propertyId: String, uid: String, phase: InspectionPhase) {
        viewModelScope.launch {
            flushAllNotes()
            runCatching { inspectionRepository.syncPendingUploads() }
            val hasUploaded = inspectionRepository.phaseHasUploadedPhotos(propertyId, phase)
            val validation = InspectionFinishPolicy.validate(
                phase = phase,
                roomCount = rooms.value.size,
                items = allItems.value,
                hasUploadedPhasePhoto = hasUploaded,
            )
            if (!validation.ok) {
                _uiState.update { it.copy(error = validation.error) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val submitted = propertyRepository.markInspectionSubmitted(propertyId, phase, uid)) {
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isLoading = false, error = submitted.userMessage) }
                    return@launch
                }
                is Outcome.Success -> Unit
            }
            _uiState.update { it.copy(isLoading = false, isFinished = true) }
            viewModelScope.launch {
                runCatching {
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
                            title = if (phase == InspectionPhase.MOVE_IN) {
                                "Move-in ready for tenant review"
                            } else {
                                "Move-out ready for tenant review"
                            },
                            body = if (phase == InspectionPhase.MOVE_IN) {
                                "The landlord finished documenting move-in. Please review and sign."
                            } else {
                                "The landlord finished documenting move-out. Please review and sign."
                            },
                            propertyId = propertyId,
                        )
                    }
                }
            }
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
