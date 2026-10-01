package com.matheusantiquera.gardenmanager.data.auth

import com.matheusantiquera.gardenmanager.core.datastore.AuthTokens
import com.matheusantiquera.gardenmanager.core.datastore.TokenStore
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.apiCall
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Resultado do cadastro, que cria a conta e em seguida entra com ela. */
sealed interface SignupOutcome {
    /** Conta criada e sessão iniciada. */
    data object LoggedIn : SignupOutcome

    /** A conta foi criada, mas o login automático falhou (ex.: rede caiu no meio). */
    data object AccountCreated : SignupOutcome

    data class Failed(val error: ApiError) : SignupOutcome
}

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val tokenStore: TokenStore,
) {

    suspend fun login(email: String, password: String): ApiResult<Unit> = apiCall {
        val tokens = authApi.login(LoginRequest(email = email.trim(), password = password))
        tokenStore.save(AuthTokens(tokens.accessToken, tokens.refreshToken))
    }

    /** O signup não devolve tokens, então o app entra com as mesmas credenciais logo depois. */
    suspend fun signup(name: String, email: String, password: String, birthDate: LocalDate): SignupOutcome {
        val trimmedEmail = email.trim()
        val created = apiCall {
            authApi.signup(
                SignupRequest(
                    name = name.trim(),
                    email = trimmedEmail,
                    password = password,
                    birthDate = birthDate.toString(),
                ),
            )
        }
        if (created is ApiResult.Failure) return SignupOutcome.Failed(created.error)

        return when (login(trimmedEmail, password)) {
            is ApiResult.Success -> SignupOutcome.LoggedIn
            is ApiResult.Failure -> SignupOutcome.AccountCreated
        }
    }

    suspend fun currentUser(): ApiResult<User> = apiCall { userApi.me().toUser() }

    /** Revoga o refresh token no servidor (se possível) e apaga a sessão local de qualquer jeito. */
    suspend fun logout() = withContext(NonCancellable) {
        val refreshToken = tokenStore.tokens.first()?.refreshToken
        if (refreshToken != null) {
            apiCall { authApi.logout(LogoutRequest(refreshToken)) }
        }
        tokenStore.clear()
    }
}
