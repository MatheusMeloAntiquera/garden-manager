package com.matheusantiquera.gardenmanager.core.network

import com.matheusantiquera.gardenmanager.core.datastore.TokenStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Envia o access token atual em `Authorization: Bearer`, quando há sessão. */
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = runBlocking { tokenStore.tokens.first() }?.accessToken
        val request = if (accessToken == null) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $accessToken").build()
        }
        return chain.proceed(request)
    }
}
