package com.matheusantiquera.gardenmanager.feature.plant.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.EmptyState
import com.matheusantiquera.gardenmanager.core.designsystem.GardenFab
import com.matheusantiquera.gardenmanager.core.designsystem.GardenFilterChip
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTheme
import com.matheusantiquera.gardenmanager.core.designsystem.InfoChip
import com.matheusantiquera.gardenmanager.core.designsystem.LoadErrorState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadingState
import com.matheusantiquera.gardenmanager.core.designsystem.SearchField
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.data.environment.Environment
import com.matheusantiquera.gardenmanager.data.plant.Plant
import com.matheusantiquera.gardenmanager.data.plant.PlantEnvironment
import com.matheusantiquera.gardenmanager.data.plant.PlantSpecies
import com.matheusantiquera.gardenmanager.feature.plant.PlantFormResult
import com.matheusantiquera.gardenmanager.feature.plant.ShowPlantFormResult

/** Quantos itens antes do fim da lista a próxima página começa a carregar. */
private const val LOAD_MORE_THRESHOLD = 5

/**
 * Aba Plantas. [plantFormResult] é o que o formulário de planta deixou ao voltar para esta tela; a tela
 * mostra a mensagem e chama [onPlantFormResultShown] para limpá-lo. [environmentFilterRequest] é o
 * ambiente pedido por quem abriu a aba (o card de um ambiente); a tela aplica o filtro e chama
 * [onEnvironmentFilterRequestConsumed] para limpá-lo.
 */
@Composable
fun PlantsScreen(
    environmentFilterRequest: String?,
    onEnvironmentFilterRequestConsumed: () -> Unit,
    plantFormResult: PlantFormResult?,
    onPlantFormResultShown: () -> Unit,
    onPlantClick: (Plant) -> Unit,
    onNewPlant: (environmentId: String?) -> Unit,
    viewModel: PlantsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recarrega sempre que a tela volta a aparecer (ao abrir, ao voltar do detalhe ou de outra aba).
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }

    LaunchedEffect(environmentFilterRequest) {
        val environmentId = environmentFilterRequest ?: return@LaunchedEffect
        onEnvironmentFilterRequestConsumed()
        viewModel.applyEnvironmentFilter(environmentId)
    }

    ShowPlantFormResult(result = plantFormResult, onShown = onPlantFormResultShown, snackbarHostState = snackbarHostState)

    PlantsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::onFilterSelected,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::load,
        onPlantClick = onPlantClick,
        onNewPlant = { onNewPlant((state.filter as? PlantsFilter.ByEnvironment)?.environmentId) },
    )
}

