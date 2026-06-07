package com.example.tenant_landlorddisputedocumenter.ui.dispute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.DisputeRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.InspectionRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.NotificationRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.PropertyRepository
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.example.tenant_landlorddisputedocumenter.domain.model.Dispute
import com.example.tenant_landlorddisputedocumenter.domain.model.DisputeStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionEditPolicy
import com.example.tenant_landlorddisputedocumenter.domain.model.NotificationType
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole
import com.example.tenant_landlorddisputedocumenter.util.InputValidation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DisputeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
)

class DisputeViewModel(
    private val authRepository: AuthRepository,
    private val disputeRepository: DisputeRepository,
    private val propertyRepository: PropertyRepository,
    private val notificationRepository: NotificationRepository,
    private val inspectionRepository: InspectionRepository,
) : ViewModel() {

    private val _propertyId = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(DisputeUiState())
    val uiState: StateFlow<DisputeUiState> = _uiState.asStateFlow()

    var targetItemId: String? = null
    var counterPhotoId: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val disputes: StateFlow<List<Dispute>> = _propertyId
        .filterNotNull()
        .flatMapLatest { disputeRepository.observeForProperty(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadForProperty(propertyId: String) {
        _propertyId.value = propertyId
    }

    fun setCounterPhoto(photoId: String) {
        counterPhotoId = photoId
    }

    fun submitDispute(reason: String, counterNote: String = "") {
        val propertyId = _propertyId.value ?: return
        val itemId = targetItemId ?: return
        val uid = authRepository.currentUserId.value ?: return
        val role = authRepository.currentUserRole.value ?: UserRole.TENANT

        if (reason.isBlank()) {
            _uiState.update { it.copy(error = "Please provide a reason for the dispute.") }
            return
        }
        InputValidation.validateTextLength(reason, "Reason")?.let { message ->
            _uiState.update { it.copy(error = message) }
            return
        }
        if (authRepository.currentUserRole.value != UserRole.TENANT) {
            _uiState.update { it.copy(error = "Only tenants can raise disputes from this flow.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val propertyEntity = propertyRepository.getProperty(propertyId)
                    ?: error("Property not found.")
                val entity = com.example.tenant_landlorddisputedocumenter.data.local.entity.PropertyEntity.from(propertyEntity)
                if (!InspectionEditPolicy.canRaiseDispute(entity, uid)) {
                    error("Disputes are only available to tenants during the review window.")
                }
                inspectionRepository.syncPendingUploads()
                disputeRepository.raise(
                    propertyId = propertyId,
                    itemId = itemId,
                    raisedByUid = uid,
                    raisedByRole = role,
                    reason = reason,
                    counterPhotoIds = counterPhotoId?.let { listOf(it) } ?: emptyList(),
                    counterNote = counterNote,
                )
            }.onSuccess {
                runCatching {
                    val property = propertyRepository.getProperty(propertyId) ?: return@runCatching
                    val recipientUid = when (uid) {
                        property.landlordId -> property.tenantId
                        property.tenantId -> property.landlordId
                        else -> null
                    }
                    recipientUid?.let {
                        notificationRepository.push(
                            recipientUid = it,
                            type = NotificationType.DISPUTE_RAISED,
                            title = "Dispute raised",
                            body = "A dispute was raised on an inspection item at ${property.address}.",
                            propertyId = propertyId,
                        )
                    }
                }
                _uiState.update { it.copy(isLoading = false, submitted = true) }
            }.onFailure { exception ->
                _uiState.update {
                    it.copy(isLoading = false, error = exception.localizedMessage ?: "Could not submit dispute.")
                }
            }
        }
    }

    fun resolveDispute(disputeId: String, resolutionNote: String, status: DisputeStatus) {
        val propertyId = _propertyId.value ?: return
        val uid = authRepository.currentUserId.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                disputeRepository.resolve(disputeId, uid, resolutionNote, status)
            }.onSuccess {
                runCatching {
                    val property = propertyRepository.getProperty(propertyId) ?: return@runCatching
                    val recipientUid = when (uid) {
                        property.landlordId -> property.tenantId
                        property.tenantId -> property.landlordId
                        else -> null
                    }
                    recipientUid?.let {
                        notificationRepository.push(
                            recipientUid = it,
                            type = NotificationType.DISPUTE_RESOLVED,
                            title = "Dispute ${status.name.lowercase()}",
                            body = "A dispute on ${property.address} was marked ${status.name.lowercase()}.",
                            propertyId = propertyId,
                        )
                    }
                }
                _uiState.update { it.copy(isLoading = false) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Could not resolve dispute.")
                }
            }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(error = null, submitted = false) }
}

class DisputeViewModelFactory(private val container: ServiceContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DisputeViewModel(
            container.authRepository,
            container.disputeRepository,
            container.propertyRepository,
            container.notificationRepository,
            container.inspectionRepository,
        ) as T
}
