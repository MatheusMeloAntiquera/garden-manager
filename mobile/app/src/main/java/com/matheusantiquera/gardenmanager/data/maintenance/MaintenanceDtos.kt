package com.matheusantiquera.gardenmanager.data.maintenance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceTypeResponse(
    val id: String,
    val name: String,
)

@Serializable
data class MaintenanceTypeListResponse(
    val data: List<MaintenanceTypeResponse>,
)

@Serializable
data class PlantRefResponse(
    val id: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class ScheduleResponse(
    val id: String,
    val plant: PlantRefResponse,
    val type: MaintenanceTypeResponse,
    @SerialName("due_at") val dueAt: String, // AAAA-MM-DD HH:mm:ss, no fuso da API
    val notes: String? = null,
    val status: String, // pending ou overdue
)

@Serializable
data class LogResponse(
    val id: String,
    val plant: PlantRefResponse,
    val type: MaintenanceTypeResponse,
    @SerialName("created_from_schedule") val createdFromSchedule: Boolean = false,
    @SerialName("performed_at") val performedAt: String, // AAAA-MM-DD HH:mm:ss, no fuso da API
    val notes: String? = null,
)

@Serializable
data class ScheduleRequest(
    @SerialName("plant_id") val plantId: String,
    @SerialName("type_id") val typeId: String,
    @SerialName("due_at") val dueAt: String,
    val notes: String? = null,
)

/** Com [scheduleId], a API herda planta, tipo e observações omitidos do agendamento, que é excluído. */
@Serializable
data class CreateLogRequest(
    @SerialName("plant_id") val plantId: String? = null,
    @SerialName("type_id") val typeId: String? = null,
    @SerialName("schedule_id") val scheduleId: String? = null,
    @SerialName("performed_at") val performedAt: String,
    val notes: String? = null,
)

@Serializable
data class UpdateLogRequest(
    @SerialName("plant_id") val plantId: String,
    @SerialName("type_id") val typeId: String,
    @SerialName("performed_at") val performedAt: String,
    val notes: String? = null,
)
