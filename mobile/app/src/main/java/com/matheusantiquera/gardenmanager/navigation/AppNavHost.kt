package com.matheusantiquera.gardenmanager.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.session.SessionState
import com.matheusantiquera.gardenmanager.feature.auth.login.LoginScreen
import com.matheusantiquera.gardenmanager.feature.auth.signup.SignupScreen
import com.matheusantiquera.gardenmanager.feature.environment.form.ENVIRONMENT_FORM_RESULT_KEY
import com.matheusantiquera.gardenmanager.feature.environment.form.EnvironmentFormResult
import com.matheusantiquera.gardenmanager.feature.environment.form.EnvironmentFormScreen
import com.matheusantiquera.gardenmanager.feature.environment.list.ArchivedEnvironmentsScreen
import com.matheusantiquera.gardenmanager.feature.environment.list.EnvironmentsScreen
import com.matheusantiquera.gardenmanager.feature.plant.PICKED_SPECIES_KEY
import com.matheusantiquera.gardenmanager.feature.plant.PLANTS_ENVIRONMENT_FILTER_KEY
import com.matheusantiquera.gardenmanager.feature.plant.PLANT_FORM_RESULT_KEY
import com.matheusantiquera.gardenmanager.feature.plant.PlantFormResult
import com.matheusantiquera.gardenmanager.feature.plant.decodeSpeciesPick
import com.matheusantiquera.gardenmanager.feature.plant.detail.PlantDetailScreen
import com.matheusantiquera.gardenmanager.feature.plant.encode
import com.matheusantiquera.gardenmanager.feature.plant.form.PlantFormScreen
import com.matheusantiquera.gardenmanager.feature.plant.list.PlantsScreen
import com.matheusantiquera.gardenmanager.feature.plant.species.SpeciesPickerScreen
import com.matheusantiquera.gardenmanager.feature.maintenance.MAINTENANCE_RESULT_KEY
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceResult
import com.matheusantiquera.gardenmanager.feature.maintenance.detail.LogDetailScreen
import com.matheusantiquera.gardenmanager.feature.maintenance.form.LogFormScreen
import com.matheusantiquera.gardenmanager.feature.maintenance.form.ScheduleFormScreen
import com.matheusantiquera.gardenmanager.feature.schedule.ScheduleScreen
import com.matheusantiquera.gardenmanager.feature.profile.ProfileScreen

/**
 * Raiz da navegação. O estado da sessão decide qual grafo aparece: ao entrar ou sair, o grafo
 * anterior é descartado inteiro, sem precisar navegar à mão nem limpar a pilha.
 */
