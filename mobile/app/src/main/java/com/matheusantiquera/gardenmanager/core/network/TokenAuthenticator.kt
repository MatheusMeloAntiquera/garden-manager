package com.matheusantiquera.gardenmanager.core.network

import com.matheusantiquera.gardenmanager.core.datastore.AuthTokens
import com.matheusantiquera.gardenmanager.core.datastore.TokenStore
import com.matheusantiquera.gardenmanager.core.session.SessionManager
import com.matheusantiquera.gardenmanager.data.auth.AuthApi
import com.matheusantiquera.gardenmanager.data.auth.RefreshRequest
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException

/**
 * Renova o par de tokens quando uma requisição autenticada volta 401 e repete a requisição.
 *
 * A API rotaciona o refresh token a cada uso, então só uma renovação pode acontecer por vez: as
 * requisições que falham ao mesmo tempo esperam a primeira renovar e reaproveitam o resultado.
 * Se o refresh token foi recusado, a sessão é encerrada.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Já tentou renovar e o servidor continua recusando: desiste.
        if (response.priorResponseCount() >= MAX_ATTEMPTS) return null

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        synchronized(lock) {
            val current = runBlocking { tokenStore.tokens.first() } ?: return null

            // Outra requisição já renovou enquanto esta esperava: basta repetir com o token novo.
            if (current.accessToken != failedToken) return response.request.withToken(current.accessToken)

            val refreshed = try {
                runBlocking { authApi.refresh(RefreshRequest(current.refreshToken)) }
            } catch (e: HttpException) {
                // Refresh token recusado (expirado, revogado, conta bloqueada): o usuário precisa entrar de novo.
                runBlocking { tokenStore.clear() }
                sessionManager.onSessionExpired()
                return null
            } catch (e: IOException) {
                // Sem rede: mantém a sessão, o app tenta de novo depois.
                return null
            }

            runBlocking { tokenStore.save(AuthTokens(refreshed.accessToken, refreshed.refreshToken)) }
            return response.request.withToken(refreshed.accessToken)
        }
    }

    private fun Request.withToken(accessToken: String): Request =
        newBuilder().header("Authorization", "Bearer $accessToken").build()

    private fun Response.priorResponseCount(): Int {
        var count = 0
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
