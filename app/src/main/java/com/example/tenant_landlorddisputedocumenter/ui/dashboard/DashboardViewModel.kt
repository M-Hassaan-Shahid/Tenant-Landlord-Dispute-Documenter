package com.example.tenant_landlorddisputedocumenter.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tenant_landlorddisputedocumenter.data.SyncCoordinator
import com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository
import com.example.tenant_landlorddisputedocumenter.data.repository.PropertyRepository
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val propertyRepository: PropertyRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val properties: StateFlow<List<Property>> = authRepository.currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else propertyRepository.observeForUser(uid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        sync(force = false)
    }

    fun sync(force: Boolean = true) {
        val uid = authRepository.currentUserId.value ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = force, error = null) }
            val syncResult = if (force) {
                syncCoordinator.syncAllForUser(uid, force = true)
            } else {
                syncCoordinator.syncDashboardForUser(uid, force = false)
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (syncResult.succeeded) null
                    else syncResult.errors.joinToString("\n"),
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

class DashboardViewModelFactory(private val container: ServiceContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DashboardViewModel(
            container.authRepository,
            container.propertyRepository,
            container.syncCoordinator,
        ) as T
}
