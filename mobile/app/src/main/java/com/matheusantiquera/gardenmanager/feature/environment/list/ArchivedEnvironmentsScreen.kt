package com.matheusantiquera.gardenmanager.feature.environment.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.EmptyState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadErrorState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadingState
import com.matheusantiquera.gardenmanager.core.designsystem.SecondaryTopBar
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.data.environment.Environment
import com.matheusantiquera.gardenmanager.feature.environment.form.EnvironmentFormResult
import com.matheusantiquera.gardenmanager.feature.environment.form.ShowEnvironmentFormResult

/** Ambientes arquivados. [formResult] funciona como na aba Ambientes. */
@Composable
fun ArchivedEnvironmentsScreen(
    formResult: EnvironmentFormResult?,
    onFormResultShown: () -> Unit,
    onBack: () -> Unit,
    onEnvironmentClick: (Environment) -> Unit,
    viewModel: ArchivedEnvironmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recarrega ao voltar do formulário: um ambiente reativado sai desta lista.
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }

    ShowEnvironmentFormResult(result = formResult, onShown = onFormResultShown, snackbarHostState = snackbarHostState)

    // Sem a barra inferior, a tela cuida da barra de navegação do sistema.
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryTopBar(
                title = stringResource(R.string.environments_archived_title),
                onNavigationClick = onBack,
                navigationIcon = R.drawable.ic_arrow_back,
                navigationContentDescription = stringResource(R.string.action_back),
            )

            when (val list = state) {
                EnvironmentListState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())

                is EnvironmentListState.Failed -> LoadErrorState(
                    title = stringResource(R.string.environments_load_error),
                    message = list.message.asString(),
                    onRetry = viewModel::load,
                    modifier = Modifier.fillMaxWidth(),
                )

                is EnvironmentListState.Loaded -> if (list.environments.isEmpty()) {
                    EmptyState(
                        icon = R.drawable.ic_home,
                        title = stringResource(R.string.environments_archived_empty_title),
                        description = stringResource(R.string.environments_archived_empty_description),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list.environments, key = { it.id }) { environment ->
                            EnvironmentCard(environment = environment, onClick = { onEnvironmentClick(environment) })
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
