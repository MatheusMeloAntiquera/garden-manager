package com.matheusantiquera.gardenmanager.feature.plant.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.environment.Environment
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentRepository
import com.matheusantiquera.gardenmanager.data.plant.Plant
import com.matheusantiquera.gardenmanager.data.plant.PlantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filtro da aba: plantas ativas (todas ou de um ambiente) ou as arquivadas. */
sealed interface PlantsFilter {
    data object All : PlantsFilter
    data class ByEnvironment(val environmentId: String) : PlantsFilter
    data object Archived : PlantsFilter
}

sealed interface PlantListState {
    data object Loading : PlantListState
    data class Loaded(val plants: List<Plant>, val total: Int, val page: Int, val hasMore: Boolean) : PlantListState
    data class Failed(val message: UiText) : PlantListState
}

data class PlantsUiState(
    val query: String = "",
    val filter: PlantsFilter = PlantsFilter.All,
    /** Ambientes ativos, um chip de filtro para cada. */
    val environments: List<Environment> = emptyList(),
    val list: PlantListState = PlantListState.Loading,
    val isLoadingMore: Boolean = false,
) {
    /** Ambiente do filtro atual, para a legenda "N plantas em X" e o link "Editar ambiente". */
    val filteredEnvironment: Environment?
        get() = (filter as? PlantsFilter.ByEnvironment)?.let { f -> environments.firstOrNull { it.id == f.environmentId } }
}

/** Espera depois da última tecla antes de buscar, para não chamar a API a cada letra. */
const val SEARCH_DEBOUNCE_MILLIS = 300L

@OptIn(FlowPreview::class)
@HiltViewModel
class PlantsViewModel @Inject constructor(
    private val plantRepository: PlantRepository,
    private val environmentRepository: EnvironmentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlantsUiState())
    val uiState: StateFlow<PlantsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        // A busca recarrega depois de uma pausa na digitação. drop(1) ignora o valor inicial, que o load() já cobre.
        viewModelScope.launch {
            _uiState.map { it.query.trim() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .collect { reload(showLoading = true) }
        }
    }

    /**
     * Filtro por ambiente pedido de fora: o card de um ambiente abre esta aba já filtrada. Aplica mesmo
     * que o ambiente já seja o filtro atual, para recarregar a lista.
     */
    fun applyEnvironmentFilter(environmentId: String) {
        _uiState.update { it.copy(filter = PlantsFilter.ByEnvironment(environmentId)) }
        reload(showLoading = true)
    }

    /**
     * Carrega (ou recarrega) a tela. A tela chama ao voltar a ficar visível; com a lista já na tela, a
     * recarga é silenciosa e uma falha mantém o que já estava.
     */
    fun load() = reload(showLoading = false)

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun onFilterSelected(filter: PlantsFilter) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        reload(showLoading = true)
    }

    /** Carrega a próxima página, quando houver. A tela chama ao chegar perto do fim da lista. */
    fun loadMore() {
        val current = _uiState.value
        val list = current.list as? PlantListState.Loaded ?: return
        if (!list.hasMore || current.isLoadingMore || loadJob?.isActive == true) return

        _uiState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            val result = fetchPage(current, page = list.page + 1)
            _uiState.update { state ->
                val loaded = state.list as? PlantListState.Loaded
                when {
                    loaded == null -> state.copy(isLoadingMore = false)
                    result is ApiResult.Success -> state.copy(
                        isLoadingMore = false,
                        list = PlantListState.Loaded(
                            plants = loaded.plants + result.value.plants,
                            total = result.value.total,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                        ),
                    )
                    // Falha ao carregar mais: mantém o que está na tela; rolar de novo tenta outra vez.
                    else -> state.copy(isLoadingMore = false)
                }
            }
        }
    }

    private fun reload(showLoading: Boolean) {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        if (showLoading || _uiState.value.list !is PlantListState.Loaded) {
            _uiState.update { it.copy(list = PlantListState.Loading, isLoadingMore = false) }
        }

        loadJob = viewModelScope.launch {
            val environments = environmentRepository.listAll(active = true)
            if (environments is ApiResult.Success) {
                _uiState.update { state ->
                    // O ambiente filtrado pode ter sido excluído ou arquivado em outra tela: volta para "Todas".
                    val filter = state.filter
                    val stillThere = filter !is PlantsFilter.ByEnvironment || environments.value.any { it.id == filter.environmentId }
                    state.copy(environments = environments.value, filter = if (stillThere) filter else PlantsFilter.All)
                }
            }

            val result = fetchPage(_uiState.value, page = 1)
            _uiState.update { state ->
                state.copy(
                    isLoadingMore = false,
                    list = when (result) {
                        is ApiResult.Success -> PlantListState.Loaded(
                            plants = result.value.plants,
                            total = result.value.total,
                            page = result.value.page,
                            hasMore = result.value.hasMore,
                        )
                        is ApiResult.Failure -> state.list as? PlantListState.Loaded
                            ?: PlantListState.Failed(result.error.toUiText())
                    },
                )
            }
        }
    }

    private suspend fun fetchPage(state: PlantsUiState, page: Int) = plantRepository.list(
        query = state.query,
        environmentId = (state.filter as? PlantsFilter.ByEnvironment)?.environmentId,
        active = state.filter != PlantsFilter.Archived,
        page = page,
    )
}
