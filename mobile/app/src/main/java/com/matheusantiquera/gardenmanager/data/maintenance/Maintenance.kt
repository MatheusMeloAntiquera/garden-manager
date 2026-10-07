package com.matheusantiquera.gardenmanager.data.maintenance

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Manutenção agendada para uma planta. */
data class MaintenanceSchedule(
    val id: String,
    val typeName: String,
    val dueAt: LocalDateTime,
    /** Calculado pela API: o prazo já passou. */
    val overdue: Boolean,
)

/** Manutenção já executada, no histórico da planta. */
data class MaintenanceLog(
    val id: String,
    val typeName: String,
    val performedAt: LocalDateTime,
)

/** Agendamentos e histórico de uma planta, com os totais de cada um. */
data class PlantMaintenance(
    val schedules: List<MaintenanceSchedule>,
    val scheduleTotal: Int,
    val logs: List<MaintenanceLog>,
    val logTotal: Int,
)

private const val STATUS_OVERDUE = "overdue"

/** Formato de data e hora da API de manutenções, sem fuso. */
private val ApiDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun parseMaintenanceDateTime(value: String): LocalDateTime = LocalDateTime.parse(value, ApiDateTimeFormat)

fun ScheduleResponse.toSchedule(): MaintenanceSchedule = MaintenanceSchedule(
    id = id,
    typeName = type.name,
    dueAt = parseMaintenanceDateTime(dueAt),
    overdue = status == STATUS_OVERDUE,
)

fun LogResponse.toLog(): MaintenanceLog = MaintenanceLog(
    id = id,
    typeName = type.name,
    performedAt = parseMaintenanceDateTime(performedAt),
)
