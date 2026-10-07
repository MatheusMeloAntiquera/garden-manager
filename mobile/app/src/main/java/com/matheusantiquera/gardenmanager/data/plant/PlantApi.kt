package com.matheusantiquera.gardenmanager.data.plant

import com.matheusantiquera.gardenmanager.core.network.PageResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Rotas de plantas. Todas exigem o access token. */
interface PlantApi {
    /** Filtros nulos não restringem; `q` busca no apelido e nos nomes da espécie, sem acentos. */
    @GET("plants")
    suspend fun list(
        @Query("q") query: String?,
        @Query("environment_id") environmentId: String?,
        @Query("active") active: Boolean?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageResponse<PlantResponse>

    @GET("plants/{id}")
    suspend fun get(@Path("id") id: String): PlantResponse

    @POST("plants")
    suspend fun create(@Body body: PlantRequest): PlantResponse

    @PUT("plants/{id}")
    suspend fun update(@Path("id") id: String, @Body body: PlantRequest): PlantResponse

    @DELETE("plants/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}
