package com.matheusantiquera.gardenmanager.feature.environment.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ambientes inativos, de onde o usuário pode abrir um para reativar. */
@HiltViewModel
class ArchivedEnvironmentsViewModel @Inject constructor(
    private val environmentRepository: EnvironmentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<EnvironmentListState>(EnvironmentListState.Loading)
    val uiState: StateFlow<EnvironmentListState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Mesma regra da aba: com a lista na tela, a recarga é silenciosa e uma falha mantém o que já estava. */
    fun load() {
        loadJob?.cancel()
        if (_uiState.value !is EnvironmentListState.Loaded) _uiState.value = EnvironmentListState.Loading

        loadJob = viewModelScope.launch {
            val result = environmentRepository.listAll(active = false)
            _uiState.update { current ->
                when (result) {
                    is ApiResult.Success -> EnvironmentListState.Loaded(result.value)
                    is ApiResult.Failure -> current as? EnvironmentListState.Loaded
                        ?: EnvironmentListState.Failed(result.error.toUiText())
                }
            }
        }
    }
}
