package com.example.tenant_landlorddisputedocumenter.ui.dispute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tenant_landlorddisputedocumenter.data.SyncCoordinator
import com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.DisputeRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.InspectionRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.NotificationRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.PropertyRepository
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.example.tenant_landlorddisputedocumenter.domain.model.Dispute
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DisputeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    /** True once the first remote sync for this property has settled (success or failure). */
    val firstLoadDone: Boolean = false,
)

class DisputeViewModel(
    private val authRepository: AuthRepository,
    private val disputeRepository: DisputeRepository,
    private val propertyRepository: PropertyRepository,
    private val notificationRepository: NotificationRepository,
    private val inspectionRepository: InspectionRepository,
    private val syncCoordinator: SyncCoordinator,
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

    /** uid -> display name for both parties on this property (for dispute attribution). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val partyNames: StateFlow<Map<String, String>> = _propertyId
        .filterNotNull()
        .flatMapLatest { propertyRepository.observeProperty(it) }
        .flatMapLatest { p ->
            val ids = listOfNotNull(p?.landlordId, p?.tenantId).filter { it.isNotBlank() }
            authRepository.observeProfiles(ids).map { profiles ->
                profiles.mapValues { it.value.displayName }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun loadForProperty(propertyId: String) {
        if (_propertyId.value == propertyId) return
        _propertyId.value = propertyId
        viewModelScope.launch {
            // force = true so a just-raised dispute is always pulled, regardless of the
            // property's sync-cache TTL. Opening this screen must show the latest disputes.
            runCatching { syncCoordinator.refreshPropertyData(propertyId, force = true) }
            _uiState.update { it.copy(firstLoadDone = true) }
        }
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
                // Refresh first so the dispute window check matches the latest move-out state.
                propertyRepository.refreshProperty(propertyId)
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

    /** Landlord proposes a resolution; the tenant must still confirm it. */
    fun proposeResolution(disputeId: String, resolutionNote: String) {
        val propertyId = _propertyId.value ?: return
        val uid = authRepository.currentUserId.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                disputeRepository.proposeResolution(disputeId, uid, resolutionNote)
            }.onSuccess {
                notifyOtherParty(
                    propertyId = propertyId,
                    actorUid = uid,
                    type = NotificationType.DISPUTE_RESOLUTION_PROPOSED,
                    title = "Resolution proposed",
                    bodyFor = { "A resolution was proposed for a dispute at $it. Open it to confirm or reject." },
                )
                _uiState.update { it.copy(isLoading = false) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Could not propose a resolution.")
                }
            }
        }
    }

    /** Tenant confirms ([accept] == true) or rejects the landlord's proposed resolution. */
    fun respondToProposal(disputeId: String, accept: Boolean, responseNote: String = "") {
        val propertyId = _propertyId.value ?: return
        val uid = authRepository.currentUserId.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                disputeRepository.respondToProposal(disputeId, uid, accept, responseNote)
            }.onSuccess {
                val outcome = if (accept) "resolved" else "unresolved"
                notifyOtherParty(
                    propertyId = propertyId,
                    actorUid = uid,
                    type = NotificationType.DISPUTE_RESOLVED,
                    title = "Dispute $outcome",
                    bodyFor = { "The tenant marked a proposed resolution as $outcome at $it." },
                )
                _uiState.update { it.copy(isLoading = false) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Could not submit your response.")
                }
            }
        }
    }

    private suspend fun notifyOtherParty(
        propertyId: String,
        actorUid: String,
        type: NotificationType,
        title: String,
        bodyFor: (address: String) -> String,
    ) {
        runCatching {
            val property = propertyRepository.getProperty(propertyId) ?: return@runCatching
            val recipientUid = when (actorUid) {
                property.landlordId -> property.tenantId
                property.tenantId -> property.landlordId
                else -> null
            }
            recipientUid?.let {
                notificationRepository.push(
                    recipientUid = it,
                    type = type,
                    title = title,
                    body = bodyFor(property.address),
                    propertyId = propertyId,
                )
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
            container.syncCoordinator,
        ) as T
}
