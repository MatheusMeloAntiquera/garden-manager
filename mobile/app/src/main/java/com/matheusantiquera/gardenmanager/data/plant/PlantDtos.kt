package com.matheusantiquera.gardenmanager.data.plant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Corpo de `POST` e `PUT /plants`. Campos nulos ficam fora do JSON: no POST, `active` omitido cria a
 * planta ativa; no PUT, que substitui tudo, omitir espécie, ambiente ou observações os limpa.
 */
@Serializable
data class PlantRequest(
    @SerialName("species_id") val speciesId: String? = null,
    @SerialName("environment_id") val environmentId: String? = null,
    val nickname: String? = null,
    val notes: String? = null,
    val active: Boolean? = null,
)

@Serializable
data class PlantSpeciesRefResponse(
    val id: String,
    @SerialName("scientific_name") val scientificName: String,
    @SerialName("common_name") val commonName: String? = null,
)

@Serializable
data class PlantEnvironmentRefResponse(
    val id: String,
    val name: String,
)

@Serializable
data class PlantResponse(
    val id: String,
    @SerialName("display_name") val displayName: String, // apelido ou, sem ele, o nome da espécie
    val nickname: String? = null,
    val notes: String? = null,
    val active: Boolean,
    val species: PlantSpeciesRefResponse? = null,
    val environment: PlantEnvironmentRefResponse? = null,
)
