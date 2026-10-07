package com.matheusantiquera.gardenmanager.feature.environment.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.EmptyState
import com.matheusantiquera.gardenmanager.core.designsystem.GardenFab
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTheme
import com.matheusantiquera.gardenmanager.core.designsystem.LoadErrorState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadingState
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.data.environment.Environment
import com.matheusantiquera.gardenmanager.feature.environment.form.EnvironmentFormResult
import com.matheusantiquera.gardenmanager.feature.environment.form.ShowEnvironmentFormResult

/**
 * Aba Ambientes. [formResult] é o que o formulário deixou ao fechar; a tela mostra a mensagem e
 * chama [onFormResultShown] para limpá-lo.
 */
@Composable
fun EnvironmentsScreen(
    formResult: EnvironmentFormResult?,
    onFormResultShown: () -> Unit,
    onEnvironmentClick: (Environment) -> Unit,
    onEditEnvironment: (Environment) -> Unit,
    onNewEnvironment: () -> Unit,
    onShowArchived: () -> Unit,
    viewModel: EnvironmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recarrega sempre que a tela volta a aparecer (ao abrir, ao voltar do formulário ou de outra aba).
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }

    ShowEnvironmentFormResult(result = formResult, onShown = onFormResultShown, snackbarHostState = snackbarHostState)

    EnvironmentsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEnvironmentClick = onEnvironmentClick,
        onEditEnvironment = onEditEnvironment,
        onNewEnvironment = onNewEnvironment,
        onShowArchived = onShowArchived,
        onRetry = viewModel::load,
    )
}

@Composable
internal fun EnvironmentsContent(
    state: EnvironmentsUiState,
    snackbarHostState: SnackbarHostState,
    onEnvironmentClick: (Environment) -> Unit,
    onEditEnvironment: (Environment) -> Unit,
    onNewEnvironment: () -> Unit,
    onShowArchived: () -> Unit,
    onRetry: () -> Unit,
) {
    // Scaffold local só para posicionar o botão flutuante e a mensagem sem que um cubra o outro.
    // As barras do sistema já são tratadas pelo Scaffold principal.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            GardenFab(text = stringResource(R.string.environments_new), icon = R.drawable.ic_add, onClick = onNewEnvironment)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Espaço no fim para o último card não ficar atrás do botão flutuante.
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { Header(firstName = state.firstName) }

            when (val list = state.list) {
                EnvironmentListState.Loading -> item(key = "loading") { LoadingState(modifier = Modifier.fillMaxWidth()) }

                is EnvironmentListState.Failed -> item(key = "error") {
                    LoadErrorState(
                        title = stringResource(R.string.environments_load_error),
                        message = list.message.asString(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is EnvironmentListState.Loaded -> if (list.environments.isEmpty()) {
                    // Sem ambientes ativos, o link fica abaixo do estado vazio.
                    item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ic_home,
                            title = stringResource(R.string.environments_empty_title),
                            description = stringResource(R.string.environments_empty_description),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "archived") { ShowArchivedLink(onClick = onShowArchived, alignment = Alignment.Center) }
                } else {
                    // Com ambientes ativos, o link fica no topo, antes do primeiro card.
                    item(key = "archived") { ShowArchivedLink(onClick = onShowArchived, alignment = Alignment.CenterEnd) }
                    items(list.environments, key = { it.id }) { environment ->
                        EnvironmentCard(
                            environment = environment,
                            onClick = { onEnvironmentClick(environment) },
                            onEdit = { onEditEnvironment(environment) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowArchivedLink(onClick: () -> Unit, alignment: Alignment) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        TextButton(onClick = onClick) {
            Text(text = stringResource(R.string.environments_show_archived), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Header(firstName: String?) {
    Column(modifier = Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        firstName?.let {
            Text(
                text = stringResource(R.string.environments_greeting, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.environments_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun EnvironmentsContentPreview() {
    GardenTheme(darkTheme = false) {
        EnvironmentsContent(
            state = EnvironmentsUiState(
                firstName = "Ana",
                list = EnvironmentListState.Loaded(
                    listOf(
                        Environment("1", "Sala de estar", "Janela leste, luz da manhã", true, plantCount = 5, overdueCount = 1),
                        Environment("2", "Varanda", "Sol da tarde, protegida do vento", true, plantCount = 1),
                        Environment("3", "Escritório", null, true, plantCount = 0),
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEnvironmentClick = {},
            onEditEnvironment = {},
            onNewEnvironment = {},
            onShowArchived = {},
            onRetry = {},
        )
    }
}
