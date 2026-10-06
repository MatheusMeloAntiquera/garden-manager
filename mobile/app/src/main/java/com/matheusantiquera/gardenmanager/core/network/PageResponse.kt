package com.matheusantiquera.gardenmanager.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Formato das listagens paginadas da API: `{ "data": [...], "page": 1, "page_size": 20, "total": 42 }`. */
@Serializable
data class PageResponse<T>(
    val data: List<T>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

/** Maior `page_size` aceito pela API. */
const val MAX_PAGE_SIZE = 100
