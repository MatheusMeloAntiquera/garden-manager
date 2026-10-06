package com.matheusantiquera.gardenmanager.data.environment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Corpo de `POST` e `PUT /environments`. No POST, `active` nulo fica fora do JSON e o ambiente nasce
 * ativo; no PUT ele é obrigatório. `notes` nulo limpa as observações, porque o PUT substitui tudo.
 */
@Serializable
data class EnvironmentRequest(
    val name: String,
    val notes: String? = null,
    val active: Boolean? = null,
)

@Serializable
data class EnvironmentResponse(
    val id: String,
    val name: String,
    val notes: String? = null,
    val active: Boolean,
    @SerialName("plant_count") val plantCount: Int = 0, // plantas ativas
    @SerialName("overdue_count") val overdueCount: Int = 0, // agendamentos vencidos das plantas ativas
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
