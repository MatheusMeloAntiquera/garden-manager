package com.matheusantiquera.gardenmanager.feature.plant.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import com.matheusantiquera.gardenmanager.data.maintenance.PlantMaintenance
import com.matheusantiquera.gardenmanager.data.plant.Plant
import com.matheusantiquera.gardenmanager.data.plant.PlantRepository
import com.matheusantiquera.gardenmanager.data.species.Species
import com.matheusantiquera.gardenmanager.data.species.SpeciesRepository
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlantDetailUiState {
    data object Loading : PlantDetailUiState

    /** [species] traz família e categoria; fica nulo se a planta não tem espécie ou se a busca falhou. */
    data class Loaded(val plant: Plant, val species: Species?, val maintenance: PlantMaintenance) : PlantDetailUiState

    data class Failed(val message: UiText) : PlantDetailUiState
}

@HiltViewModel
class PlantDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plantRepository: PlantRepository,
    private val speciesRepository: SpeciesRepository,
    private val maintenanceRepository: MaintenanceRepository,
) : ViewModel() {

    private val plantId: String = checkNotNull(savedStateHandle[ARG_PLANT_ID]) { "PlantDetailRoute sem id" }

    private val _uiState = MutableStateFlow<PlantDetailUiState>(PlantDetailUiState.Loading)
    val uiState: StateFlow<PlantDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Sheet de ações e exclusão de um agendamento; excluir recarrega o detalhe. */
    val actions = MaintenanceActions(maintenanceRepository, viewModelScope, onDeleted = { load() })

    fun onScheduleClick(schedule: MaintenanceSchedule) = actions.openSheet(schedule)

    /**
     * Carrega (ou recarrega) o detalhe. A tela chama ao voltar a ficar visível, por exemplo depois de
     * editar; com o detalhe já na tela, a recarga é silenciosa e uma falha mantém o que já estava.
     */
    fun load() {
        loadJob?.cancel()
        if (_uiState.value !is PlantDetailUiState.Loaded) _uiState.value = PlantDetailUiState.Loading

        loadJob = viewModelScope.launch {
            val maintenance = async { maintenanceRepository.forPlant(plantId) }
            val plantResult = plantRepository.get(plantId)
            val speciesResult = (plantResult as? ApiResult.Success)?.value?.species?.let { speciesRepository.get(it.id) }
            val maintenanceResult = maintenance.await()

            _uiState.update { current ->
                when {
                    plantResult is ApiResult.Failure -> current.keepOr(plantResult.error.toUiText())
                    maintenanceResult is ApiResult.Failure -> current.keepOr(maintenanceResult.error.toUiText())
                    else -> PlantDetailUiState.Loaded(
                        plant = (plantResult as ApiResult.Success).value,
                        species = (speciesResult as? ApiResult.Success)?.value,
                        maintenance = (maintenanceResult as ApiResult.Success).value,
                    )
                }
            }
        }
    }

    private fun PlantDetailUiState.keepOr(message: UiText): PlantDetailUiState =
        this as? PlantDetailUiState.Loaded ?: PlantDetailUiState.Failed(message)

    companion object {
        /** Nome do argumento em [com.matheusantiquera.gardenmanager.navigation.PlantDetailRoute]. */
        const val ARG_PLANT_ID = "id"
    }
}
