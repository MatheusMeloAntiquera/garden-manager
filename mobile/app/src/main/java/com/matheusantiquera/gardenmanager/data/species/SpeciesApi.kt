package com.matheusantiquera.gardenmanager.data.species

import com.matheusantiquera.gardenmanager.core.network.PageResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Catálogo de espécies, somente leitura. Exige o access token. */
interface SpeciesApi {
    /** Ordenadas pelo nome popular principal; `q` busca nos nomes populares e no científico, sem acentos. */
    @GET("species")
    suspend fun search(
        @Query("q") query: String?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PageResponse<SpeciesResponse>

    @GET("species/{id}")
    suspend fun get(@Path("id") id: String): SpeciesResponse
}
