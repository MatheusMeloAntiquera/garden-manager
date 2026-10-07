package com.matheusantiquera.gardenmanager.feature.plant.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
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
import com.matheusantiquera.gardenmanager.data.plant.PlantSpecies
import com.matheusantiquera.gardenmanager.feature.plant.PlantFormResult
import com.matheusantiquera.gardenmanager.feature.plant.SpeciesPick

/**
 * Criação e edição de planta. [onPickSpecies] abre o seletor com a espécie atual; a escolha volta em
 * [speciesPick], que a tela aplica e limpa com [onSpeciesPickConsumed]. [onDone] é chamado depois de
 * salvar ou excluir, com o resultado.
 */
@Composable
fun PlantFormScreen(
    speciesPick: SpeciesPick?,
    onSpeciesPickConsumed: () -> Unit,
    onClose: () -> Unit,
    onPickSpecies: (currentSpeciesId: String?) -> Unit,
    onDone: (PlantFormResult) -> Unit,
    viewModel: PlantFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(speciesPick) {
        val pick = speciesPick ?: return@LaunchedEffect
        onSpeciesPickConsumed()
        viewModel.onSpeciesPicked(pick)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlantFormEvent.Done -> onDone(event.result)
            }
        }
    }

    PlantFormContent(
        state = state,
        onClose = onClose,
        onRetryLoad = viewModel::load,
        onNicknameChange = viewModel::onNicknameChange,
        onPickSpecies = { onPickSpecies(state.species?.id) },
        onEnvironmentChange = viewModel::onEnvironmentChange,
        onNotesChange = viewModel::onNotesChange,
        onActiveChange = viewModel::onActiveChange,
        onSave = viewModel::onSave,
        onDeleteClick = viewModel::onDeleteClick,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onDeleteDismiss = viewModel::onDeleteDismiss,
    )
}

