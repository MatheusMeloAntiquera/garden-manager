package com.matheusantiquera.gardenmanager.feature.maintenance.form

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.BannerKind
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTextField
import com.matheusantiquera.gardenmanager.core.designsystem.LoadErrorState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadingState
import com.matheusantiquera.gardenmanager.core.designsystem.MessageBanner
import com.matheusantiquera.gardenmanager.core.designsystem.PrimaryButton
import com.matheusantiquera.gardenmanager.core.designsystem.ProgressDialog
import com.matheusantiquera.gardenmanager.core.designsystem.SecondaryButton
import com.matheusantiquera.gardenmanager.core.designsystem.SecondaryTopBar
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceType
import com.matheusantiquera.gardenmanager.feature.maintenance.formatDate
import com.matheusantiquera.gardenmanager.feature.maintenance.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Estrutura dos formulários de manutenção: cabeçalho, carregamento, campos rolando, Cancelar e Salvar, e a
 * janela "Salvando…". Os campos de cada formulário entram em [fields].
 */
@Composable
internal fun MaintenanceFormLayout(
    title: String,
    isLoading: Boolean,
    loadError: UiText?,
    loadErrorTitle: String,
    isSaving: Boolean,
    onClose: () -> Unit,
    onRetryLoad: () -> Unit,
    onSave: () -> Unit,
    error: UiText?,
    fields: @Composable ColumnScope.() -> Unit,
) {
    // Sem a barra inferior, a tela cuida das barras do sistema e do teclado.
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        SecondaryTopBar(title = title, onNavigationClick = onClose)

        when {
            isLoading -> LoadingState(modifier = Modifier.fillMaxSize())
            loadError != null -> LoadErrorState(
                title = loadErrorTitle,
                message = loadError.asString(),
                onRetry = onRetryLoad,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                error?.let { MessageBanner(text = it.asString(), kind = BannerKind.Error) }

                fields()

                Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Cancelar faz o mesmo que o X do cabeçalho: fecha sem salvar.
                    SecondaryButton(text = stringResource(R.string.action_cancel), onClick = onClose, modifier = Modifier.weight(1f), enabled = !isSaving)
                    PrimaryButton(text = stringResource(R.string.action_save), onClick = onSave, modifier = Modifier.weight(1f), loading = isSaving)
                }
            }
        }
    }

    if (isSaving) ProgressDialog(text = stringResource(R.string.maintenance_saving))
}

/** A planta do formulário, fixa: ícone e nome num cartão. */
@Composable
internal fun PlantHeader(name: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_leaf),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = stringResource(R.string.maintenance_form_plant),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Seletor do tipo de manutenção. Com [locked], mostra o tipo sem permitir trocar (concluir um agendamento). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TypeField(
    types: List<MaintenanceType>,
    selectedId: String?,
    onChange: (String) -> Unit,
    enabled: Boolean,
    locked: Boolean,
    errorText: String?,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactive = enabled && !locked
    val selectedText = types.firstOrNull { it.id == selectedId }?.name.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (interactive) expanded = it }) {
        GardenTextField(
            value = selectedText,
            onValueChange = {},
            label = stringResource(R.string.maintenance_form_type),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, interactive),
            placeholder = stringResource(R.string.maintenance_form_type_placeholder),
            errorText = errorText,
            trailingContent = if (locked) {
                null
            } else {
                {
                    Icon(painter = painterResource(R.drawable.ic_chevron_down), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            },
            enabled = interactive,
            readOnly = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onChange(type.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Data e hora lado a lado; tocar abre o seletor de cada uma. [errorText] aparece embaixo dos dois. */
@Composable
internal fun DateTimeFields(
    date: LocalDate,
    time: LocalTime,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    dateLabel: String,
    timeLabel: String,
    enabled: Boolean,
    errorText: String? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerField(
                label = dateLabel,
                value = formatDate(date),
                icon = R.drawable.ic_calendar,
                onClick = { showDatePicker = true },
                enabled = enabled,
                isError = errorText != null,
                modifier = Modifier.weight(3f),
            )
            PickerField(
                label = timeLabel,
                value = formatTime(time),
                icon = null,
                onClick = { showTimePicker = true },
                enabled = enabled,
                isError = errorText != null,
                modifier = Modifier.weight(2f),
            )
        }
        errorText?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }

    if (showDatePicker) {
        DateDialog(
            initial = date,
            onConfirm = {
                showDatePicker = false
                onDateChange(it)
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        TimeDialog(
            initial = time,
            onConfirm = {
                showTimePicker = false
                onTimeChange(it)
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** Campo que mostra um valor e abre um seletor ao tocar, no estilo dos campos de texto do design. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "$label: $value"
                },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                icon?.let {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // O seletor trabalha com milissegundos em UTC à meia-noite do dia escolhido.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok), style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.maintenance_form_time_dialog_title), style = MaterialTheme.typography.titleMedium) },
        text = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge) }
        },
    )
}
