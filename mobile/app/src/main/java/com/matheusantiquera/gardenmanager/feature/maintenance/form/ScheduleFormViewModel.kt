package com.matheusantiquera.gardenmanager.feature.maintenance.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.core.ui.withMinimumDuration
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceType
import com.matheusantiquera.gardenmanager.data.maintenance.ScheduleInput
import com.matheusantiquera.gardenmanager.data.plant.PlantRepository
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
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

data class ScheduleFormUiState(
    /** Editando um agendamento existente; falso ao criar um novo. */
    val isEditing: Boolean,
    /** Carregando a planta, os tipos e, ao editar, o agendamento. */
    val isLoading: Boolean = true,
    val loadError: UiText? = null,
    /** A planta é fixa: o formulário só mostra o nome. */
    val plantName: String = "",
    val types: List<MaintenanceType> = emptyList(),
    val typeId: String? = null,
    val date: LocalDate,
    val time: LocalTime,
    val notes: String = "",
    val typeError: UiText? = null,
    val notesError: UiText? = null,
    /** Erro geral ao salvar. */
    val error: UiText? = null,
    val isSaving: Boolean = false,
)

sealed interface MaintenanceFormEvent {
    /** O formulário terminou: a tela fecha e a de destino mostra a mensagem do [result]. */
    data class Done(val result: MaintenanceResult) : MaintenanceFormEvent
}

@HiltViewModel
class ScheduleFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plantRepository: PlantRepository,
    private val maintenanceRepository: MaintenanceRepository,
    clock: Clock,
) : ViewModel() {

    private val plantId: String = checkNotNull(savedStateHandle[ARG_PLANT_ID]) { "ScheduleFormRoute sem plantId" }
    private val scheduleId: String? = savedStateHandle[ARG_ID]

    private val _uiState = MutableStateFlow(
        MaintenanceFormValidator.defaultDueAt(LocalDate.now(clock)).let {
            ScheduleFormUiState(isEditing = scheduleId != null, date = it.toLocalDate(), time = it.toLocalTime())
        },
    )
    val uiState: StateFlow<ScheduleFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<MaintenanceFormEvent>(Channel.BUFFERED)
    val events: Flow<MaintenanceFormEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val types = async { maintenanceRepository.types() }
            val plant = async { plantRepository.get(plantId) }
            val schedule = scheduleId?.let { id -> async { maintenanceRepository.getSchedule(id) } }

            val typesResult = types.await()
            val plantResult = plant.await()
            val scheduleResult = schedule?.await()

            val failure = listOfNotNull(typesResult, plantResult, scheduleResult).filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) {
                _uiState.update { it.copy(isLoading = false, loadError = failure.error.toUiText()) }
                return@launch
            }

            val loadedSchedule = (scheduleResult as? ApiResult.Success)?.value
            _uiState.update { state ->
                val loaded = state.copy(
                    isLoading = false,
                    plantName = (plantResult as ApiResult.Success).value.displayName,
                    types = (typesResult as ApiResult.Success).value,
                )
                if (loadedSchedule == null) {
                    loaded
                } else {
                    loaded.copy(
                        typeId = loadedSchedule.typeId,
                        date = loadedSchedule.dueAt.toLocalDate(),
                        time = loadedSchedule.dueAt.toLocalTime(),
                        notes = loadedSchedule.notes.orEmpty(),
                    )
                }
            }
        }
    }

    fun onTypeChange(typeId: String) = _uiState.update { it.copy(typeId = typeId, typeError = null, error = null) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date, error = null) }

    fun onTimeChange(time: LocalTime) = _uiState.update { it.copy(time = time, error = null) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value, notesError = null, error = null) }

    fun onSave() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading) return

        val typeId = current.typeId
        val typeError = if (typeId == null) UiText.Resource(R.string.validation_maintenance_type_required) else null
        val notesError = if (MaintenanceFormValidator.isNotesTooLong(current.notes)) UiText.Resource(R.string.validation_notes_too_long) else null
        if (typeId == null || notesError != null) {
            _uiState.update { it.copy(typeError = typeError, notesError = notesError) }
            return
        }

        val input = ScheduleInput(
            plantId = plantId,
            typeId = typeId,
            dueAt = current.date.atTime(current.time),
            notes = MaintenanceFormValidator.normalize(current.notes),
        )
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = withMinimumDuration {
                if (scheduleId == null) maintenanceRepository.createSchedule(input) else maintenanceRepository.updateSchedule(scheduleId, input)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(
                        MaintenanceFormEvent.Done(if (scheduleId == null) MaintenanceResult.ScheduleCreated else MaintenanceResult.ScheduleUpdated),
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error.toUiText()) }
            }
        }
    }

    companion object {
        /** Nomes dos argumentos em [com.matheusantiquera.gardenmanager.navigation.ScheduleFormRoute]. */
        const val ARG_PLANT_ID = "plantId"
        const val ARG_ID = "id"
    }
}
