package com.matheusantiquera.gardenmanager.feature.environment.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.data.auth.AuthRepository
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnvironmentsUiState(
    /** Nulo enquanto o usuário não carregou (ou se falhou): a saudação some. */
    val firstName: String? = null,
    val list: EnvironmentListState = EnvironmentListState.Loading,
)

/** Aba Ambientes: saudação e os ambientes ativos, cada um com suas plantas e manutenções atrasadas. */
@HiltViewModel
class EnvironmentsViewModel @Inject constructor(
    private val environmentRepository: EnvironmentRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnvironmentsUiState())
    val uiState: StateFlow<EnvironmentsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Carrega (ou recarrega) a tela. A tela chama ao voltar a ficar visível, para refletir o que mudou
     * em outras telas. Com a lista já na tela, a recarga é silenciosa: uma falha mantém o que já estava.
     */
    fun load() {
        loadJob?.cancel()
        if (_uiState.value.list !is EnvironmentListState.Loaded) {
            _uiState.update { it.copy(list = EnvironmentListState.Loading) }
        }

        loadJob = viewModelScope.launch {
            val environments = async { environmentRepository.listAll(active = true) }
            val user = if (_uiState.value.firstName == null) async { authRepository.currentUser() } else null

            val environmentsResult = environments.await()
            val userResult = user?.await()

            _uiState.update { current ->
                current.copy(
                    firstName = (userResult as? ApiResult.Success)?.value?.firstName ?: current.firstName,
                    list = when (environmentsResult) {
                        is ApiResult.Success -> EnvironmentListState.Loaded(environmentsResult.value)
                        is ApiResult.Failure -> current.list as? EnvironmentListState.Loaded
                            ?: EnvironmentListState.Failed(environmentsResult.error.toUiText())
                    },
                )
            }
        }
    }
}
