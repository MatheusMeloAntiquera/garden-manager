package com.matheusantiquera.gardenmanager.data.environment

import com.matheusantiquera.gardenmanager.core.network.PageResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Rotas de ambientes. Todas exigem o access token. */
interface EnvironmentApi {
    /** `active` nulo lista ativos e inativos. */
    @GET("environments")
    suspend fun list(
        @Query("active") active: Boolean?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageResponse<EnvironmentResponse>

    @GET("environments/{id}")
    suspend fun get(@Path("id") id: String): EnvironmentResponse

    @POST("environments")
    suspend fun create(@Body body: EnvironmentRequest): EnvironmentResponse

    @PUT("environments/{id}")
    suspend fun update(@Path("id") id: String, @Body body: EnvironmentRequest): EnvironmentResponse

    @DELETE("environments/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}
