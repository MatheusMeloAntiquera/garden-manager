package com.matheusantiquera.gardenmanager.core.network

import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.ui.UiText
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

/** Configuração de JSON compartilhada pela API e pelo parser de erros. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Formato padrão de erro da API: `{ "error": "...", "details": [...] }`. */
@Serializable
data class ErrorResponse(
    @SerialName("error") val error: String? = null,
    @SerialName("details") val details: JsonElement? = null,
)

/** Um item de `details` de erro de validação, no formato `Campo: regra` (ex.: `BirthDate: birthdate`). */
data class ValidationDetail(val field: String, val rule: String)

sealed interface ApiError {
    /** A API respondeu com um status de erro. */
    data class Http(
        val code: Int,
        val message: String?,
        val details: List<ValidationDetail> = emptyList(),
    ) : ApiError

    /** Sem conexão, timeout ou servidor inacessível. */
    data object Network : ApiError

    data class Unknown(val cause: Throwable) : ApiError
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Executa uma chamada de rede convertendo as falhas em [ApiResult.Failure]. */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.Failure(parseApiError(e.code(), e.response()?.errorBody()?.string()))
} catch (e: IOException) {
    ApiResult.Failure(ApiError.Network)
} catch (e: Exception) {
    ApiResult.Failure(ApiError.Unknown(e))
}

/** Converte o corpo de uma resposta de erro da API em [ApiError.Http]. */
fun parseApiError(code: Int, body: String?): ApiError.Http {
    val parsed = body
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { ApiJson.decodeFromString<ErrorResponse>(it) }.getOrNull() }

    val details = (parsed?.details as? JsonArray)
        .orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        .mapNotNull { line ->
            val separator = line.indexOf(": ")
            if (separator <= 0) null else ValidationDetail(line.substring(0, separator), line.substring(separator + 2))
        }

    return ApiError.Http(code = code, message = parsed?.error, details = details)
}

fun ApiError.toUiText(): UiText = when (this) {
    is ApiError.Http -> message?.takeIf { it.isNotBlank() }?.let { UiText.Dynamic(it) }
        ?: UiText.Resource(R.string.error_unknown)
    ApiError.Network -> UiText.Resource(R.string.error_network)
    is ApiError.Unknown -> UiText.Resource(R.string.error_unknown)
}
