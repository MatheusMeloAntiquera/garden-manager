package com.matheusantiquera.gardenmanager.data.maintenance

import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.MAX_PAGE_SIZE
import com.matheusantiquera.gardenmanager.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Singleton
class MaintenanceRepository @Inject constructor(
    private val api: MaintenanceApi,
) {

    /**
     * Agendamentos e histórico de uma planta, buscados em paralelo. Traz até [MAX_PAGE_SIZE] de cada um
     * (os agendamentos mais próximos e os registros mais recentes); os totais contam todos.
     */
    suspend fun forPlant(plantId: String): ApiResult<PlantMaintenance> = apiCall {
        coroutineScope {
            val schedules = async { api.plantSchedules(plantId, page = 1, pageSize = MAX_PAGE_SIZE) }
            val logs = async { api.plantLogs(plantId, page = 1, pageSize = MAX_PAGE_SIZE) }
            val schedulePage = schedules.await()
            val logPage = logs.await()
            PlantMaintenance(
                schedules = schedulePage.data.map { it.toSchedule() },
                scheduleTotal = schedulePage.total,
                logs = logPage.data.map { it.toLog() },
                logTotal = logPage.total,
            )
        }
    }
}
