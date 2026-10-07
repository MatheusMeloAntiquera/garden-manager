package com.matheusantiquera.gardenmanager.data.plant

data class Plant(
    val id: String,
    /** Apelido ou, sem ele, o nome popular (ou científico) da espécie. */
    val displayName: String,
    val nickname: String?,
    val notes: String?,
    val active: Boolean,
    val species: PlantSpecies?,
    val environment: PlantEnvironment?,
) {
    /** Letra do avatar da lista. */
    val initial: String get() = displayName.trim().take(1).uppercase()

    /**
     * Nome popular da espécie, quando ele não é o próprio título da planta. Sem apelido, o título já é o
     * nome popular, e repeti-lo embaixo não diz nada.
     */
    val secondaryCommonName: String?
        get() = species?.commonName?.takeIf { !it.equals(displayName, ignoreCase = true) }
}

data class PlantSpecies(
    val id: String,
    val scientificName: String,
    val commonName: String?,
)

data class PlantEnvironment(
    val id: String,
    val name: String,
)

fun PlantResponse.toPlant(): Plant = Plant(
    id = id,
    displayName = displayName,
    nickname = nickname,
    notes = notes,
    active = active,
    species = species?.let { PlantSpecies(id = it.id, scientificName = it.scientificName, commonName = it.commonName) },
    environment = environment?.let { PlantEnvironment(id = it.id, name = it.name) },
)
