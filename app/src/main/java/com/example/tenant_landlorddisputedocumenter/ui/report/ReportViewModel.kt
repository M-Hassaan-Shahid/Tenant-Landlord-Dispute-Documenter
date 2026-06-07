package com.example.tenant_landlorddisputedocumenter.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tenant_landlorddisputedocumenter.data.SyncCoordinator
import com.example.tenant_landlorddisputedocumenter.data.repository.ReportRepository
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ReportUiState(
    val isGenerating: Boolean = false,
    val error: String? = null,
    val reportFile: File? = null
)

class ReportViewModel(
    private val syncCoordinator: SyncCoordinator,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun generateReport(propertyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, reportFile = null) }
            runCatching {
                val sync = syncCoordinator.syncPropertyForReport(propertyId)
                reportRepository.validateReportReady(propertyId)
                val file = reportRepository.generateReport(propertyId)
                if (!sync.succeeded && file != null) {
                    // Report generated from local cache; cloud sync had partial failures.
                    file to sync.errors.firstOrNull()
                } else if (!sync.succeeded) {
                    error(sync.errors.joinToString("\n"))
                } else {
                    file to null
                }
            }
                .onSuccess { (file, syncWarning) ->
                    if (file != null) {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                reportFile = file,
                                error = syncWarning,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isGenerating = false, error = "Could not generate report. Is the inspection complete?") }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isGenerating = false, error = e.localizedMessage ?: "Report generation failed.") }
                }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(error = null, reportFile = null) }
}

class ReportViewModelFactory(private val container: ServiceContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReportViewModel(container.syncCoordinator, container.reportRepository) as T
}
