package com.matheusantiquera.gardenmanager.feature.maintenance

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

/** Seções da lista de pendentes da Agenda. "Esta semana" vai até 7 dias a partir de hoje. */
enum class AgendaGroup { Overdue, ThisWeek, Later }

/** Prazo de um agendamento como a Agenda o descreve, contado em dias de calendário a partir de hoje. */
sealed interface AgendaDue {
    val dueAt: LocalDateTime
    val group: AgendaGroup

    /** [daysAgo] é 0 quando venceu hoje (mais cedo), 1 ontem e assim por diante. */
    data class Overdue(val daysAgo: Long, override val dueAt: LocalDateTime) : AgendaDue {
        override val group: AgendaGroup get() = AgendaGroup.Overdue
    }

    /** [daysAhead] é 0 para hoje e 1 para amanhã. */
    data class Upcoming(val daysAhead: Long, override val dueAt: LocalDateTime) : AgendaDue {
        override val group: AgendaGroup get() = if (daysAhead <= WEEK_DAYS) AgendaGroup.ThisWeek else AgendaGroup.Later
    }
}

private const val WEEK_DAYS = 7L

/** Vencido vem da API (prazo antes de agora); os demais contam dias de calendário a partir de [today]. */
fun MaintenanceSchedule.agendaDue(today: LocalDate = LocalDate.now()): AgendaDue {
    val days = ChronoUnit.DAYS.between(today, dueAt.toLocalDate())
    return if (overdue) AgendaDue.Overdue(daysAgo = (-days).coerceAtLeast(0), dueAt = dueAt) else AgendaDue.Upcoming(days.coerceAtLeast(0), dueAt)
}

private val WeekdayNames = arrayOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
private val TimeOfDay: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Hora de um prazo: "08:00". */
fun formatTime(time: LocalTime): String = time.format(TimeOfDay)

/** Dia da semana e data como no canvas: "Sex, 03/10". */
fun formatWeekdayDate(dueAt: LocalDateTime): String = "${WeekdayNames[dueAt.dayOfWeek.ordinal]}, ${dueAt.format(DayMonth)}"

/** Data de um formulário: "03/10/2026". */
fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
