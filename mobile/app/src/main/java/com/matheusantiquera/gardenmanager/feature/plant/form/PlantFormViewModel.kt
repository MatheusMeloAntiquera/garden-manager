package com.matheusantiquera.gardenmanager.feature.plant.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.core.ui.withMinimumDuration
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.plant.PlantInput
import com.matheusantiquera.gardenmanager.data.plant.PlantRepository
import com.matheusantiquera.gardenmanager.data.plant.PlantSpecies
import com.matheusantiquera.gardenmanager.feature.plant.PlantFormResult
import com.matheusantiquera.gardenmanager.feature.plant.SpeciesPick
import com.matheusantiquera.gardenmanager.feature.plant.toPlantSpecies
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Uma opção do seletor de ambiente. [archived] marca o ambiente arquivado em que a planta já está. */
data class EnvironmentOption(val id: String, val name: String, val archived: Boolean = false)

data class PlantFormUiState(
    /** Editando uma planta existente; falso ao criar uma nova. */
    val isEditing: Boolean,
    /** Carregando a planta e os ambientes. */
    val isLoading: Boolean = true,
    val loadError: UiText? = null,
    val nickname: String = "",
    val species: PlantSpecies? = null,
    val environmentId: String? = null,
    val notes: String = "",
    val active: Boolean = true,
    val environments: List<EnvironmentOption> = emptyList(),
    /** Nome salvo, usado no diálogo de exclusão mesmo que o apelido tenha sido editado. */
    val savedName: String = "",
    /** Totais de manutenção da planta, para o diálogo de exclusão. */
    val scheduleTotal: Int = 0,
    val logTotal: Int = 0,
    val nicknameError: UiText? = null,
    val notesError: UiText? = null,
    /** Faltam apelido e espécie: a dica "Informe um apelido ou uma espécie." vira erro. */
    val missingNicknameAndSpecies: Boolean = false,
    /** Erro geral ao salvar ou excluir. */
    val error: UiText? = null,
    val isSaving: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
) {
    val selectedEnvironment: EnvironmentOption? get() = environments.firstOrNull { it.id == environmentId }
}

sealed interface PlantFormEvent {
    /** O formulário terminou: a tela fecha e a de destino mostra a mensagem do [result]. */
    data class Done(val result: PlantFormResult) : PlantFormEvent
}

