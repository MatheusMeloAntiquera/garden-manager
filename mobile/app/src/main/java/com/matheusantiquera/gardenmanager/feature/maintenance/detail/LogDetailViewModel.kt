package com.matheusantiquera.gardenmanager.feature.maintenance.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceActions
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LogDetailUiState {
    data object Loading : LogDetailUiState

    data class Loaded(val log: MaintenanceLog) : LogDetailUiState

    data class Failed(val message: UiText) : LogDetailUiState
}

@HiltViewModel
class LogDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MaintenanceRepository,
) : ViewModel() {

    private val logId: String = checkNotNull(savedStateHandle[ARG_ID]) { "LogDetailRoute sem id" }

    private val _uiState = MutableStateFlow<LogDetailUiState>(LogDetailUiState.Loading)
    val uiState: StateFlow<LogDetailUiState> = _uiState.asStateFlow()

    /** Confirmação e exclusão da execução; ao excluir, a tela volta e quem a abriu se recarrega. */
    val actions = MaintenanceActions(repository, viewModelScope, onDeleted = {})

    private var loadJob: Job? = null

    /**
     * Carrega (ou recarrega) a execução. A tela chama ao voltar a ficar visível, por exemplo depois de
     * editar; com o detalhe já na tela, a recarga é silenciosa e uma falha mantém o que já estava.
     */
    fun load() {
        loadJob?.cancel()
        if (_uiState.value !is LogDetailUiState.Loaded) _uiState.value = LogDetailUiState.Loading

        loadJob = viewModelScope.launch {
            val result = repository.getLog(logId)
            _uiState.value = when (result) {
                is ApiResult.Success -> LogDetailUiState.Loaded(result.value)
                is ApiResult.Failure -> _uiState.value as? LogDetailUiState.Loaded ?: LogDetailUiState.Failed(result.error.toUiText())
            }
        }
    }

    fun onDeleteClick() {
        val loaded = _uiState.value as? LogDetailUiState.Loaded ?: return
        actions.askDelete(MaintenanceTarget.OfLog(loaded.log))
    }

    companion object {
        /** Nome do argumento em [com.matheusantiquera.gardenmanager.navigation.LogDetailRoute]. */
        const val ARG_ID = "id"
    }
}
