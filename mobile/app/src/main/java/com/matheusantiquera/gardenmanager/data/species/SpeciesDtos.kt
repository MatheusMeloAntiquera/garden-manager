package com.matheusantiquera.gardenmanager.data.species

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpeciesResponse(
    val id: String,
    @SerialName("scientific_name") val scientificName: String,
    val family: String,
    val category: String, // folhagem, suculenta, flor, arvore, erva, hortalica, frutifera ou grama
    @SerialName("common_name") val commonName: String? = null, // nome popular principal
)
