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
