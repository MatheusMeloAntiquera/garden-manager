package com.matheusantiquera.gardenmanager.feature.schedule

import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenancePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PagedState<out T> {
    data object Loading : PagedState<Nothing>

    data class Loaded<T>(
        val items: List<T>,
        val total: Int,
        val page: Int,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false,
    ) : PagedState<T>

    data class Failed(val message: UiText) : PagedState<Nothing>
}

/**
 * Lista carregada aos poucos: [reload] busca a primeira página e [loadMore] a seguinte. Com a lista já
 * na tela, a recarga silenciosa mantém o que está nela se falhar.
 */
class PagedList<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (page: Int) -> ApiResult<MaintenancePage<T>>,
) {
    private val _state = MutableStateFlow<PagedState<T>>(PagedState.Loading)
    val state: StateFlow<PagedState<T>> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    /** Recarrega do começo. [showLoading] troca a lista pelo indicador de carregamento antes de buscar. */
    fun reload(showLoading: Boolean) {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        if (showLoading || _state.value !is PagedState.Loaded) _state.value = PagedState.Loading

        loadJob = scope.launch {
            val result = fetch(1)
            _state.update { current ->
                when (result) {
                    is ApiResult.Success -> PagedState.Loaded(
                        items = result.value.items,
                        total = result.value.total,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                    )
                    is ApiResult.Failure -> (current as? PagedState.Loaded<T>)?.copy(isLoadingMore = false)
                        ?: PagedState.Failed(result.error.toUiText())
                }
            }
        }
    }

    /** Carrega a próxima página, quando houver. A tela chama ao chegar perto do fim da lista. */
    fun loadMore() {
        val current = _state.value as? PagedState.Loaded<T> ?: return
        if (!current.hasMore || current.isLoadingMore || loadJob?.isActive == true) return

        _state.value = current.copy(isLoadingMore = true)
        loadMoreJob = scope.launch {
            val result = fetch(current.page + 1)
            _state.update { state ->
                val loaded = state as? PagedState.Loaded<T> ?: return@update state
                when (result) {
                    is ApiResult.Success -> PagedState.Loaded(
                        items = loaded.items + result.value.items,
                        total = result.value.total,
                        page = result.value.page,
                        hasMore = result.value.hasMore,
                    )
                    // Falha ao carregar mais: mantém o que está na tela; rolar de novo tenta outra vez.
                    is ApiResult.Failure -> loaded.copy(isLoadingMore = false)
                }
            }
        }
    }
}
