package com.matheusantiquera.gardenmanager.feature.plant.species

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.EmptyState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadErrorState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadingState
import com.matheusantiquera.gardenmanager.core.designsystem.SearchField
import com.matheusantiquera.gardenmanager.core.designsystem.SecondaryTopBar
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.data.species.Species
import com.matheusantiquera.gardenmanager.feature.plant.PickedSpecies
import com.matheusantiquera.gardenmanager.feature.plant.SpeciesPick

/** Quantos itens antes do fim da lista a próxima página começa a carregar. */
private const val LOAD_MORE_THRESHOLD = 5

/** Busca no catálogo de espécies. [onPick] recebe a escolha, inclusive "Sem espécie". */
@Composable
fun SpeciesPickerScreen(
    onClose: () -> Unit,
    onPick: (SpeciesPick) -> Unit,
    viewModel: SpeciesPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.results) { if (nearEnd) viewModel.loadMore() }

    // Sem a barra inferior, a tela cuida das barras do sistema e do teclado.
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        SecondaryTopBar(title = stringResource(R.string.species_picker_title), onNavigationClick = onClose)

        SearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.species_picker_search_placeholder),
            label = stringResource(R.string.species_picker_search_label),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "none") {
                OptionRow(
                    title = stringResource(R.string.species_picker_none),
                    subtitle = stringResource(R.string.species_picker_none_description),
                    selected = state.selectedId == null,
                    onClick = { onPick(SpeciesPick(species = null)) },
                )
            }

            when (val results = state.results) {
                SpeciesResultsState.Loading -> item(key = "loading") { LoadingState(modifier = Modifier.fillMaxWidth()) }

                is SpeciesResultsState.Failed -> item(key = "error") {
                    LoadErrorState(
                        title = stringResource(R.string.species_picker_load_error),
                        message = results.message.asString(),
                        onRetry = viewModel::search,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is SpeciesResultsState.Loaded -> {
                    if (results.species.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                icon = R.drawable.ic_search,
                                title = stringResource(R.string.species_picker_empty_title),
                                description = stringResource(R.string.species_picker_empty_description),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(results.species, key = { it.id }) { species ->
                        SpeciesRow(
                            species = species,
                            selected = species.id == state.selectedId,
                            onClick = {
                                onPick(SpeciesPick(PickedSpecies(species.id, species.scientificName, species.commonName)))
                            },
                        )
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

/** Nome popular em destaque e o científico em itálico; sem nome popular, o científico ocupa o título. */
@Composable
private fun SpeciesRow(species: Species, selected: Boolean, onClick: () -> Unit) {
    OptionRow(
        title = species.commonName ?: species.scientificName,
        subtitle = species.scientificName.takeIf { species.commonName != null },
        subtitleItalic = true,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    subtitleItalic: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.let { style -> if (subtitleItalic) style.copy(fontStyle = FontStyle.Italic) else style },
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.species_picker_selected),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
