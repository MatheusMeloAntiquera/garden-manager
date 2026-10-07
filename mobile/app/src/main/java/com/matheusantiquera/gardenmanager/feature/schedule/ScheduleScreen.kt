package com.matheusantiquera.gardenmanager.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.EmptyState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadErrorState
import com.matheusantiquera.gardenmanager.core.designsystem.LoadingState
import com.matheusantiquera.gardenmanager.core.ui.ShowOneShotMessage
import com.matheusantiquera.gardenmanager.core.ui.asString
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import com.matheusantiquera.gardenmanager.feature.maintenance.AgendaGroup
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceActionsHost
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceResult
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceTypeIcon
import com.matheusantiquera.gardenmanager.feature.maintenance.ShowMaintenanceResult
import com.matheusantiquera.gardenmanager.feature.maintenance.agendaDue
import com.matheusantiquera.gardenmanager.feature.maintenance.agendaDueColor
import com.matheusantiquera.gardenmanager.feature.maintenance.agendaDueText
import com.matheusantiquera.gardenmanager.feature.maintenance.formatPerformedAt

/** Quantos itens antes do fim da lista a próxima página começa a carregar. */
private const val LOAD_MORE_THRESHOLD = 5

/**
 * Aba Agenda. [result] é o que um formulário de manutenção deixou ao voltar para esta tela; a tela
 * mostra a mensagem e chama [onResultShown] para limpá-lo. Concluir abre o registro já preenchido e tocar
 * numa execução do histórico abre o detalhe dela ([onLogClick]).
 */
@Composable
fun ScheduleScreen(
    result: MaintenanceResult?,
    onResultShown: () -> Unit,
    onComplete: (MaintenanceSchedule) -> Unit,
    onEditSchedule: (MaintenanceSchedule) -> Unit,
    onLogClick: (MaintenanceLog) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val pending by viewModel.pending.state.collectAsStateWithLifecycle()
    val history by viewModel.history.state.collectAsStateWithLifecycle()
    val overdueTotal by viewModel.overdueTotal.collectAsStateWithLifecycle()
    val actions by viewModel.actions.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recarrega sempre que a tela volta a aparecer (ao abrir, ao voltar de um formulário ou de outra aba).
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }

    ShowMaintenanceResult(result = result, onShown = onResultShown, snackbarHostState = snackbarHostState)
    ShowMaintenanceResult(result = actions.deleted, onShown = viewModel.actions::clearDeleted, snackbarHostState = snackbarHostState)
    ShowOneShotMessage(message = actions.error?.asString(), onShown = viewModel.actions::clearError, snackbarHostState = snackbarHostState)

    ScheduleContent(
        tab = tab,
        pending = pending,
        history = history,
        overdueTotal = overdueTotal,
        snackbarHostState = snackbarHostState,
        onTabSelected = viewModel::onTabSelected,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::load,
        onScheduleClick = viewModel::onScheduleClick,
        onLogClick = onLogClick,
        onComplete = onComplete,
    )

    MaintenanceActionsHost(
        state = actions,
        onDismissSheet = viewModel.actions::dismissSheet,
        onComplete = onComplete,
        onEditSchedule = onEditSchedule,
        onAskDelete = viewModel.actions::askDelete,
        onDismissDelete = viewModel.actions::dismissDelete,
        onConfirmDelete = viewModel.actions::confirmDelete,
    )
}

/** Uma linha da lista de pendentes: o título de um grupo ou um agendamento. */
private sealed interface PendingRow {
    data class Header(val group: AgendaGroup, val count: Int) : PendingRow
    data class Item(val schedule: MaintenanceSchedule) : PendingRow
}

/** Agrupa a lista (já ordenada por prazo) em Atrasadas, Esta semana e Mais tarde, sem os grupos vazios. */
private fun groupPending(items: List<MaintenanceSchedule>, overdueTotal: Int?): List<PendingRow> {
    val byGroup = items.groupBy { it.agendaDue().group }
    return AgendaGroup.entries.flatMap { group ->
        val inGroup = byGroup[group].orEmpty()
        if (inGroup.isEmpty()) {
            emptyList()
        } else {
            // O total de atrasadas vem da API: a lista carregada pode ter só as primeiras.
            val count = if (group == AgendaGroup.Overdue) overdueTotal ?: inGroup.size else inGroup.size
            listOf<PendingRow>(PendingRow.Header(group, count)) + inGroup.map { PendingRow.Item(it) }
        }
    }
}

