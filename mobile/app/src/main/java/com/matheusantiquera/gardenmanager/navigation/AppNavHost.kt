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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.session.SessionState
import com.matheusantiquera.gardenmanager.feature.auth.login.LoginScreen
import com.matheusantiquera.gardenmanager.feature.auth.signup.SignupScreen
import com.matheusantiquera.gardenmanager.feature.placeholder.PlaceholderScreen
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hasRoute(destination.route::class) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = EnvironmentsRoute,
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            composable<EnvironmentsRoute> { PlaceholderScreen(R.string.nav_environments, R.drawable.ic_home) }
            composable<PlantsRoute> { PlaceholderScreen(R.string.nav_plants, R.drawable.ic_leaf) }
            composable<ScheduleRoute> { PlaceholderScreen(R.string.nav_schedule, R.drawable.ic_calendar) }
            composable<ProfileRoute> { ProfileScreen() }
        }
    }
}
