package com.matheusantiquera.gardenmanager.feature.environment.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.BannerKind
import com.matheusantiquera.gardenmanager.core.designsystem.ConfirmDeleteDialog
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTextField
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTheme
import com.matheusantiquera.gardenmanager.core.designsystem.LoadErrorState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadingState
import com.matheusantiquera.gardenmanager.core.designsystem.MessageBanner
import com.matheusantiquera.gardenmanager.core.designsystem.PrimaryButton
import com.matheusantiquera.gardenmanager.core.designsystem.ProgressDialog
import com.matheusantiquera.gardenmanager.core.designsystem.SecondaryButton
import com.matheusantiquera.gardenmanager.core.designsystem.SecondaryTopBar
import com.matheusantiquera.gardenmanager.core.designsystem.SwitchRow
import com.matheusantiquera.gardenmanager.core.ui.asString

/** Criação e edição de ambiente. [onDone] é chamado depois de salvar ou excluir, com o resultado. */
@Composable
fun EnvironmentFormScreen(
    onClose: () -> Unit,
    onDone: (EnvironmentFormResult) -> Unit,
    viewModel: EnvironmentFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EnvironmentFormEvent.Done -> onDone(event.result)
            }
        }
    }

    EnvironmentFormContent(
        state = state,
        onClose = onClose,
        onRetryLoad = viewModel::load,
        onNameChange = viewModel::onNameChange,
        onNotesChange = viewModel::onNotesChange,
        onActiveChange = viewModel::onActiveChange,
        onSave = viewModel::onSave,
        onDeleteClick = viewModel::onDeleteClick,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onDeleteDismiss = viewModel::onDeleteDismiss,
    )
}

@Composable
internal fun EnvironmentFormContent(
    state: EnvironmentFormUiState,
    onClose: () -> Unit,
    onRetryLoad: () -> Unit,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
) {
    // Sem a barra inferior, a tela cuida das barras do sistema e do teclado.
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        SecondaryTopBar(
            title = stringResource(if (state.isEditing) R.string.environment_form_edit_title else R.string.environment_form_new_title),
            onNavigationClick = onClose,
        )

        when {
            state.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
            state.loadError != null -> LoadErrorState(
                title = stringResource(R.string.environment_load_error),
                message = state.loadError.asString(),
                onRetry = onRetryLoad,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> FormFields(
                state = state,
                onNameChange = onNameChange,
                onNotesChange = onNotesChange,
                onActiveChange = onActiveChange,
                onCancel = onClose,
                onSave = onSave,
                onDeleteClick = onDeleteClick,
            )
        }
    }

    when {
        state.isSaving -> ProgressDialog(text = stringResource(R.string.environment_saving))
        // Ao confirmar, a confirmação dá lugar à janela "Excluindo…".
        state.isDeleting -> ProgressDialog(text = stringResource(R.string.environment_deleting))
        state.showDeleteConfirmation -> ConfirmDeleteDialog(
            title = stringResource(R.string.environment_delete_title),
            message = deleteMessage(name = state.savedName, plantCount = state.plantCount),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
}

@Composable
private fun FormFields(
    state: EnvironmentFormUiState,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val busy = state.isSaving || state.isDeleting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        state.error?.let { MessageBanner(text = it.asString(), kind = BannerKind.Error) }

        GardenTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.field_environment_name),
            placeholder = stringResource(R.string.field_environment_name_placeholder),
            errorText = state.nameError?.asString(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            enabled = !busy,
        )

        GardenTextField(
            value = state.notes,
            onValueChange = onNotesChange,
            label = stringResource(R.string.field_notes),
            placeholder = stringResource(R.string.field_environment_notes_placeholder),
            errorText = state.notesError?.asString(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            enabled = !busy,
            singleLine = false,
            minLines = 3,
        )

        if (state.isEditing) {
            SwitchRow(
                title = stringResource(R.string.environment_active),
                description = stringResource(R.string.environment_active_description),
                checked = state.active,
                onCheckedChange = onActiveChange,
                enabled = !busy,
            )

            PlantCountInfo(plantCount = state.plantCount)
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Cancelar faz o mesmo que o X do cabeçalho: fecha sem salvar.
                SecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                )
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    loading = state.isSaving,
                    enabled = !state.isDeleting,
                )
            }
            if (state.isEditing) {
                TextButton(
                    onClick = onDeleteClick,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.environment_delete),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlantCountInfo(plantCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_leaf),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (plantCount == 0) {
                    stringResource(R.string.environment_form_no_plants)
                } else {
                    pluralStringResource(R.plurals.environment_form_plant_count, plantCount, plantCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Texto do diálogo de exclusão. As strings trazem `<b>` escapado, interpretado por [AnnotatedString.fromHtml]. */
@Composable
private fun deleteMessage(name: String, plantCount: Int): AnnotatedString {
    val safeName = name.escapeHtml()
    val html = if (plantCount == 0) {
        stringResource(R.string.environment_delete_message_no_plants, safeName)
    } else {
        pluralStringResource(R.plurals.environment_delete_message, plantCount, safeName, plantCount)
    }
    return AnnotatedString.fromHtml(html)
}

// O nome é digitado pelo usuário e vai dentro de HTML: escapa para não virar marcação.
private fun String.escapeHtml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun EnvironmentFormEditPreview() {
    GardenTheme(darkTheme = false) {
        EnvironmentFormContent(
            state = EnvironmentFormUiState(
                isEditing = true,
                name = "Sala de estar",
                notes = "Janela leste, luz da manhã",
                plantCount = 5,
                savedName = "Sala de estar",
            ),
            onClose = {},
            onRetryLoad = {},
            onNameChange = {},
            onNotesChange = {},
            onActiveChange = {},
            onSave = {},
            onDeleteClick = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
        )
    }
}
