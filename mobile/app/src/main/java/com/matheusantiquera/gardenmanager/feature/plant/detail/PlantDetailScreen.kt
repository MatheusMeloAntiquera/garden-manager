package com.matheusantiquera.gardenmanager.feature.plant.detail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTheme
import com.matheusantiquera.gardenmanager.core.designsystem.InfoChip
import com.matheusantiquera.gardenmanager.core.designsystem.LoadErrorState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadingState
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import com.matheusantiquera.gardenmanager.data.maintenance.PlantMaintenance
import com.matheusantiquera.gardenmanager.data.plant.Plant
import com.matheusantiquera.gardenmanager.data.plant.PlantEnvironment
import com.matheusantiquera.gardenmanager.data.plant.PlantSpecies
import com.matheusantiquera.gardenmanager.data.species.Species
import com.matheusantiquera.gardenmanager.data.species.SpeciesCategory
import com.matheusantiquera.gardenmanager.feature.plant.PlantFormResult
import com.matheusantiquera.gardenmanager.feature.plant.ShowPlantFormResult
import java.time.LocalDateTime

/**
 * Detalhe da planta. [formResult] é o que o formulário de edição deixou ao voltar; a tela mostra a
 * mensagem e chama [onFormResultShown] para limpá-lo. Registrar e Agendar entram com a Agenda.
 */
@Composable
fun PlantDetailScreen(
    formResult: PlantFormResult?,
    onFormResultShown: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: PlantDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recarrega ao voltar do formulário de edição.
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }

    ShowPlantFormResult(result = formResult, onShown = onFormResultShown, snackbarHostState = snackbarHostState)

    // Sem a barra inferior, a tela cuida da barra de navegação do sistema.
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        PlantDetailContent(state = state, onBack = onBack, onEdit = onEdit, onRetry = viewModel::load)
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun PlantDetailContent(
    state: PlantDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (state is PlantDetailUiState.Loaded) {
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.plant_edit),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }

        when (state) {
            PlantDetailUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is PlantDetailUiState.Failed -> LoadErrorState(
                title = stringResource(R.string.plant_load_error),
                message = state.message.asString(),
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            is PlantDetailUiState.Loaded -> Details(state)
        }
    }
}

@Composable
private fun Details(state: PlantDetailUiState.Loaded) {
    val plant = state.plant
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header(plant = plant, species = state.species)

        val category = state.species?.category
        if (plant.environment != null || category != null || !plant.active) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plant.environment?.let { DetailChip(text = it.name, icon = R.drawable.ic_home) }
                category?.let { DetailChip(text = stringResource(it.label)) }
                if (!plant.active) DetailChip(text = stringResource(R.string.plant_archived))
            }
        }

        plant.notes?.let { notes ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text = notes,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SchedulesSection(schedules = state.maintenance.schedules)
        HistorySection(logs = state.maintenance.logs)
    }
}

/** Ícone em caixa, título, nome popular e "científico · família". */
@Composable
private fun Header(plant: Plant, species: Species?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.size(76.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.ic_leaf), contentDescription = null, modifier = Modifier.size(38.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = plant.displayName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            plant.secondaryCommonName?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            plant.species?.let { ref ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(ref.scientificName) }
                        species?.family?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailChip(text: String, @DrawableRes icon: Int? = null) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.let { Icon(painter = painterResource(it), contentDescription = null, modifier = Modifier.size(15.dp)) }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun EmptySectionText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SchedulesSection(schedules: List<MaintenanceSchedule>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.plant_schedules_title))
        if (schedules.isEmpty()) EmptySectionText(stringResource(R.string.plant_schedules_empty))
        schedules.forEach { ScheduleRow(it) }
    }
}

@Composable
private fun ScheduleRow(schedule: MaintenanceSchedule) {
    val visual = maintenanceTypeVisual(schedule.typeName)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = visual.container, contentColor = visual.content) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painter = painterResource(visual.icon), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = schedule.typeName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = stringResource(R.string.plant_schedule_due, formatDueAt(schedule.dueAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DueStatusChip(schedule.dueStatus())
        }
    }
}

