package com.matheusantiquera.gardenmanager.data.maintenance

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Tipo do catálogo de manutenções (Rega, Poda, Adubação...). */
data class MaintenanceType(
    val id: String,
    val name: String,
)

/** Manutenção agendada para uma planta. */
data class MaintenanceSchedule(
    val id: String,
    val plantId: String,
    val plantName: String,
    val typeId: String,
    val typeName: String,
    val dueAt: LocalDateTime,
    /** Calculado pela API: o prazo já passou. */
    val overdue: Boolean,
    val notes: String? = null,
)

/** Manutenção já executada, no histórico. */
data class MaintenanceLog(
    val id: String,
    val plantId: String,
    val plantName: String,
    val typeId: String,
    val typeName: String,
    val performedAt: LocalDateTime,
    /** Nasceu de um agendamento (que foi excluído ao registrá-la). */
    val fromSchedule: Boolean = false,
    val notes: String? = null,
)

/** Agendamentos e histórico de uma planta, com os totais de cada um. */
data class PlantMaintenance(
    val schedules: List<MaintenanceSchedule>,
    val scheduleTotal: Int,
    val logs: List<MaintenanceLog>,
    val logTotal: Int,
)

/** Uma página de uma listagem de manutenções e o total que atende ao filtro. */
data class MaintenancePage<T>(
    val items: List<T>,
    val page: Int,
    val total: Int,
) {
    val hasMore: Boolean get() = page * MAINTENANCE_PAGE_SIZE < total
}

/** Itens por página nas listas da Agenda. A tela carrega a próxima ao chegar perto do fim. */
const val MAINTENANCE_PAGE_SIZE = 20

/** Dados de um agendamento para criar ou editar. Espera as observações já normalizadas. */
data class ScheduleInput(
    val plantId: String,
    val typeId: String,
    val dueAt: LocalDateTime,
    val notes: String?,
)

/**
 * Dados de uma execução para criar ou editar. Com [scheduleId] (só ao criar), o agendamento é
 * executado e excluído pela API.
 */
data class LogInput(
    val plantId: String,
    val typeId: String,
    val performedAt: LocalDateTime,
    val notes: String?,
    val scheduleId: String? = null,
)

private const val STATUS_OVERDUE = "overdue"

/** Formato de data e hora da API de manutenções, sem fuso. */
private val ApiDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun parseMaintenanceDateTime(value: String): LocalDateTime = LocalDateTime.parse(value, ApiDateTimeFormat)

fun formatMaintenanceDateTime(value: LocalDateTime): String = value.format(ApiDateTimeFormat)

fun MaintenanceTypeResponse.toType(): MaintenanceType = MaintenanceType(id = id, name = name)

fun ScheduleResponse.toSchedule(): MaintenanceSchedule = MaintenanceSchedule(
    id = id,
    plantId = plant.id,
    plantName = plant.displayName,
    typeId = type.id,
    typeName = type.name,
    dueAt = parseMaintenanceDateTime(dueAt),
    overdue = status == STATUS_OVERDUE,
    notes = notes,
)

fun LogResponse.toLog(): MaintenanceLog = MaintenanceLog(
    id = id,
    plantId = plant.id,
    plantName = plant.displayName,
    typeId = type.id,
    typeName = type.name,
    performedAt = parseMaintenanceDateTime(performedAt),
    fromSchedule = createdFromSchedule,
    notes = notes,
)
