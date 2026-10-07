package com.matheusantiquera.gardenmanager.feature.maintenance

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.ConfirmDeleteDialog
import com.matheusantiquera.gardenmanager.core.designsystem.ProgressDialog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule

/**
 * Sheet de ações de um agendamento (Concluir, Editar, Excluir) e a confirmação de exclusão de um
 * agendamento ou de uma execução. Concluir e editar são repassados a quem usa, que abre o formulário.
 */
@Composable
fun MaintenanceActionsHost(
    state: MaintenanceActionsState,
    onDismissSheet: () -> Unit,
    onComplete: (MaintenanceSchedule) -> Unit,
    onEditSchedule: (MaintenanceSchedule) -> Unit,
    onAskDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    state.sheet?.let { schedule ->
        ActionSheet(
            schedule = schedule,
            onDismiss = onDismissSheet,
            onComplete = { schedule ->
                onDismissSheet()
                onComplete(schedule)
            },
            onEditSchedule = { schedule ->
                onDismissSheet()
                onEditSchedule(schedule)
            },
            onDelete = onAskDelete,
        )
    }

    when {
        // Ao confirmar, a confirmação dá lugar à janela "Excluindo…".
        state.isDeleting -> ProgressDialog(text = stringResource(R.string.maintenance_deleting))
        state.confirmDelete != null -> {
            val target = state.confirmDelete
            val isSchedule = target is MaintenanceTarget.OfSchedule
            ConfirmDeleteDialog(
                title = stringResource(if (isSchedule) R.string.maintenance_schedule_delete_title else R.string.maintenance_log_delete_title),
                message = deleteMessage(target, isSchedule),
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDelete,
            )
        }
    }
}

@Composable
private fun deleteMessage(target: MaintenanceTarget, isSchedule: Boolean): AnnotatedString {
    val html = stringResource(
        if (isSchedule) R.string.maintenance_schedule_delete_message else R.string.maintenance_log_delete_message,
        "${target.typeName} · ${target.plantName}".escapeHtml(),
    )
    return AnnotatedString.fromHtml(html)
}

// O apelido da planta é digitado pelo usuário e vai dentro de HTML: escapa para não virar marcação.
private fun String.escapeHtml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(
    schedule: MaintenanceSchedule,
    onDismiss: () -> Unit,
    onComplete: (MaintenanceSchedule) -> Unit,
    onEditSchedule: (MaintenanceSchedule) -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 8.dp).padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MaintenanceTypeIcon(typeName = schedule.typeName, size = 44.dp, iconSize = 22.dp, corner = 14.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${schedule.typeName} · ${schedule.plantName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val due = schedule.agendaDue()
                    Text(text = agendaDueText(due), style = MaterialTheme.typography.bodySmall, color = agendaDueColor(due))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 8.dp))

            ActionRow(R.drawable.ic_check, stringResource(R.string.maintenance_action_complete)) { onComplete(schedule) }
            ActionRow(R.drawable.ic_edit, stringResource(R.string.maintenance_action_edit_schedule)) { onEditSchedule(schedule) }
            ActionRow(
                R.drawable.ic_delete,
                stringResource(R.string.maintenance_action_delete_schedule),
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ActionRow(
    @DrawableRes icon: Int,
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (color == MaterialTheme.colorScheme.error) color else MaterialTheme.colorScheme.primary,
        )
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = color)
    }
}
