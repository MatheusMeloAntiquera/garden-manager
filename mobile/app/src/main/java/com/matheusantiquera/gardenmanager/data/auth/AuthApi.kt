package com.matheusantiquera.gardenmanager.data.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Rotas de autenticação, que não usam o access token. */
interface AuthApi {
    @POST("auth/signup")
    suspend fun signup(@Body body: SignupRequest): UserResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<Unit>
}

/** Rotas do usuário autenticado. */
interface UserApi {
    @GET("users/me")
    suspend fun me(): UserResponse
}
