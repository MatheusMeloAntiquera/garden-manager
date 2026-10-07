package com.matheusantiquera.gardenmanager.feature.maintenance

import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.core.ui.withMinimumDuration
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Agendamento ou execução sobre o qual a sheet de ações foi aberta. */
sealed interface MaintenanceTarget {
    val typeName: String
    val plantName: String

    data class OfSchedule(val schedule: MaintenanceSchedule) : MaintenanceTarget {
        override val typeName: String get() = schedule.typeName
        override val plantName: String get() = schedule.plantName
    }

    data class OfLog(val log: MaintenanceLog) : MaintenanceTarget {
        override val typeName: String get() = log.typeName
        override val plantName: String get() = log.plantName
    }
}

data class MaintenanceActionsState(
    /** Agendamento da sheet de ações aberta, se houver. */
    val sheet: MaintenanceSchedule? = null,
    /** Item cuja exclusão está sendo confirmada. */
    val confirmDelete: MaintenanceTarget? = null,
    val isDeleting: Boolean = false,
    /** Falha ao excluir, mostrada uma vez pela tela. */
    val error: UiText? = null,
    /** Exclusão concluída, para a tela mostrar a mensagem uma vez. */
    val deleted: MaintenanceResult? = null,
)

/**
 * Estado e ações da sheet de um agendamento (concluir, editar e excluir) e a exclusão de uma execução, compartilhados pela
 * Agenda, pelo detalhe da planta e pelo detalhe do registro. Concluir e editar levam a outras telas, então ficam com quem usa; a
 * exclusão acontece aqui e chama [onDeleted] para a lista se recarregar.
 */
class MaintenanceActions(
    private val repository: MaintenanceRepository,
    private val scope: CoroutineScope,
    private val onDeleted: () -> Unit,
) {
    private val _state = MutableStateFlow(MaintenanceActionsState())
    val state: StateFlow<MaintenanceActionsState> = _state.asStateFlow()

    fun openSheet(schedule: MaintenanceSchedule) = _state.update { it.copy(sheet = schedule) }

    fun dismissSheet() = _state.update { it.copy(sheet = null) }

    /** "Excluir" na sheet de um agendamento: fecha a sheet e pede a confirmação. */
    fun askDelete() = _state.update { it.copy(sheet = null, confirmDelete = it.sheet?.let(MaintenanceTarget::OfSchedule)) }

    /** Pede a confirmação de exclusão de [target] direto, sem sheet (o botão Excluir do detalhe do registro). */
    fun askDelete(target: MaintenanceTarget) = _state.update { it.copy(confirmDelete = target) }

    fun dismissDelete() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(confirmDelete = null) }
    }

    fun confirmDelete() {
        val current = _state.value
        val target = current.confirmDelete ?: return
        if (current.isDeleting) return

        _state.update { it.copy(isDeleting = true) }
        scope.launch {
            val result = withMinimumDuration {
                when (target) {
                    is MaintenanceTarget.OfSchedule -> repository.deleteSchedule(target.schedule.id)
                    is MaintenanceTarget.OfLog -> repository.deleteLog(target.log.id)
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    val deleted = when (target) {
                        is MaintenanceTarget.OfSchedule -> MaintenanceResult.ScheduleDeleted
                        is MaintenanceTarget.OfLog -> MaintenanceResult.LogDeleted
                    }
                    _state.update { it.copy(isDeleting = false, confirmDelete = null, deleted = deleted) }
                    onDeleted()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(isDeleting = false, confirmDelete = null, error = result.error.toUiText())
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearDeleted() = _state.update { it.copy(deleted = null) }
}
