package com.matheusantiquera.gardenmanager.feature.plant.detail

import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Situação de um agendamento, como aparece no chip do detalhe da planta. */
sealed interface DueStatus {
    data object Overdue : DueStatus
    data object Today : DueStatus
    data object Tomorrow : DueStatus
    data class InDays(val days: Long) : DueStatus
}

/**
 * Vencido vem da API (prazo antes de agora). Os demais contam dias de calendário a partir de [today]:
 * um prazo ainda hoje, mesmo daqui a algumas horas, é "Hoje".
 */
fun MaintenanceSchedule.dueStatus(today: LocalDate = LocalDate.now()): DueStatus {
    if (overdue) return DueStatus.Overdue
    return when (val days = ChronoUnit.DAYS.between(today, dueAt.toLocalDate())) {
        in Long.MIN_VALUE..0L -> DueStatus.Today
        1L -> DueStatus.Tomorrow
        else -> DueStatus.InDays(days)
    }
}

private val DayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val DayMonthTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM, HH:mm")
private val FullDateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** Prazo como no canvas: "29/09, 09:00", ou só "13/10" quando a hora é 00:00 (prazo de dia inteiro). */
fun formatDueAt(dueAt: LocalDateTime): String =
    if (dueAt.toLocalTime() == LocalTime.MIDNIGHT) dueAt.format(DayMonth) else dueAt.format(DayMonthTime)

/** Data e hora de uma manutenção feita, no histórico: "22/09/2026 08:40". */
fun formatPerformedAt(performedAt: LocalDateTime): String = performedAt.format(FullDateTime)
