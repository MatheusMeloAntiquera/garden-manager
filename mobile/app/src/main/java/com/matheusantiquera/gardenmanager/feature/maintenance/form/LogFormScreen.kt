package com.matheusantiquera.gardenmanager.feature.maintenance.form

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTextField
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceResult

/**
 * Registro de uma manutenção feita: avulso (Registrar), a partir de um agendamento (Concluir) ou edição de
 * uma execução. [onDone] é chamado depois de salvar, com o resultado.
 */
@Composable
fun LogFormScreen(
    onClose: () -> Unit,
    onDone: (MaintenanceResult) -> Unit,
    viewModel: LogFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MaintenanceFormEvent.Done -> onDone(event.result)
            }
        }
    }

    MaintenanceFormLayout(
        title = stringResource(if (state.isEditing) R.string.log_form_edit_title else R.string.log_form_new_title),
        isLoading = state.isLoading,
        loadError = state.loadError,
        loadErrorTitle = stringResource(R.string.maintenance_form_load_error),
        isSaving = state.isSaving,
        onClose = onClose,
        onRetryLoad = viewModel::load,
        onSave = viewModel::onSave,
        error = state.error,
    ) {
        PlantHeader(name = state.plantName)

        if (state.createdFromSchedule) {
            Text(
                text = stringResource(R.string.log_form_from_schedule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TypeField(
            types = state.types,
            selectedId = state.typeId,
            onChange = viewModel::onTypeChange,
            enabled = !state.isSaving,
            locked = state.isFromSchedule,
            errorText = state.typeError?.asString(),
        )

        DateTimeFields(
            date = state.date,
            time = state.time,
            onDateChange = viewModel::onDateChange,
            onTimeChange = viewModel::onTimeChange,
            dateLabel = stringResource(R.string.log_form_date),
            timeLabel = stringResource(R.string.log_form_time),
            enabled = !state.isSaving,
            errorText = state.dateTimeError?.asString(),
        )

        GardenTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChange,
            label = stringResource(R.string.field_notes),
            placeholder = stringResource(R.string.field_maintenance_notes_placeholder),
            errorText = state.notesError?.asString(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            enabled = !state.isSaving,
            singleLine = false,
            minLines = 3,
        )
    }
}