@Composable
internal fun PlantFormContent(
    state: PlantFormUiState,
    onClose: () -> Unit,
    onRetryLoad: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onPickSpecies: () -> Unit,
    onEnvironmentChange: (String?) -> Unit,
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
            title = stringResource(if (state.isEditing) R.string.plant_form_edit_title else R.string.plant_form_new_title),
            onNavigationClick = onClose,
        )

        when {
            state.isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
            state.loadError != null -> LoadErrorState(
                title = stringResource(if (state.isEditing) R.string.plant_load_error else R.string.plant_form_load_error),
                message = state.loadError.asString(),
                onRetry = onRetryLoad,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> FormFields(
                state = state,
                onNicknameChange = onNicknameChange,
                onPickSpecies = onPickSpecies,
                onEnvironmentChange = onEnvironmentChange,
                onNotesChange = onNotesChange,
                onActiveChange = onActiveChange,
                onCancel = onClose,
                onSave = onSave,
                onDeleteClick = onDeleteClick,
            )
        }
    }

    when {
        state.isSaving -> ProgressDialog(text = stringResource(R.string.plant_saving))
        // Ao confirmar, a confirmação dá lugar à janela "Excluindo…".
        state.isDeleting -> ProgressDialog(text = stringResource(R.string.plant_deleting))
        state.showDeleteConfirmation -> ConfirmDeleteDialog(
            title = stringResource(R.string.plant_delete_title),
            message = deleteMessage(name = state.savedName, scheduleTotal = state.scheduleTotal, logTotal = state.logTotal),
            note = stringResource(R.string.plant_delete_note),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
}

@Composable
private fun FormFields(
    state: PlantFormUiState,
    onNicknameChange: (String) -> Unit,
    onPickSpecies: () -> Unit,
    onEnvironmentChange: (String?) -> Unit,
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

        NicknameOrSpeciesHint(isError = state.missingNicknameAndSpecies)

        GardenTextField(
            value = state.nickname,
            onValueChange = onNicknameChange,
            label = stringResource(R.string.field_plant_nickname),
            placeholder = stringResource(R.string.field_plant_nickname_placeholder),
            errorText = state.nicknameError?.asString(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            enabled = !busy,
        )

        SpeciesField(species = state.species, onClick = onPickSpecies, enabled = !busy)

        EnvironmentField(state = state, onEnvironmentChange = onEnvironmentChange, enabled = !busy)

        GardenTextField(
            value = state.notes,
            onValueChange = onNotesChange,
            label = stringResource(R.string.field_notes),
            placeholder = stringResource(R.string.field_plant_notes_placeholder),
            errorText = state.notesError?.asString(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            enabled = !busy,
            singleLine = false,
            minLines = 3,
        )

        if (state.isEditing) {
            SwitchRow(
                title = stringResource(R.string.plant_active),
                description = stringResource(R.string.plant_active_description),
                checked = state.active,
                onCheckedChange = onActiveChange,
                enabled = !busy,
            )
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
                        text = stringResource(R.string.plant_delete),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Campo de espécie: mostra o nome popular e o científico e abre a busca no catálogo ao tocar. */
@Composable
private fun SpeciesField(species: PlantSpecies?, onClick: () -> Unit, enabled: Boolean) {
    val label = stringResource(R.string.field_plant_species)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 62.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    if (species == null) {
                        Text(
                            text = stringResource(R.string.field_plant_species_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = species.commonName ?: species.scientificName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (species.commonName != null) {
                            Text(
                                text = species.scientificName,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Seletor de ambiente: "Sem ambiente" e os ambientes ativos (mais o arquivado em que a planta já está). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnvironmentField(state: PlantFormUiState, onEnvironmentChange: (String?) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.field_plant_environment_none)
    val selected = state.selectedEnvironment
    val selectedText = selected?.let { optionLabel(it) } ?: none

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        GardenTextField(
            value = selectedText,
            onValueChange = {},
            label = stringResource(R.string.field_plant_environment),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            trailingContent = {
                Icon(painter = painterResource(R.drawable.ic_chevron_down), contentDescription = null, modifier = Modifier.size(20.dp))
            },
            enabled = enabled,
            readOnly = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(none, style = MaterialTheme.typography.bodyLarge) },
                onClick = {
                    onEnvironmentChange(null)
                    expanded = false
                },
            )
            state.environments.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onEnvironmentChange(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun optionLabel(option: EnvironmentOption): String =
    if (option.archived) stringResource(R.string.field_plant_environment_archived, option.name) else option.name

/** A dica antes do apelido, que vira erro quando se tenta salvar sem apelido e sem espécie. */
@Composable
private fun NicknameOrSpeciesHint(isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(if (isError) R.drawable.ic_alert else R.drawable.ic_info),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Text(text = stringResource(R.string.validation_plant_nickname_or_species), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * Texto do diálogo de exclusão: "“X” será excluída junto com **2 agendamentos e 3 registros do
 * histórico**." O `<b>` vai escapado nas strings e é interpretado por [AnnotatedString.fromHtml].
 */
@Composable
private fun deleteMessage(name: String, scheduleTotal: Int, logTotal: Int): AnnotatedString {
    val parts = listOfNotNull(
        scheduleTotal.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.plant_delete_schedules, it, it) },
        logTotal.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.plant_delete_logs, it, it) },
    )
    val safeName = name.escapeHtml()
    val html = if (parts.isEmpty()) {
        stringResource(R.string.plant_delete_message_nothing, safeName)
    } else {
        stringResource(R.string.plant_delete_message, safeName, parts.joinToString(stringResource(R.string.list_and)))
    }
    return AnnotatedString.fromHtml(html)
}

// O nome é digitado pelo usuário e vai dentro de HTML: escapa para não virar marcação.
private fun String.escapeHtml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PlantFormEditPreview() {
    GardenTheme(darkTheme = false) {
        PlantFormContent(
            state = PlantFormUiState(
                isEditing = true,
                isLoading = false,
                nickname = "Monstrinha",
                species = PlantSpecies("s1", "Monstera deliciosa", "Costela-de-adão"),
                environmentId = "e1",
                environments = listOf(EnvironmentOption("e1", "Sala de estar")),
                notes = "Perto da janela, luz indireta.",
                savedName = "Monstrinha",
            ),
            onClose = {},
            onRetryLoad = {},
            onNicknameChange = {},
            onPickSpecies = {},
            onEnvironmentChange = {},
            onNotesChange = {},
            onActiveChange = {},
            onSave = {},
            onDeleteClick = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
        )
    }
}
