package com.matheusantiquera.gardenmanager.feature.maintenance

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.ui.ShowOneShotMessage

/** O que aconteceu com um agendamento ou uma execução. A tela de destino mostra a mensagem correspondente. */
enum class MaintenanceResult {
    ScheduleCreated,
    ScheduleUpdated,
    ScheduleDeleted,
    LogCreated,
    LogUpdated,
    LogDeleted,
}

/** Chave do `savedStateHandle` da tela de destino onde os formulários de manutenção deixam o [MaintenanceResult]. */
const val MAINTENANCE_RESULT_KEY = "maintenance_result"

/** Mostra a mensagem do [result] no [snackbarHostState] e chama [onShown] para limpá-lo. */
@Composable
fun ShowMaintenanceResult(
    result: MaintenanceResult?,
    onShown: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val message = when (result) {
        MaintenanceResult.ScheduleCreated -> stringResource(R.string.maintenance_schedule_created)
        MaintenanceResult.ScheduleUpdated -> stringResource(R.string.maintenance_schedule_updated)
        MaintenanceResult.ScheduleDeleted -> stringResource(R.string.maintenance_schedule_deleted)
        MaintenanceResult.LogCreated -> stringResource(R.string.maintenance_log_created)
        MaintenanceResult.LogUpdated -> stringResource(R.string.maintenance_log_updated)
        MaintenanceResult.LogDeleted -> stringResource(R.string.maintenance_log_deleted)
        null -> null
    }
    ShowOneShotMessage(message = message, onShown = onShown, snackbarHostState = snackbarHostState)
}
