package com.matheusantiquera.gardenmanager.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.session.SessionManager
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.auth.AuthRepository
import com.matheusantiquera.gardenmanager.feature.auth.signup.SignupValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    /** Erro geral do envio (credenciais inválidas, conta bloqueada, sem rede...). */
    val error: UiText? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** Aviso vindo de fora da tela (sessão expirada, conta recém-criada). */
    val notice: StateFlow<UiText?> = sessionManager.notice

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordError = null, error = null) }

    fun onTogglePasswordVisibility() = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onSubmit() {
        val current = _uiState.value
        if (current.isLoading) return

        val emailError = when {
            current.email.isBlank() -> UiText.Resource(R.string.validation_email_required)
            !SignupValidator.isValidEmail(current.email) -> UiText.Resource(R.string.validation_email_invalid)
            else -> null
        }
        val passwordError = if (current.password.isEmpty()) UiText.Resource(R.string.validation_password_required) else null
        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        sessionManager.clearNotice()
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = authRepository.login(current.email, current.password)
            _uiState.update {
                when (result) {
                    // Com a sessão iniciada, a navegação sai da tela de login sozinha.
                    is ApiResult.Success -> it.copy(isLoading = false)
                    is ApiResult.Failure -> it.copy(isLoading = false, error = result.error.toUiText())
                }
            }
        }
    }
}
