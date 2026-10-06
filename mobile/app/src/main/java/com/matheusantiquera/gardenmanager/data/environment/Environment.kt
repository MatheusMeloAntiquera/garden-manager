package com.matheusantiquera.gardenmanager.data.environment

data class Environment(
    val id: String,
    val name: String,
    val notes: String?,
    val active: Boolean,
    /** Quantidade de plantas ativas no ambiente; as arquivadas não entram. */
    val plantCount: Int,
    /** Manutenções agendadas e já vencidas das plantas ativas do ambiente. */
    val overdueCount: Int = 0,
)

fun EnvironmentResponse.toEnvironment(): Environment = Environment(
    id = id,
    name = name,
    notes = notes,
    active = active,
    plantCount = plantCount,
    overdueCount = overdueCount,
)
