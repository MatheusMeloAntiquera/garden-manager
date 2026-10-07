package com.matheusantiquera.gardenmanager.feature.plant.detail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.matheusantiquera.gardenmanager.core.ui.ShowOneShotMessage
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import com.matheusantiquera.gardenmanager.data.maintenance.PlantMaintenance
import com.matheusantiquera.gardenmanager.data.plant.Plant
import com.matheusantiquera.gardenmanager.data.plant.PlantEnvironment
import com.matheusantiquera.gardenmanager.data.plant.PlantSpecies
import com.matheusantiquera.gardenmanager.data.species.Species
import com.matheusantiquera.gardenmanager.data.species.SpeciesCategory
import com.matheusantiquera.gardenmanager.feature.maintenance.DueStatus
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceActionsHost
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceResult
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceTypeIcon
import com.matheusantiquera.gardenmanager.feature.maintenance.ShowMaintenanceResult
import com.matheusantiquera.gardenmanager.feature.maintenance.dueStatus
import com.matheusantiquera.gardenmanager.feature.maintenance.formatDueAt
import com.matheusantiquera.gardenmanager.feature.maintenance.formatPerformedAt
import com.matheusantiquera.gardenmanager.feature.maintenance.maintenanceTypeVisual
import com.matheusantiquera.gardenmanager.feature.plant.PlantFormResult
import com.matheusantiquera.gardenmanager.feature.plant.ShowPlantFormResult
import java.time.LocalDateTime

/**
 * Detalhe da planta. [formResult] é o que o formulário de edição deixou ao voltar e [maintenanceResult]
 * o de um formulário de manutenção; a tela mostra a mensagem e chama [onFormResultShown] ou
 * [onMaintenanceResultShown] para limpá-lo. Registrar e Agendar abrem os formulários de manutenção; tocar
 * num agendamento abre as ações dele e tocar numa execução abre o detalhe ([onOpenLog]). Numa planta
 * arquivada não há Registrar nem Agendar, os agendamentos não abrem ações e o detalhe da execução é só
 * para leitura.
 */
@Composable
fun PlantDetailScreen(
    formResult: PlantFormResult?,
    onFormResultShown: () -> Unit,
    maintenanceResult: MaintenanceResult?,
    onMaintenanceResultShown: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRegister: () -> Unit,
    onSchedule: () -> Unit,
    onCompleteSchedule: (MaintenanceSchedule) -> Unit,
    onEditSchedule: (MaintenanceSchedule) -> Unit,
    onOpenLog: (log: MaintenanceLog, readOnly: Boolean) -> Unit,
    viewModel: PlantDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions by viewModel.actions.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recarrega ao voltar dos formulários.
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }

    ShowPlantFormResult(result = formResult, onShown = onFormResultShown, snackbarHostState = snackbarHostState)
    ShowMaintenanceResult(result = maintenanceResult, onShown = onMaintenanceResultShown, snackbarHostState = snackbarHostState)
    ShowMaintenanceResult(result = actions.deleted, onShown = viewModel.actions::clearDeleted, snackbarHostState = snackbarHostState)
    ShowOneShotMessage(message = actions.error?.asString(), onShown = viewModel.actions::clearError, snackbarHostState = snackbarHostState)

    // Sem a barra inferior, a tela cuida da barra de navegação do sistema.
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        PlantDetailContent(
            state = state,
            onBack = onBack,
            onEdit = onEdit,
            onRetry = viewModel::load,
            onRegister = onRegister,
            onSchedule = onSchedule,
            onScheduleClick = viewModel::onScheduleClick,
            onLogClick = { log -> onOpenLog(log, (state as? PlantDetailUiState.Loaded)?.plant?.active == false) },
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    MaintenanceActionsHost(
        state = actions,
        onDismissSheet = viewModel.actions::dismissSheet,
        onComplete = onCompleteSchedule,
        onEditSchedule = onEditSchedule,
        onAskDelete = viewModel.actions::askDelete,
        onDismissDelete = viewModel.actions::dismissDelete,
        onConfirmDelete = viewModel.actions::confirmDelete,
    )
}

@Composable
internal fun PlantDetailContent(
    state: PlantDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onRegister: () -> Unit,
    onSchedule: () -> Unit,
    onScheduleClick: (MaintenanceSchedule) -> Unit,
    onLogClick: (MaintenanceLog) -> Unit,
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
            is PlantDetailUiState.Loaded -> Details(
                state = state,
                onRegister = onRegister,
                onSchedule = onSchedule,
                onScheduleClick = onScheduleClick,
                onLogClick = onLogClick,
            )
        }
    }
}

@Composable
private fun Details(
    state: PlantDetailUiState.Loaded,
    onRegister: () -> Unit,
    onSchedule: () -> Unit,
    onScheduleClick: (MaintenanceSchedule) -> Unit,
    onLogClick: (MaintenanceLog) -> Unit,
) {
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

        // Planta arquivada não recebe manutenção nova nem mexe nas que já tem: só leitura.
        if (plant.active) MaintenanceButtons(onRegister = onRegister, onSchedule = onSchedule)

        SchedulesSection(schedules = state.maintenance.schedules, onScheduleClick = onScheduleClick.takeIf { plant.active })
        HistorySection(logs = state.maintenance.logs, onLogClick = onLogClick)
    }
}

/** Registrar (o que foi feito agora) e Agendar (o que fazer depois), como no canvas. */
@Composable
private fun MaintenanceButtons(onRegister: () -> Unit, onSchedule: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onRegister, modifier = Modifier.weight(1f).height(48.dp), shape = CircleShape) {
            Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.plant_action_register), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
            onClick = onSchedule,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Icon(painter = painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.plant_action_schedule), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
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
private fun SchedulesSection(schedules: List<MaintenanceSchedule>, onScheduleClick: ((MaintenanceSchedule) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.plant_schedules_title))
        if (schedules.isEmpty()) EmptySectionText(stringResource(R.string.plant_schedules_empty))
        schedules.forEach { schedule -> ScheduleRow(schedule, onClick = onScheduleClick?.let { click -> { click(schedule) } }) }
    }
}

@Composable
private fun ScheduleRow(schedule: MaintenanceSchedule, onClick: (() -> Unit)?) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
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
            MaintenanceTypeIcon(typeName = schedule.typeName)
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
private fun HistorySection(logs: List<MaintenanceLog>, onLogClick: (MaintenanceLog) -> Unit) {
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
                    LogRow(log, onClick = { onLogClick(log) })
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: MaintenanceLog, onClick: () -> Unit) {
    val visual = maintenanceTypeVisual(log.typeName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                        MaintenanceSchedule("m1", "p1", "Monstrinha", "t1", "Rega", now.minusDays(2), overdue = true),
                        MaintenanceSchedule("m2", "p1", "Monstrinha", "t2", "Adubação", now.plusDays(12).toLocalDate().atStartOfDay(), overdue = false),
                    ),
                    scheduleTotal = 2,
                    logs = listOf(
                        MaintenanceLog("l1", "p1", "Monstrinha", "t1", "Rega", now.minusDays(9)),
                        MaintenanceLog("l2", "p1", "Monstrinha", "t3", "Poda", now.minusDays(21)),
                    ),
                    logTotal = 2,
                ),
            ),
            onBack = {},
            onEdit = {},
            onRetry = {},
            onRegister = {},
            onSchedule = {},
            onScheduleClick = {},
            onLogClick = {},
        )
    }
}