@Composable
internal fun ScheduleContent(
    tab: AgendaTab,
    pending: PagedState<MaintenanceSchedule>,
    history: PagedState<MaintenanceLog>,
    overdueTotal: Int?,
    snackbarHostState: SnackbarHostState,
    onTabSelected: (AgendaTab) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onScheduleClick: (MaintenanceSchedule) -> Unit,
    onLogClick: (MaintenanceLog) -> Unit,
    onComplete: (MaintenanceSchedule) -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    val current = if (tab == AgendaTab.Pending) pending else history
    LaunchedEffect(nearEnd, current, tab) { if (nearEnd) onLoadMore() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.schedule_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TabSwitch(tab = tab, onTabSelected = onTabSelected)
                }
            }

            when (tab) {
                AgendaTab.Pending -> pendingContent(pending, overdueTotal, onRetry, onScheduleClick, onComplete)
                AgendaTab.History -> historyContent(history, onRetry, onLogClick)
            }

            if ((current as? PagedState.Loaded)?.isLoadingMore == true) {
                item(key = "loading-more") {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pendingContent(
    state: PagedState<MaintenanceSchedule>,
    overdueTotal: Int?,
    onRetry: () -> Unit,
    onScheduleClick: (MaintenanceSchedule) -> Unit,
    onComplete: (MaintenanceSchedule) -> Unit,
) {
    when (state) {
        PagedState.Loading -> item(key = "loading") { LoadingState(modifier = Modifier.fillMaxWidth()) }
        is PagedState.Failed -> item(key = "error") {
            LoadErrorState(
                title = stringResource(R.string.schedule_load_error),
                message = state.message.asString(),
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is PagedState.Loaded -> {
            if (state.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_calendar,
                        title = stringResource(R.string.schedule_empty_pending_title),
                        description = stringResource(R.string.schedule_empty_pending_description),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            groupPending(state.items, overdueTotal).forEach { row ->
                when (row) {
                    is PendingRow.Header -> item(key = "group-${row.group}") {
                        GroupHeader(group = row.group, count = row.count, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp))
                    }
                    is PendingRow.Item -> item(key = row.schedule.id) {
                        ScheduleRow(
                            schedule = row.schedule,
                            onClick = { onScheduleClick(row.schedule) },
                            onComplete = { onComplete(row.schedule) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyContent(
    state: PagedState<MaintenanceLog>,
    onRetry: () -> Unit,
    onLogClick: (MaintenanceLog) -> Unit,
) {
    when (state) {
        PagedState.Loading -> item(key = "loading") { LoadingState(modifier = Modifier.fillMaxWidth()) }
        is PagedState.Failed -> item(key = "error") {
            LoadErrorState(
                title = stringResource(R.string.schedule_load_error),
                message = state.message.asString(),
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is PagedState.Loaded -> {
            if (state.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_calendar,
                        title = stringResource(R.string.schedule_empty_history_title),
                        description = stringResource(R.string.schedule_empty_history_description),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(state.items.size, key = { state.items[it].id }) { index ->
                val log = state.items[index]
                LogRow(log = log, onClick = { onLogClick(log) }, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

/** Seletor Pendentes / Histórico do canvas: duas metades numa pílula com contorno. */
@Composable
private fun TabSwitch(tab: AgendaTab, onTabSelected: (AgendaTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
    ) {
        TabOption(text = stringResource(R.string.schedule_tab_pending), selected = tab == AgendaTab.Pending, modifier = Modifier.weight(1f)) {
            onTabSelected(AgendaTab.Pending)
        }
        // A borda externa é única e acompanha o arredondamento da pílula; entre as metades fica só um divisor.
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline))
        TabOption(text = stringResource(R.string.schedule_tab_history), selected = tab == AgendaTab.History, modifier = Modifier.weight(1f)) {
            onTabSelected(AgendaTab.History)
        }
    }
}

@Composable
private fun TabOption(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxSize()
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GroupHeader(group: AgendaGroup, count: Int, modifier: Modifier = Modifier) {
    val isOverdue = group == AgendaGroup.Overdue
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(
                when (group) {
                    AgendaGroup.Overdue -> R.string.schedule_group_overdue
                    AgendaGroup.ThisWeek -> R.string.schedule_group_this_week
                    AgendaGroup.Later -> R.string.schedule_group_later
                },
            ),
            style = MaterialTheme.typography.titleSmall,
            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isOverdue) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                Text(text = count.toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Agendamento: toque no cartão abre as ações; o ✓ à direita abre o registro (concluir). */
@Composable
private fun ScheduleRow(schedule: MaintenanceSchedule, onClick: () -> Unit, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val due = schedule.agendaDue()
    val overdue = schedule.overdue
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MaintenanceTypeIcon(typeName = schedule.typeName)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${schedule.typeName} · ${schedule.plantName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = agendaDueText(due), style = MaterialTheme.typography.bodySmall, color = agendaDueColor(due))
                }
            }
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                IconButton(onClick = onComplete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.schedule_complete, schedule.typeName, schedule.plantName),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Execução do histórico: "Tipo · Planta" e a data; o toque abre as ações. */
@Composable
private fun LogRow(log: MaintenanceLog, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MaintenanceTypeIcon(typeName = log.typeName)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${log.typeName} · ${log.plantName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatPerformedAt(log.performedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
