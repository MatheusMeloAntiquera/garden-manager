package com.matheusantiquera.gardenmanager.feature.maintenance.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.core.ui.withMinimumDuration
import com.matheusantiquera.gardenmanager.data.maintenance.LogInput
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceType
import com.matheusantiquera.gardenmanager.data.plant.PlantRepository
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
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

data class LogFormUiState(
    /** Editando uma execução existente. */
    val isEditing: Boolean,
    /** Concluindo um agendamento: o tipo vem dele e fica fixo. */
    val isFromSchedule: Boolean,
    /** Ao editar, a execução nasceu de um agendamento (informativo, não muda). */
    val createdFromSchedule: Boolean = false,
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
    val dateTimeError: UiText? = null,
    val notesError: UiText? = null,
    /** Erro geral ao salvar. */
    val error: UiText? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class LogFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val plantRepository: PlantRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val clock: Clock,
) : ViewModel() {

    private val plantId: String = checkNotNull(savedStateHandle[ARG_PLANT_ID]) { "LogFormRoute sem plantId" }
    private val logId: String? = savedStateHandle[ARG_ID]
    private val scheduleId: String? = savedStateHandle[ARG_SCHEDULE_ID]

    private val _uiState = MutableStateFlow(
        now().let {
            LogFormUiState(isEditing = logId != null, isFromSchedule = scheduleId != null, date = it.toLocalDate(), time = it.toLocalTime())
        },
    )
    val uiState: StateFlow<LogFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<MaintenanceFormEvent>(Channel.BUFFERED)
    val events: Flow<MaintenanceFormEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** O instante atual sem segundos: a API recusa data no futuro e o relógio dela pode estar um pouco atrás. */
    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val types = async { maintenanceRepository.types() }
            val plant = async { plantRepository.get(plantId) }
            val schedule = scheduleId?.let { id -> async { maintenanceRepository.getSchedule(id) } }
            val log = logId?.let { id -> async { maintenanceRepository.getLog(id) } }

            val typesResult = types.await()
            val plantResult = plant.await()
            val scheduleResult = schedule?.await()
            val logResult = log?.await()

            val failure = listOfNotNull(typesResult, plantResult, scheduleResult, logResult)
                .filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) {
                _uiState.update { it.copy(isLoading = false, loadError = failure.error.toUiText()) }
                return@launch
            }

            val loadedSchedule = (scheduleResult as? ApiResult.Success)?.value
            val loadedLog = (logResult as? ApiResult.Success)?.value
            _uiState.update { state ->
                val loaded = state.copy(
                    isLoading = false,
                    plantName = (plantResult as ApiResult.Success).value.displayName,
                    types = (typesResult as ApiResult.Success).value,
                )
                when {
                    // Concluir: tipo e observações do agendamento; data e hora são agora.
                    loadedSchedule != null -> loaded.copy(typeId = loadedSchedule.typeId, notes = loadedSchedule.notes.orEmpty())
                    loadedLog != null -> loaded.copy(
                        typeId = loadedLog.typeId,
                        date = loadedLog.performedAt.toLocalDate(),
                        time = loadedLog.performedAt.toLocalTime(),
                        notes = loadedLog.notes.orEmpty(),
                        createdFromSchedule = loadedLog.fromSchedule,
                    )
                    else -> loaded
                }
            }
        }
    }

    fun onTypeChange(typeId: String) = _uiState.update { it.copy(typeId = typeId, typeError = null, error = null) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date, dateTimeError = null, error = null) }

    fun onTimeChange(time: LocalTime) = _uiState.update { it.copy(time = time, dateTimeError = null, error = null) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value, notesError = null, error = null) }

    fun onSave() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading) return

        val typeId = current.typeId
        val performedAt = current.date.atTime(current.time)
        val typeError = if (typeId == null) UiText.Resource(R.string.validation_maintenance_type_required) else null
        val dateTimeError = if (MaintenanceFormValidator.isInFuture(performedAt, now())) {
            UiText.Resource(R.string.validation_maintenance_performed_in_future)
        } else {
            null
        }
        val notesError = if (MaintenanceFormValidator.isNotesTooLong(current.notes)) UiText.Resource(R.string.validation_notes_too_long) else null
        if (typeId == null || dateTimeError != null || notesError != null) {
            _uiState.update { it.copy(typeError = typeError, dateTimeError = dateTimeError, notesError = notesError) }
            return
        }

        val input = LogInput(
            plantId = plantId,
            typeId = typeId,
            performedAt = performedAt,
            notes = MaintenanceFormValidator.normalize(current.notes),
            scheduleId = scheduleId,
        )
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = withMinimumDuration {
                if (logId == null) maintenanceRepository.createLog(input) else maintenanceRepository.updateLog(logId, input)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(MaintenanceFormEvent.Done(if (logId == null) MaintenanceResult.LogCreated else MaintenanceResult.LogUpdated))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error.toUiText()) }
            }
        }
    }

    companion object {
        /** Nomes dos argumentos em [com.matheusantiquera.gardenmanager.navigation.LogFormRoute]. */
        const val ARG_PLANT_ID = "plantId"
        const val ARG_ID = "id"
        const val ARG_SCHEDULE_ID = "scheduleId"
    }
}