@Composable
fun AppRoot(sessionState: SessionState) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (sessionState) {
            SessionState.Loading -> SplashScreen()
            SessionState.LoggedOut -> AuthNavHost()
            SessionState.LoggedIn -> MainNavHost()
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.size(88.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primary) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_leaf),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(onNavigateToSignup = { navController.navigate(SignupRoute) })
        }
        composable<SignupRoute> {
            SignupScreen(
                onBack = { navController.popBackStack() },
                onAccountCreatedWithoutLogin = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun MainNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // A barra inferior só aparece nas abas; telas secundárias (formulários, arquivados) ocupam a tela toda.
    val showBottomBar = TopLevelDestination.entries.any { currentDestination?.hasRoute(it.route::class) == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hasRoute(destination.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination.route) },
                            icon = { Icon(painter = painterResource(destination.icon), contentDescription = null, modifier = Modifier.size(22.dp)) },
                            label = { Text(stringResource(destination.label), style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = EnvironmentsRoute,
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            composable<EnvironmentsRoute> { entry ->
                EnvironmentsScreen(
                    formResult = entry.enumResult<EnvironmentFormResult>(ENVIRONMENT_FORM_RESULT_KEY),
                    onFormResultShown = { entry.clearResult(ENVIRONMENT_FORM_RESULT_KEY) },
                    // Como no canvas: o card abre a aba Plantas filtrada pelo ambiente; o lápis abre a edição.
                    onEnvironmentClick = { environment ->
                        entry.ifResumed {
                            navController.navigateToTopLevel(PlantsRoute)
                            navController.getBackStackEntry<PlantsRoute>().savedStateHandle[PLANTS_ENVIRONMENT_FILTER_KEY] = environment.id
                        }
                    },
                    onEditEnvironment = { entry.ifResumed { navController.navigate(EnvironmentFormRoute(id = it.id)) } },
                    onNewEnvironment = { entry.ifResumed { navController.navigate(EnvironmentFormRoute()) } },
                    onShowArchived = { entry.ifResumed { navController.navigate(ArchivedEnvironmentsRoute) } },
                )
            }
            composable<PlantsRoute> { entry ->
                PlantsScreen(
                    environmentFilterRequest = entry.stringResult(PLANTS_ENVIRONMENT_FILTER_KEY),
                    onEnvironmentFilterRequestConsumed = { entry.clearResult(PLANTS_ENVIRONMENT_FILTER_KEY) },
                    plantFormResult = entry.enumResult<PlantFormResult>(PLANT_FORM_RESULT_KEY),
                    onPlantFormResultShown = { entry.clearResult(PLANT_FORM_RESULT_KEY) },
                    onPlantClick = { entry.ifResumed { navController.navigate(PlantDetailRoute(id = it.id)) } },
                    onNewPlant = { environmentId -> entry.ifResumed { navController.navigate(PlantFormRoute(environmentId = environmentId)) } },
                )
            }
            composable<ScheduleRoute> { entry ->
                ScheduleScreen(
                    result = entry.enumResult<MaintenanceResult>(MAINTENANCE_RESULT_KEY),
                    onResultShown = { entry.clearResult(MAINTENANCE_RESULT_KEY) },
                    onComplete = { entry.ifResumed { navController.navigate(LogFormRoute(plantId = it.plantId, scheduleId = it.id)) } },
                    onEditSchedule = { entry.ifResumed { navController.navigate(ScheduleFormRoute(plantId = it.plantId, id = it.id)) } },
                    onLogClick = { entry.ifResumed { navController.navigate(LogDetailRoute(id = it.id)) } },
                )
            }
            composable<ProfileRoute> { ProfileScreen() }

            composable<ArchivedEnvironmentsRoute> { entry ->
                ArchivedEnvironmentsScreen(
                    formResult = entry.enumResult<EnvironmentFormResult>(ENVIRONMENT_FORM_RESULT_KEY),
                    onFormResultShown = { entry.clearResult(ENVIRONMENT_FORM_RESULT_KEY) },
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                    onEnvironmentClick = { entry.ifResumed { navController.navigate(EnvironmentFormRoute(id = it.id)) } },
                )
            }
            composable<EnvironmentFormRoute> { entry ->
                EnvironmentFormScreen(
                    onClose = { entry.ifResumed { navController.popBackStack() } },
                    onDone = { result ->
                        // Deixa o resultado para a tela de onde o formulário foi aberto mostrar a mensagem.
                        navController.previousBackStackEntry?.savedStateHandle?.set(ENVIRONMENT_FORM_RESULT_KEY, result.name)
                        navController.popBackStack()
                    },
                )
            }

            composable<PlantDetailRoute> { entry ->
                val plantId = entry.toRoute<PlantDetailRoute>().id
                PlantDetailScreen(
                    formResult = entry.enumResult<PlantFormResult>(PLANT_FORM_RESULT_KEY),
                    onFormResultShown = { entry.clearResult(PLANT_FORM_RESULT_KEY) },
                    maintenanceResult = entry.enumResult<MaintenanceResult>(MAINTENANCE_RESULT_KEY),
                    onMaintenanceResultShown = { entry.clearResult(MAINTENANCE_RESULT_KEY) },
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                    onEdit = { entry.ifResumed { navController.navigate(PlantFormRoute(id = plantId)) } },
                    onRegister = { entry.ifResumed { navController.navigate(LogFormRoute(plantId = plantId)) } },
                    onSchedule = { entry.ifResumed { navController.navigate(ScheduleFormRoute(plantId = plantId)) } },
                    onCompleteSchedule = { entry.ifResumed { navController.navigate(LogFormRoute(plantId = plantId, scheduleId = it.id)) } },
                    onEditSchedule = { entry.ifResumed { navController.navigate(ScheduleFormRoute(plantId = plantId, id = it.id)) } },
                    onOpenLog = { log, readOnly -> entry.ifResumed { navController.navigate(LogDetailRoute(id = log.id, readOnly = readOnly)) } },
                )
            }
            composable<ScheduleFormRoute> { entry ->
                ScheduleFormScreen(
                    onClose = { entry.ifResumed { navController.popBackStack() } },
                    onDone = { result -> navController.finishMaintenanceForm(result) },
                )
            }
            composable<LogDetailRoute> { entry ->
                val route = entry.toRoute<LogDetailRoute>()
                LogDetailScreen(
                    result = entry.enumResult<MaintenanceResult>(MAINTENANCE_RESULT_KEY),
                    onResultShown = { entry.clearResult(MAINTENANCE_RESULT_KEY) },
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                    onEdit = { log -> entry.ifResumed { navController.navigate(LogFormRoute(plantId = log.plantId, id = log.id)) } },
                    onDeleted = { result -> navController.finishMaintenanceForm(result) },
                    readOnly = route.readOnly,
                )
            }
            composable<LogFormRoute> { entry ->
                LogFormScreen(
                    onClose = { entry.ifResumed { navController.popBackStack() } },
                    onDone = { result -> navController.finishMaintenanceForm(result) },
                )
            }
            composable<PlantFormRoute> { entry ->
                PlantFormScreen(
                    speciesPick = entry.stringResult(PICKED_SPECIES_KEY)?.let(::decodeSpeciesPick),
                    onSpeciesPickConsumed = { entry.clearResult(PICKED_SPECIES_KEY) },
                    onClose = { entry.ifResumed { navController.popBackStack() } },
                    onPickSpecies = { entry.ifResumed { navController.navigate(SpeciesPickerRoute(selectedId = it)) } },
                    onDone = { result ->
                        if (result == PlantFormResult.Deleted) {
                            // A planta não existe mais: volta direto para a lista, pulando o detalhe.
                            navController.popBackStack<PlantsRoute>(inclusive = false)
                            navController.getBackStackEntry<PlantsRoute>().savedStateHandle[PLANT_FORM_RESULT_KEY] = result.name
                        } else {
                            // Criada volta para a lista; editada, para o detalhe.
                            navController.previousBackStackEntry?.savedStateHandle?.set(PLANT_FORM_RESULT_KEY, result.name)
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable<SpeciesPickerRoute> { entry ->
                SpeciesPickerScreen(
                    onClose = { entry.ifResumed { navController.popBackStack() } },
                    onPick = { pick ->
                        entry.ifResumed {
                            // Deixa a escolha para o formulário de onde o seletor foi aberto.
                            navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_SPECIES_KEY, pick.encode())
                            navController.popBackStack()
                        }
                    },
                )
            }
        }
    }
}

/** Fecha um formulário de manutenção deixando o [result] para a tela de onde ele foi aberto mostrar a mensagem. */
private fun NavHostController.finishMaintenanceForm(result: MaintenanceResult) {
    previousBackStackEntry?.savedStateHandle?.set(MAINTENANCE_RESULT_KEY, result.name)
    popBackStack()
}

/**
 * Executa [action] só com esta tela ativa. Durante a animação de troca de tela, a que está saindo ainda
 * recebe toques; sem isso, um toque rápido logo depois de trocar de aba acertava um botão da tela
 * anterior que estivesse no mesmo lugar.
 */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}

/**
 * Valor que outra tela deixou no `savedStateHandle` desta entrada da pilha (o resultado de um formulário,
 * a escolha de um seletor, um filtro pedido), ou nulo. É lido aqui, na tela, e repassado ao ViewModel:
 * o `SavedStateHandle` que o ViewModel recebe não é o mesmo desta entrada e não enxerga esses valores.
 */
@Composable
private fun NavBackStackEntry.stringResult(key: String): String? {
    val value by savedStateHandle.getStateFlow<String?>(key, null).collectAsStateWithLifecycle()
    return value
}

/** Como [stringResult], para resultados guardados pelo nome de uma constante de enum. */
@Composable
private inline fun <reified T : Enum<T>> NavBackStackEntry.enumResult(key: String): T? =
    stringResult(key)?.let { name -> enumValues<T>().firstOrNull { it.name == name } }

private fun NavBackStackEntry.clearResult(key: String) {
    savedStateHandle[key] = null
}

/** Troca de aba como a barra inferior faz: preserva o estado de cada aba e não empilha abas repetidas. */
private fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