@Composable
internal fun PlantsContent(
    state: PlantsUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (PlantsFilter) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onPlantClick: (Plant) -> Unit,
    onNewPlant: () -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.list) { if (nearEnd) onLoadMore() }

    // Scaffold local só para posicionar o botão flutuante e a mensagem sem que um cubra o outro.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            GardenFab(text = stringResource(R.string.plants_new), icon = R.drawable.ic_add, onClick = onNewPlant)
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 28.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = stringResource(R.string.plants_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    SearchField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = stringResource(R.string.plants_search_placeholder),
                        label = stringResource(R.string.plants_search_label),
                    )
                }
            }

            item(key = "filters") { FilterChips(state = state, onFilterSelected = onFilterSelected) }

            state.filteredEnvironment?.let { environment ->
                item(key = "caption") {
                    FilterCaption(environment = environment, total = (state.list as? PlantListState.Loaded)?.total)
                }
            }

            when (val list = state.list) {
                PlantListState.Loading -> item(key = "loading") { LoadingState(modifier = Modifier.fillMaxWidth()) }

                is PlantListState.Failed -> item(key = "error") {
                    LoadErrorState(
                        title = stringResource(R.string.plants_load_error),
                        message = list.message.asString(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is PlantListState.Loaded -> {
                    if (list.plants.isEmpty()) {
                        item(key = "empty") { EmptyPlants(state = state) }
                    }
                    items(list.plants, key = { it.id }) { plant ->
                        PlantRow(plant = plant, onClick = { onPlantClick(plant) }, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    if (state.isLoadingMore) {
                        item(key = "loading-more") {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(state: PlantsUiState, onFilterSelected: (PlantsFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            GardenFilterChip(
                text = stringResource(R.string.plants_filter_all),
                selected = state.filter == PlantsFilter.All,
                onClick = { onFilterSelected(PlantsFilter.All) },
            )
        }
        items(state.environments, key = { it.id }) { environment ->
            val filter = PlantsFilter.ByEnvironment(environment.id)
            GardenFilterChip(text = environment.name, selected = state.filter == filter, onClick = { onFilterSelected(filter) })
        }
        item(key = "archived") {
            GardenFilterChip(
                text = stringResource(R.string.plants_filter_archived),
                selected = state.filter == PlantsFilter.Archived,
                onClick = { onFilterSelected(PlantsFilter.Archived) },
            )
        }
    }
}

/** "5 plantas em Sala de estar". A edição do ambiente fica no lápis do card, na aba Ambientes. */
@Composable
private fun FilterCaption(environment: Environment, total: Int?) {
    if (total == null) return
    Text(
        text = if (total == 0) {
            stringResource(R.string.plants_in_environment_none, environment.name)
        } else {
            pluralStringResource(R.plurals.plants_in_environment, total, total, environment.name)
        },
        modifier = Modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyPlants(state: PlantsUiState) {
    val (title, description) = when {
        state.query.isNotBlank() -> R.string.plants_empty_search_title to R.string.plants_empty_search_description
        state.filter == PlantsFilter.Archived -> R.string.plants_empty_archived_title to R.string.plants_empty_archived_description
        state.filter is PlantsFilter.ByEnvironment -> R.string.plants_empty_environment_title to R.string.plants_empty_environment_description
        else -> R.string.plants_empty_title to R.string.plants_empty_description
    }
    EmptyState(
        icon = R.drawable.ic_leaf,
        title = stringResource(title),
        description = stringResource(description),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Linha de uma planta: inicial, título, o ambiente num chip (quando houver) e "nome popular · nome
 * científico" (o científico em itálico).
 */
@Composable
private fun PlantRow(plant: Plant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = plant.initial, style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = plant.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                plant.environment?.let { InfoChip(text = it.name, modifier = Modifier.padding(vertical = 2.dp)) }
                plant.species?.let { species ->
                    Text(
                        text = speciesLine(commonName = plant.secondaryCommonName, scientificName = species.scientificName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** "Costela-de-adão · *Monstera deliciosa*", ou só o nome científico quando não há nome popular a mostrar. */
internal fun speciesLine(commonName: String?, scientificName: String) = buildAnnotatedString {
    commonName?.let {
        append(it)
        append(" · ")
    }
    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(scientificName) }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun PlantsContentPreview() {
    val sala = Environment("e1", "Sala de estar", null, true, plantCount = 2)
    GardenTheme(darkTheme = false) {
        PlantsContent(
            state = PlantsUiState(
                filter = PlantsFilter.ByEnvironment(sala.id),
                environments = listOf(sala, Environment("e2", "Varanda", null, true, plantCount = 1)),
                list = PlantListState.Loaded(
                    plants = listOf(
                        Plant(
                            "p1", "Monstrinha", "Monstrinha", null, true,
                            PlantSpecies("s1", "Monstera deliciosa", "Costela-de-adão"), PlantEnvironment(sala.id, sala.name),
                        ),
                        Plant("p2", "Jiboia", null, null, true, PlantSpecies("s2", "Epipremnum aureum", "Jiboia"), null),
                    ),
                    total = 2,
                    page = 1,
                    hasMore = false,
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onQueryChange = {},
            onFilterSelected = {},
            onLoadMore = {},
            onRetry = {},
            onPlantClick = {},
            onNewPlant = {},
        )
    }
}
