package com.matheusantiquera.gardenmanager.data.maintenance

import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.MAX_PAGE_SIZE
import com.matheusantiquera.gardenmanager.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

@Singleton
class MaintenanceRepository @Inject constructor(
    private val api: MaintenanceApi,
) {

    // O catálogo é fixo (vem de migration): uma busca bem-sucedida serve até o app fechar.
    @Volatile
    private var cachedTypes: List<MaintenanceType>? = null

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

    /** Tipos de manutenção, ordenados por nome. */
    suspend fun types(): ApiResult<List<MaintenanceType>> {
        cachedTypes?.let { return ApiResult.Success(it) }
        return apiCall { api.types().data.map { it.toType() } }.also { result ->
            if (result is ApiResult.Success) cachedTypes = result.value
        }
    }

    /**
     * Uma página dos agendamentos das plantas ativas do usuário, do prazo mais próximo ao mais distante. As
     * plantas arquivadas ficam de fora: a Agenda não mostra o que é delas.
     */
    suspend fun schedules(page: Int): ApiResult<MaintenancePage<MaintenanceSchedule>> = apiCall {
        val response = api.schedules(status = null, plantActive = true, page = page, pageSize = MAINTENANCE_PAGE_SIZE)
        MaintenancePage(response.data.map { it.toSchedule() }, response.page, response.total)
    }

    /** Quantos agendamentos de plantas ativas estão atrasados, no total (não só os já carregados). */
    suspend fun overdueCount(): ApiResult<Int> = apiCall {
        api.schedules(status = "overdue", plantActive = true, page = 1, pageSize = 1).total
    }

    /** Uma página das execuções das plantas ativas do usuário, da mais recente à mais antiga. */
    suspend fun logs(page: Int): ApiResult<MaintenancePage<MaintenanceLog>> = apiCall {
        val response = api.logs(plantActive = true, page = page, pageSize = MAINTENANCE_PAGE_SIZE)
        MaintenancePage(response.data.map { it.toLog() }, response.page, response.total)
    }

    suspend fun getSchedule(id: String): ApiResult<MaintenanceSchedule> = apiCall { api.schedule(id).toSchedule() }

    suspend fun createSchedule(input: ScheduleInput): ApiResult<MaintenanceSchedule> = apiCall {
        api.createSchedule(input.toRequest()).toSchedule()
    }

    /** Substitui planta, tipo, prazo e observações do agendamento. */
    suspend fun updateSchedule(id: String, input: ScheduleInput): ApiResult<MaintenanceSchedule> = apiCall {
        api.updateSchedule(id, input.toRequest()).toSchedule()
    }

    suspend fun deleteSchedule(id: String): ApiResult<Unit> = apiCall {
        val response = api.deleteSchedule(id)
        if (!response.isSuccessful) throw HttpException(response)
    }

    suspend fun getLog(id: String): ApiResult<MaintenanceLog> = apiCall { api.log(id).toLog() }

    /**
     * Registra uma execução. Com scheduleId, a API a vincula ao agendamento e o exclui. Observações em
     * branco não são enviadas: nesse caso a API herda as do agendamento.
     */
    suspend fun createLog(input: LogInput): ApiResult<MaintenanceLog> = apiCall {
        val request = CreateLogRequest(
            plantId = input.plantId,
            typeId = input.typeId,
            scheduleId = input.scheduleId,
            performedAt = formatMaintenanceDateTime(input.performedAt),
            notes = input.notes,
        )
        api.createLog(request).toLog()
    }

    /** Substitui planta, tipo, data e observações da execução. */
    suspend fun updateLog(id: String, input: LogInput): ApiResult<MaintenanceLog> = apiCall {
        val request = UpdateLogRequest(
            plantId = input.plantId,
            typeId = input.typeId,
            performedAt = formatMaintenanceDateTime(input.performedAt),
            notes = input.notes,
        )
        api.updateLog(id, request).toLog()
    }

    suspend fun deleteLog(id: String): ApiResult<Unit> = apiCall {
        val response = api.deleteLog(id)
        if (!response.isSuccessful) throw HttpException(response)
    }

    private fun ScheduleInput.toRequest() = ScheduleRequest(
        plantId = plantId,
        typeId = typeId,
        dueAt = formatMaintenanceDateTime(dueAt),
        notes = notes,
    )
}