@Composable
private fun DueStatusChip(status: DueStatus) {
    val colors = GardenTheme.colors
    val text = when (status) {
        DueStatus.Overdue -> stringResource(R.string.plant_due_overdue)
        DueStatus.Today -> stringResource(R.string.plant_due_today)
        DueStatus.Tomorrow -> stringResource(R.string.plant_due_tomorrow)
        is DueStatus.InDays -> pluralStringResource(R.plurals.plant_due_in_days, status.days.toInt(), status.days.toInt())
    }
    // Atrasada em vermelho, hoje e amanhã em âmbar ("pendente"), o resto neutro, como no canvas.
    when (status) {
        DueStatus.Overdue -> InfoChip(text, containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
        DueStatus.Today, DueStatus.Tomorrow -> InfoChip(text, containerColor = colors.pendingContainer, contentColor = colors.pending)
        is DueStatus.InDays -> InfoChip(text)
    }
}

@Composable
private fun HistorySection(logs: List<MaintenanceLog>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.plant_history_title))
        if (logs.isEmpty()) {
            EmptySectionText(stringResource(R.string.plant_history_empty))
            return@Column
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                logs.forEachIndexed { index, log ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LogRow(log)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: MaintenanceLog) {
    val visual = maintenanceTypeVisual(log.typeName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painter = painterResource(visual.icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = visual.content)
        Text(
            text = log.typeName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = formatPerformedAt(log.performedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ícone e cores de um tipo de manutenção, como no canvas. Tipo desconhecido usa o calendário. */
private data class MaintenanceTypeVisual(@DrawableRes val icon: Int, val container: Color, val content: Color)

@Composable
private fun maintenanceTypeVisual(typeName: String): MaintenanceTypeVisual {
    val scheme = MaterialTheme.colorScheme
    val garden = GardenTheme.colors
    return when (typeName.lowercase()) {
        "rega" -> MaintenanceTypeVisual(R.drawable.ic_water, garden.waterContainer, garden.water)
        "adubação" -> MaintenanceTypeVisual(R.drawable.ic_sprout, scheme.primaryContainer, scheme.primary)
        "poda" -> MaintenanceTypeVisual(R.drawable.ic_scissors, scheme.primaryContainer, scheme.primary)
        "replante" -> MaintenanceTypeVisual(R.drawable.ic_leaf, scheme.primaryContainer, scheme.primary)
        "mudança de ambiente" -> MaintenanceTypeVisual(R.drawable.ic_home, scheme.surfaceVariant, scheme.onSurfaceVariant)
        else -> MaintenanceTypeVisual(R.drawable.ic_calendar, scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PlantDetailPreview() {
    val now = LocalDateTime.now()
    GardenTheme(darkTheme = false) {
        PlantDetailContent(
            state = PlantDetailUiState.Loaded(
                plant = Plant(
                    "p1", "Monstrinha", "Monstrinha", "Perto da janela, luz indireta.", true,
                    PlantSpecies("s1", "Monstera deliciosa", "Costela-de-adão"), PlantEnvironment("e1", "Sala de estar"),
                ),
                species = Species("s1", "Monstera deliciosa", "Araceae", SpeciesCategory.Foliage, "Costela-de-adão"),
                maintenance = PlantMaintenance(
                    schedules = listOf(
                        MaintenanceSchedule("m1", "Rega", now.minusDays(2), overdue = true),
                        MaintenanceSchedule("m2", "Adubação", now.plusDays(12).toLocalDate().atStartOfDay(), overdue = false),
                    ),
                    scheduleTotal = 2,
                    logs = listOf(MaintenanceLog("l1", "Rega", now.minusDays(9)), MaintenanceLog("l2", "Poda", now.minusDays(21))),
                    logTotal = 2,
                ),
            ),
            onBack = {},
            onEdit = {},
            onRetry = {},
        )
    }
}
