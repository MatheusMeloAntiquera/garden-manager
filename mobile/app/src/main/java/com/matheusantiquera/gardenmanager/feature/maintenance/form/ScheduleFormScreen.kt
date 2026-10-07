package com.matheusantiquera.gardenmanager.feature.maintenance.form

import androidx.compose.foundation.text.KeyboardOptions
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

/** Criação e edição de agendamento de uma planta. [onDone] é chamado depois de salvar, com o resultado. */
@Composable
fun ScheduleFormScreen(
    onClose: () -> Unit,
    onDone: (MaintenanceResult) -> Unit,
    viewModel: ScheduleFormViewModel = hiltViewModel(),
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
        title = stringResource(if (state.isEditing) R.string.schedule_form_edit_title else R.string.schedule_form_new_title),
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

        TypeField(
            types = state.types,
            selectedId = state.typeId,
            onChange = viewModel::onTypeChange,
            enabled = !state.isSaving,
            locked = false,
            errorText = state.typeError?.asString(),
        )

        DateTimeFields(
            date = state.date,
            time = state.time,
            onDateChange = viewModel::onDateChange,
            onTimeChange = viewModel::onTimeChange,
            dateLabel = stringResource(R.string.schedule_form_date),
            timeLabel = stringResource(R.string.schedule_form_time),
            enabled = !state.isSaving,
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
