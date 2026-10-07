package com.matheusantiquera.gardenmanager.data.plant

import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/** Uma página da listagem de plantas e o total que atende aos filtros. */
data class PlantPage(
    val plants: List<Plant>,
    val page: Int,
    val total: Int,
) {
    val hasMore: Boolean get() = page * PLANTS_PAGE_SIZE < total
}

/** Plantas por página na lista. A aba carrega a próxima página ao chegar perto do fim. */
const val PLANTS_PAGE_SIZE = 20

/** Dados de uma planta para criar ou editar. Espera apelido e observações já normalizados. */
data class PlantInput(
    val nickname: String?,
    val speciesId: String?,
    val environmentId: String?,
    val notes: String?,
)

@Singleton
class PlantRepository @Inject constructor(
    private val api: PlantApi,
) {

    /** Uma página das plantas do usuário, ordenadas pelo nome de exibição. Filtros nulos não restringem. */
    suspend fun list(query: String?, environmentId: String?, active: Boolean?, page: Int): ApiResult<PlantPage> = apiCall {
        val response = api.list(
            query = query?.trim()?.ifEmpty { null },
            environmentId = environmentId,
            active = active,
            page = page,
            pageSize = PLANTS_PAGE_SIZE,
        )
        PlantPage(plants = response.data.map { it.toPlant() }, page = response.page, total = response.total)
    }

    suspend fun get(id: String): ApiResult<Plant> = apiCall { api.get(id).toPlant() }

    /** Cria a planta ativa. */
    suspend fun create(input: PlantInput): ApiResult<Plant> = apiCall {
        api.create(input.toRequest(active = null)).toPlant()
    }

    /** Substitui todos os dados da planta, inclusive o status. */
    suspend fun update(id: String, input: PlantInput, active: Boolean): ApiResult<Plant> = apiCall {
        api.update(id, input.toRequest(active = active)).toPlant()
    }

    /** Exclui a planta junto com os agendamentos e o histórico de manutenções dela. */
    suspend fun delete(id: String): ApiResult<Unit> = apiCall {
        val response = api.delete(id)
        if (!response.isSuccessful) throw HttpException(response)
    }

    private fun PlantInput.toRequest(active: Boolean?) = PlantRequest(
        speciesId = speciesId,
        environmentId = environmentId,
        nickname = nickname,
        notes = notes,
        active = active,
    )
}
