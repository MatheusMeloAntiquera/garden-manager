package com.matheusantiquera.gardenmanager.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Abas da Agenda: o que falta fazer e o que já foi feito. */
enum class AgendaTab { Pending, History }

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val maintenanceRepository: MaintenanceRepository,
) : ViewModel() {

    private val _tab = MutableStateFlow(AgendaTab.Pending)
    val tab: StateFlow<AgendaTab> = _tab.asStateFlow()

    val pending = PagedList<MaintenanceSchedule>(viewModelScope) { maintenanceRepository.schedules(it) }
    val history = PagedList<MaintenanceLog>(viewModelScope) { maintenanceRepository.logs(it) }

    /** Total de atrasados, mesmo os que ainda não foram carregados na lista. Nulo até a primeira resposta. */
    private val _overdueTotal = MutableStateFlow<Int?>(null)
    val overdueTotal: StateFlow<Int?> = _overdueTotal.asStateFlow()

    val actions = MaintenanceActions(maintenanceRepository, viewModelScope, onDeleted = { load() })

    /**
     * Carrega (ou recarrega) a aba atual. A tela chama ao voltar a ficar visível, por exemplo depois de
     * um formulário; com a lista já na tela, a recarga é silenciosa e uma falha mantém o que já estava.
     */
    fun load() = reload(_tab.value, showLoading = false)

    fun onTabSelected(tab: AgendaTab) {
        if (tab == _tab.value) return
        _tab.value = tab
        reload(tab, showLoading = false)
    }

    fun loadMore() = when (_tab.value) {
        AgendaTab.Pending -> pending.loadMore()
        AgendaTab.History -> history.loadMore()
    }

    fun onScheduleClick(schedule: MaintenanceSchedule) = actions.openSheet(schedule)

    private fun reload(tab: AgendaTab, showLoading: Boolean) {
        when (tab) {
            AgendaTab.Pending -> {
                pending.reload(showLoading)
                viewModelScope.launch {
                    val result = maintenanceRepository.overdueCount()
                    if (result is ApiResult.Success) _overdueTotal.update { result.value }
                }
            }
            AgendaTab.History -> history.reload(showLoading)
        }
    }
}
