package com.matheusantiquera.gardenmanager.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.matheusantiquera.gardenmanager.R
import kotlinx.serialization.Serializable

// Fluxo de autenticação.
@Serializable
data object LoginRoute

@Serializable
data object SignupRoute

// Abas do app com sessão iniciada.
@Serializable
data object EnvironmentsRoute

@Serializable
data object PlantsRoute

@Serializable
data object ScheduleRoute

@Serializable
data object ProfileRoute

// Telas secundárias, abertas a partir das abas.
@Serializable
data object ArchivedEnvironmentsRoute

/** Formulário de ambiente: com [id] edita, sem ele cria um novo. */
@Serializable
data class EnvironmentFormRoute(val id: String? = null)

@Serializable
data class PlantDetailRoute(val id: String)

/**
 * Formulário de planta: com [id] edita, sem ele cria uma nova. [environmentId] preenche o ambiente
 * ao criar, quando a lista estava filtrada por um.
 */
@Serializable
data class PlantFormRoute(val id: String? = null, val environmentId: String? = null)

/** Formulário de agendamento de uma planta: com [id] edita, sem ele cria um novo. A planta é fixa. */
@Serializable
data class ScheduleFormRoute(val plantId: String, val id: String? = null)

/** Detalhe de uma execução. Com [readOnly] (execução de planta arquivada), não oferece editar nem excluir. */
@Serializable
data class LogDetailRoute(val id: String, val readOnly: Boolean = false)

/**
 * Formulário de registro de uma manutenção feita: com [id] edita uma execução; com [scheduleId] conclui
 * aquele agendamento; sem os dois registra uma manutenção avulsa. A planta é fixa.
 */
@Serializable
data class LogFormRoute(val plantId: String, val id: String? = null, val scheduleId: String? = null)

/** Busca no catálogo de espécies. [selectedId] é a espécie atual da planta, marcada na lista. */
@Serializable
data class SpeciesPickerRoute(val selectedId: String? = null)

/** Item da barra de navegação inferior. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Environments(EnvironmentsRoute, R.string.nav_environments, R.drawable.ic_home),
    Plants(PlantsRoute, R.string.nav_plants, R.drawable.ic_leaf),
    Schedule(ScheduleRoute, R.string.nav_schedule, R.drawable.ic_calendar),
    Profile(ProfileRoute, R.string.nav_profile, R.drawable.ic_person),
}
