package com.matheusantiquera.gardenmanager.feature.plant

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiJson
import com.matheusantiquera.gardenmanager.core.ui.ShowOneShotMessage
import com.matheusantiquera.gardenmanager.data.plant.PlantSpecies
import kotlinx.serialization.Serializable

/** Como o formulário de planta terminou. A tela para onde ele volta mostra a mensagem correspondente. */
enum class PlantFormResult { Created, Updated, Deleted }

/** Chave do `savedStateHandle` da tela de destino onde o formulário deixa o [PlantFormResult]. */
const val PLANT_FORM_RESULT_KEY = "plant_form_result"

/**
 * Chave do `savedStateHandle` da aba Plantas com o id do ambiente a filtrar. Quem abre a aba a partir
 * de um ambiente grava o id; a aba aplica o filtro e limpa a chave.
 */
const val PLANTS_ENVIRONMENT_FILTER_KEY = "plants_environment_filter"

/** Chave do `savedStateHandle` do formulário onde o seletor de espécie deixa a escolha. */
const val PICKED_SPECIES_KEY = "picked_species"

/** Escolha feita no seletor de espécie. [species] nulo é "Sem espécie". */
@Serializable
data class SpeciesPick(val species: PickedSpecies?)

@Serializable
data class PickedSpecies(val id: String, val scientificName: String, val commonName: String?)

fun SpeciesPick.encode(): String = ApiJson.encodeToString(SpeciesPick.serializer(), this)

/** Nulo quando o texto não é uma escolha válida. */
fun decodeSpeciesPick(value: String): SpeciesPick? =
    runCatching { ApiJson.decodeFromString(SpeciesPick.serializer(), value) }.getOrNull()

fun PickedSpecies.toPlantSpecies(): PlantSpecies = PlantSpecies(id = id, scientificName = scientificName, commonName = commonName)

/** Mostra a mensagem do [result] no [snackbarHostState] e chama [onShown] para limpá-lo. */
@Composable
internal fun ShowPlantFormResult(
    result: PlantFormResult?,
    onShown: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val message = when (result) {
        PlantFormResult.Created -> stringResource(R.string.plant_created)
        PlantFormResult.Updated -> stringResource(R.string.plant_updated)
        PlantFormResult.Deleted -> stringResource(R.string.plant_deleted)
        null -> null
    }
    ShowOneShotMessage(message = message, onShown = onShown, snackbarHostState = snackbarHostState)
}
