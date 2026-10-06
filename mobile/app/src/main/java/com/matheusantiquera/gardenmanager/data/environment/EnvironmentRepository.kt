package com.matheusantiquera.gardenmanager.data.environment

import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.MAX_PAGE_SIZE
import com.matheusantiquera.gardenmanager.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnvironmentRepository @Inject constructor(
    private val api: EnvironmentApi,
) {

    /**
     * Todos os ambientes do usuário, ordenados por nome. Um usuário tem poucos ambientes, então a lista
     * vem inteira, buscando as páginas em sequência. `active` nulo traz ativos e inativos.
     */
    suspend fun listAll(active: Boolean?): ApiResult<List<Environment>> = apiCall {
        val all = mutableListOf<Environment>()
        var page = 1
        do {
            val response = api.list(active = active, page = page, pageSize = MAX_PAGE_SIZE)
            all += response.data.map { it.toEnvironment() }
            page++
        } while (response.data.isNotEmpty() && all.size < response.total)
        all
    }

    suspend fun get(id: String): ApiResult<Environment> = apiCall { api.get(id).toEnvironment() }

    /** Cria o ambiente ativo. Espera nome e observações já normalizados. */
    suspend fun create(name: String, notes: String?): ApiResult<Environment> = apiCall {
        api.create(EnvironmentRequest(name = name, notes = notes)).toEnvironment()
    }

    /** Substitui nome, observações e status. Espera nome e observações já normalizados. */
    suspend fun update(id: String, name: String, notes: String?, active: Boolean): ApiResult<Environment> = apiCall {
        api.update(id, EnvironmentRequest(name = name, notes = notes, active = active)).toEnvironment()
    }

    /** Exclui o ambiente. As plantas dele não são apagadas: ficam sem ambiente. */
    suspend fun delete(id: String): ApiResult<Unit> = apiCall {
        val response = api.delete(id)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }
}
