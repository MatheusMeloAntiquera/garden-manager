package com.matheusantiquera.gardenmanager.data.maintenance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceTypeResponse(
    val id: String,
    val name: String,
)

@Serializable
data class ScheduleResponse(
    val id: String,
    val type: MaintenanceTypeResponse,
    @SerialName("due_at") val dueAt: String, // AAAA-MM-DD HH:mm:ss, no fuso da API
    val status: String, // pending ou overdue
)

@Serializable
data class LogResponse(
    val id: String,
    val type: MaintenanceTypeResponse,
    @SerialName("performed_at") val performedAt: String, // AAAA-MM-DD HH:mm:ss, no fuso da API
)
