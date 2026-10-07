package com.matheusantiquera.gardenmanager.feature.maintenance.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTheme
import com.matheusantiquera.gardenmanager.core.designsystem.LoadErrorState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadingState
import com.matheusantiquera.gardenmanager.core.designsystem.PrimaryButton
import com.matheusantiquera.gardenmanager.core.ui.ShowOneShotMessage
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceActionsHost
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceResult
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceTypeIcon
import com.matheusantiquera.gardenmanager.feature.maintenance.ShowMaintenanceResult
import com.matheusantiquera.gardenmanager.feature.maintenance.formatPerformedAt
import java.time.LocalDateTime

/**
 * Detalhe de uma execução, para ler. [result] é o que o formulário de edição deixou ao voltar; a tela
 * mostra a mensagem e chama [onResultShown] para limpá-lo. Com [readOnly] (execução de planta arquivada),
 * não há Editar nem Excluir. Depois de excluir, chama [onDeleted] com o resultado e a tela fecha.
 */
@Composable
fun LogDetailScreen(
    result: MaintenanceResult?,
    onResultShown: () -> Unit,
    onBack: () -> Unit,
    onEdit: (MaintenanceLog) -> Unit,
    onDeleted: (MaintenanceResult) -> Unit,
    readOnly: Boolean,
    viewModel: LogDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions by viewModel.actions.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recarrega ao voltar do formulário de edição.
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }

    LaunchedEffect(actions.deleted) { actions.deleted?.let(onDeleted) }

    ShowMaintenanceResult(result = result, onShown = onResultShown, snackbarHostState = snackbarHostState)
    ShowOneShotMessage(message = actions.error?.asString(), onShown = viewModel.actions::clearError, snackbarHostState = snackbarHostState)

    // Sem a barra inferior, a tela cuida da barra de navegação do sistema.
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        LogDetailContent(
            state = state,
            readOnly = readOnly,
            onBack = onBack,
            onRetry = viewModel::load,
            onEdit = onEdit,
            onDelete = viewModel::onDeleteClick,
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    MaintenanceActionsHost(
        state = actions,
        onDismissSheet = {},
        onComplete = {},
        onEditSchedule = {},
        onAskDelete = {},
        onDismissDelete = viewModel.actions::dismissDelete,
        onConfirmDelete = viewModel.actions::confirmDelete,
    )
}

@Composable
internal fun LogDetailContent(
    state: LogDetailUiState,
    readOnly: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (MaintenanceLog) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        when (state) {
            LogDetailUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is LogDetailUiState.Failed -> LoadErrorState(
                title = stringResource(R.string.log_detail_load_error),
                message = state.message.asString(),
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            is LogDetailUiState.Loaded -> Details(log = state.log, readOnly = readOnly, onEdit = { onEdit(state.log) }, onDelete = onDelete)
        }
    }
}

@Composable
private fun Details(log: MaintenanceLog, readOnly: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MaintenanceTypeIcon(typeName = log.typeName, size = 64.dp, iconSize = 32.dp, corner = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = log.typeName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    text = stringResource(R.string.log_detail_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                InfoRow(label = stringResource(R.string.log_detail_plant), value = log.plantName)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoRow(label = stringResource(R.string.log_detail_performed_at), value = formatPerformedAt(log.performedAt))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoRow(
                    label = stringResource(R.string.log_detail_origin),
                    value = stringResource(if (log.fromSchedule) R.string.log_detail_origin_schedule else R.string.log_detail_origin_manual),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.field_notes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            if (log.notes == null) {
                Text(
                    text = stringResource(R.string.log_detail_no_notes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = log.notes,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (!readOnly) {
            Spacer(modifier = Modifier.weight(1f))
            PrimaryButton(text = stringResource(R.string.maintenance_action_edit_log), onClick = onEdit)
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.maintenance_action_delete_log),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LogDetailPreview() {
    GardenTheme(darkTheme = false) {
        LogDetailContent(
            state = LogDetailUiState.Loaded(
                MaintenanceLog(
                    "l1", "p1", "Monstrinha", "t1", "Rega", LocalDateTime.of(2026, 9, 22, 8, 40),
                    fromSchedule = true, notes = "Meio litro de água, folhas borrifadas.",
                ),
            ),
            readOnly = false,
            onBack = {},
            onRetry = {},
            onEdit = {},
            onDelete = {},
        )
    }
}
