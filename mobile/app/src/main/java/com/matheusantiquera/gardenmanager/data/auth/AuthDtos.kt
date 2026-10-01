package com.matheusantiquera.gardenmanager.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val name: String,
    val email: String,
    val password: String,
    @SerialName("birth_date") val birthDate: String, // AAAA-MM-DD
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class LogoutRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
    @SerialName("birth_date") val birthDate: String? = null, // AAAA-MM-DD; nulo em contas antigas
    val active: Boolean,
    @SerialName("created_at") val createdAt: String,
)
