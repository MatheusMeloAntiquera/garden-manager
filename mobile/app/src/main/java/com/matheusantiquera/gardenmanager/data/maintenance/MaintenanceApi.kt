package com.matheusantiquera.gardenmanager.data.maintenance

import com.matheusantiquera.gardenmanager.core.network.PageResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Rotas de manutenção de uma planta. Todas exigem o access token. */
interface MaintenanceApi {
    /** Agendamentos da planta, do prazo mais antigo ao mais novo. */
    @GET("plants/{id}/maintenance-schedules")
    suspend fun plantSchedules(
        @Path("id") plantId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageResponse<ScheduleResponse>

    /** Execuções registradas da planta, da mais recente à mais antiga. */
    @GET("plants/{id}/maintenance-logs")
    suspend fun plantLogs(
        @Path("id") plantId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageResponse<LogResponse>
}
