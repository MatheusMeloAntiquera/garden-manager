package com.matheusantiquera.gardenmanager.feature.maintenance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTheme
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog

/** "Venceu há 2 dias", "Amanhã, 08:00", "Sex, 03/10": o prazo como a Agenda o descreve. */
@Composable
fun agendaDueText(due: AgendaDue): String = when (due) {
    is AgendaDue.Overdue -> when (due.daysAgo) {
        0L -> stringResource(R.string.maintenance_due_overdue_today, formatTime(due.dueAt.toLocalTime()))
        1L -> stringResource(R.string.maintenance_due_overdue_yesterday)
        else -> pluralStringResource(R.plurals.maintenance_due_overdue_days, due.daysAgo.toInt(), due.daysAgo.toInt())
    }
    is AgendaDue.Upcoming -> when (due.daysAhead) {
        0L -> stringResource(R.string.maintenance_due_today, formatTime(due.dueAt.toLocalTime()))
        1L -> stringResource(R.string.maintenance_due_tomorrow, formatTime(due.dueAt.toLocalTime()))
        else -> formatWeekdayDate(due.dueAt)
    }
}

/** Vermelho para atrasada, âmbar para o que vence em até 7 dias e neutro para o resto. */
@Composable
fun agendaDueColor(due: AgendaDue): Color = when (due.group) {
    AgendaGroup.Overdue -> MaterialTheme.colorScheme.error
    AgendaGroup.ThisWeek -> GardenTheme.colors.pending
    AgendaGroup.Later -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "Feita em 22/09/2026 08:40", com "· a partir de agendamento" quando a execução nasceu de um. */
@Composable
fun logSubtitle(log: MaintenanceLog): String {
    val performedAt = formatPerformedAt(log.performedAt)
    return stringResource(
        if (log.fromSchedule) R.string.maintenance_log_subtitle_from_schedule else R.string.maintenance_log_subtitle,
        performedAt,
    )
}
