package com.matheusantiquera.gardenmanager.feature.environment.list

import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.environment.Environment

/** Estado da lista de ambientes, compartilhado pela aba e pela tela de arquivados. */
sealed interface EnvironmentListState {
    data object Loading : EnvironmentListState
    data class Loaded(val environments: List<Environment>) : EnvironmentListState
    data class Failed(val message: UiText) : EnvironmentListState
}