@HiltViewModel
class PlantFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plantRepository: PlantRepository,
    private val environmentRepository: EnvironmentRepository,
    private val maintenanceRepository: MaintenanceRepository,
) : ViewModel() {

    private val plantId: String? = savedStateHandle[ARG_PLANT_ID]
    private val initialEnvironmentId: String? = savedStateHandle[ARG_ENVIRONMENT_ID]

    private val _uiState = MutableStateFlow(PlantFormUiState(isEditing = plantId != null))
    val uiState: StateFlow<PlantFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlantFormEvent>(Channel.BUFFERED)
    val events: Flow<PlantFormEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Escolha feita no seletor de espécie, inclusive "Sem espécie". */
    fun onSpeciesPicked(pick: SpeciesPick) = onSpeciesChange(pick.species?.toPlantSpecies())

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val environments = async { environmentRepository.listAll(active = true) }
            val plant = plantId?.let { id -> async { plantRepository.get(id) } }
            val maintenance = plantId?.let { id -> async { maintenanceRepository.forPlant(id) } }

            val environmentsResult = environments.await()
            val plantResult = plant?.await()
            val maintenanceResult = maintenance?.await()

            val failure = listOfNotNull(environmentsResult, plantResult, maintenanceResult).filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) {
                _uiState.update { it.copy(isLoading = false, loadError = failure.error.toUiText()) }
                return@launch
            }

            val activeOptions = (environmentsResult as ApiResult.Success).value.map { EnvironmentOption(it.id, it.name) }
            val loadedPlant = (plantResult as? ApiResult.Success)?.value
            val loadedMaintenance = (maintenanceResult as? ApiResult.Success)?.value

            // A planta pode estar num ambiente arquivado, fora da lista de ativos: ele continua como opção
            // para não ser trocado sem querer ao salvar.
            val currentEnvironment = loadedPlant?.environment
            val options = if (currentEnvironment != null && activeOptions.none { it.id == currentEnvironment.id }) {
                activeOptions + EnvironmentOption(currentEnvironment.id, currentEnvironment.name, archived = true)
            } else {
                activeOptions
            }

            _uiState.update { state ->
                if (loadedPlant == null) {
                    // Criação: o ambiente vem do filtro da lista, se ele ainda for uma opção válida.
                    state.copy(
                        isLoading = false,
                        environments = options,
                        environmentId = initialEnvironmentId?.takeIf { id -> options.any { it.id == id } },
                    )
                } else {
                    state.copy(
                        isLoading = false,
                        environments = options,
                        nickname = loadedPlant.nickname.orEmpty(),
                        species = loadedPlant.species,
                        environmentId = loadedPlant.environment?.id,
                        notes = loadedPlant.notes.orEmpty(),
                        active = loadedPlant.active,
                        savedName = loadedPlant.displayName,
                        scheduleTotal = loadedMaintenance?.scheduleTotal ?: 0,
                        logTotal = loadedMaintenance?.logTotal ?: 0,
                    )
                }
            }
        }
    }

    fun onNicknameChange(value: String) =
        _uiState.update { it.copy(nickname = value, nicknameError = null, missingNicknameAndSpecies = false, error = null) }

    fun onSpeciesChange(species: PlantSpecies?) =
        _uiState.update { it.copy(species = species, missingNicknameAndSpecies = false, error = null) }

    fun onEnvironmentChange(environmentId: String?) = _uiState.update { it.copy(environmentId = environmentId, error = null) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value, notesError = null, error = null) }

    fun onActiveChange(value: Boolean) = _uiState.update { it.copy(active = value, error = null) }

    fun onSave() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading) return

        val missing = PlantFormValidator.isMissingNicknameAndSpecies(current.nickname, current.species?.id)
        val nicknameError = if (PlantFormValidator.isNicknameTooLong(current.nickname)) {
            UiText.Resource(R.string.validation_plant_nickname_too_long)
        } else {
            null
        }
        val notesError = if (PlantFormValidator.isNotesTooLong(current.notes)) UiText.Resource(R.string.validation_notes_too_long) else null
        if (missing || nicknameError != null || notesError != null) {
            _uiState.update { it.copy(missingNicknameAndSpecies = missing, nicknameError = nicknameError, notesError = notesError) }
            return
        }

        val input = PlantInput(
            nickname = PlantFormValidator.normalize(current.nickname),
            speciesId = current.species?.id,
            environmentId = current.environmentId,
            notes = PlantFormValidator.normalize(current.notes),
        )
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = withMinimumDuration {
                if (plantId == null) plantRepository.create(input) else plantRepository.update(plantId, input, current.active)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(PlantFormEvent.Done(if (plantId == null) PlantFormResult.Created else PlantFormResult.Updated))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error.toUiText()) }
            }
        }
    }

    fun onDeleteClick() {
        if (plantId == null || _uiState.value.isLoading) return
        _uiState.update { it.copy(showDeleteConfirmation = true, error = null) }
    }

    fun onDeleteDismiss() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun onDeleteConfirm() {
        val id = plantId ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = withMinimumDuration { plantRepository.delete(id) }) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isDeleting = false, showDeleteConfirmation = false) }
                    _events.send(PlantFormEvent.Done(PlantFormResult.Deleted))
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isDeleting = false, showDeleteConfirmation = false, error = result.error.toUiText())
                }
            }
        }
    }

    companion object {
        /** Nomes dos argumentos em [com.matheusantiquera.gardenmanager.navigation.PlantFormRoute]. */
        const val ARG_PLANT_ID = "id"
        const val ARG_ENVIRONMENT_ID = "environmentId"
    }
}
