package com.matheusantiquera.gardenmanager.feature.plant.species

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.species.Species
import com.matheusantiquera.gardenmanager.data.species.SpeciesRepository
import com.matheusantiquera.gardenmanager.feature.plant.list.SEARCH_DEBOUNCE_MILLIS
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

sealed interface SpeciesResultsState {
    data object Loading : SpeciesResultsState
    data class Loaded(val species: List<Species>, val page: Int, val hasMore: Boolean) : SpeciesResultsState
    data class Failed(val message: UiText) : SpeciesResultsState
}

data class SpeciesPickerUiState(
    val query: String = "",
    /** Espécie que a planta já tem, marcada na lista. */
    val selectedId: String? = null,
    val results: SpeciesResultsState = SpeciesResultsState.Loading,
    val isLoadingMore: Boolean = false,
)

/** Busca no catálogo de espécies para o formulário de planta. */
@OptIn(FlowPreview::class)
@HiltViewModel
class SpeciesPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpeciesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeciesPickerUiState(selectedId = savedStateHandle[ARG_SELECTED_ID]))
    val uiState: StateFlow<SpeciesPickerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        search()
        // Refaz a busca depois de uma pausa na digitação. drop(1) ignora o valor inicial, já buscado.
        viewModelScope.launch {
            _uiState.map { it.query.trim() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .collect { search() }
        }
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun search() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.update { it.copy(results = SpeciesResultsState.Loading, isLoadingMore = false) }
        searchJob = viewModelScope.launch {
            val result = repository.search(_uiState.value.query, page = 1)
            _uiState.update {
                it.copy(
                    results = when (result) {
                        is ApiResult.Success -> SpeciesResultsState.Loaded(result.value.species, result.value.page, result.value.hasMore)
                        is ApiResult.Failure -> SpeciesResultsState.Failed(result.error.toUiText())
                    },
                )
            }
        }
    }

    /** Carrega a próxima página, quando houver. A tela chama ao chegar perto do fim da lista. */
    fun loadMore() {
        val current = _uiState.value
        val loaded = current.results as? SpeciesResultsState.Loaded ?: return
        if (!loaded.hasMore || current.isLoadingMore || searchJob?.isActive == true) return

        _uiState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            val result = repository.search(current.query, page = loaded.page + 1)
            _uiState.update { state ->
                val now = state.results as? SpeciesResultsState.Loaded
                if (now != null && result is ApiResult.Success) {
                    state.copy(
                        isLoadingMore = false,
                        results = SpeciesResultsState.Loaded(now.species + result.value.species, result.value.page, result.value.hasMore),
                    )
                } else {
                    // Falha ao carregar mais: mantém o que está na tela; rolar de novo tenta outra vez.
                    state.copy(isLoadingMore = false)
                }
            }
        }
    }

    companion object {
        /** Nome do argumento em [com.matheusantiquera.gardenmanager.navigation.SpeciesPickerRoute]. */
        const val ARG_SELECTED_ID = "selectedId"
    }
}
