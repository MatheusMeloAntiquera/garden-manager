package com.matheusantiquera.gardenmanager.data.species

import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Uma página da busca no catálogo e o total encontrado. */
data class SpeciesPage(
    val species: List<Species>,
    val page: Int,
    val total: Int,
) {
    val hasMore: Boolean get() = page * SPECIES_PAGE_SIZE < total
}

/** Espécies por página na busca. O catálogo tem cerca de 200, então poucas páginas bastam. */
const val SPECIES_PAGE_SIZE = 30

@Singleton
class SpeciesRepository @Inject constructor(
    private val api: SpeciesApi,
) {

    /** Busca no catálogo. Texto em branco lista todas, pelo nome popular principal. */
    suspend fun search(query: String, page: Int): ApiResult<SpeciesPage> = apiCall {
        val response = api.search(query = query.trim().ifEmpty { null }, page = page, pageSize = SPECIES_PAGE_SIZE)
        SpeciesPage(species = response.data.map { it.toSpecies() }, page = response.page, total = response.total)
    }

    suspend fun get(id: String): ApiResult<Species> = apiCall { api.get(id).toSpecies() }
}
