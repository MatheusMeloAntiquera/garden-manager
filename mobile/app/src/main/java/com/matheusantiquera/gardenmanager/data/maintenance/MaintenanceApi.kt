package com.matheusantiquera.gardenmanager.data.maintenance

import com.matheusantiquera.gardenmanager.core.network.PageResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Rotas de manutenção. Todas exigem o access token. */
interface MaintenanceApi {
    /** Catálogo de tipos (Rega, Poda...), ordenado por nome e sem paginação. */
    @GET("maintenance-types")
    suspend fun types(): MaintenanceTypeListResponse

    /**
     * Agendamentos do usuário, do prazo mais antigo ao mais novo. [status] é pending ou overdue; [plantActive]
     * restringe a plantas ativas (true) ou arquivadas (false), e nulo traz as duas.
     */
    @GET("maintenance-schedules")
    suspend fun schedules(
        @Query("status") status: String?,
        @Query("plant_active") plantActive: Boolean?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageResponse<ScheduleResponse>

    /** Agendamentos da planta, do prazo mais antigo ao mais novo. */
    @GET("plants/{id}/maintenance-schedules")
    suspend fun plantSchedules(
        @Path("id") plantId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageResponse<ScheduleResponse>

    @GET("maintenance-schedules/{id}")
    suspend fun schedule(@Path("id") id: String): ScheduleResponse

    @POST("maintenance-schedules")
    suspend fun createSchedule(@Body body: ScheduleRequest): ScheduleResponse

    @PUT("maintenance-schedules/{id}")
    suspend fun updateSchedule(@Path("id") id: String, @Body body: ScheduleRequest): ScheduleResponse

    @DELETE("maintenance-schedules/{id}")
    suspend fun deleteSchedule(@Path("id") id: String): Response<Unit>

    /** Execuções do usuário, da mais recente à mais antiga. [plantActive] como em [schedules]. */
    @GET("maintenance-logs")
    suspend fun logs(
        @Query("plant_active") plantActive: Boolean?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageResponse<LogResponse>

    /** Execuções registradas da planta, da mais recente à mais antiga. */
    @GET("plants/{id}/maintenance-logs")
    suspend fun plantLogs(
        @Path("id") plantId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageResponse<LogResponse>

    @GET("maintenance-logs/{id}")
    suspend fun log(@Path("id") id: String): LogResponse

    @POST("maintenance-logs")
    suspend fun createLog(@Body body: CreateLogRequest): LogResponse

    @PUT("maintenance-logs/{id}")
    suspend fun updateLog(@Path("id") id: String, @Body body: UpdateLogRequest): LogResponse

    @DELETE("maintenance-logs/{id}")
    suspend fun deleteLog(@Path("id") id: String): Response<Unit>
}
