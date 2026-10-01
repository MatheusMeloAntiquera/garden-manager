package com.matheusantiquera.gardenmanager.feature.auth.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.session.SessionManager
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.auth.AuthRepository
import com.matheusantiquera.gardenmanager.data.auth.SignupOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignupUiState(
    val name: String = "",
    val email: String = "",
    /** Só os dígitos da data (`ddMMaaaa`); a máscara com barras é só visual. */
    val birthDateDigits: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val nameError: UiText? = null,
    val emailError: UiText? = null,
    val birthDateError: UiText? = null,
    val passwordError: UiText? = null,
    val error: UiText? = null,
) {
    val passwordRules: SignupValidator.PasswordRules get() = SignupValidator.passwordRules(password)
}

/** Eventos únicos da tela de cadastro. */
sealed interface SignupEvent {
    /** A conta foi criada mas o login automático falhou: voltar ao login. */
    data object AccountCreatedWithoutLogin : SignupEvent
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    private val _events = Channel<SignupEvent>(Channel.BUFFERED)
    val events: Flow<SignupEvent> = _events.receiveAsFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null, error = null) }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null, error = null) }

    fun onBirthDateChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(BIRTH_DATE_DIGITS)
        _uiState.update { it.copy(birthDateDigits = digits, birthDateError = null, error = null) }
    }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordError = null, error = null) }

    fun onTogglePasswordVisibility() = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onSubmit() {
        val current = _uiState.value
        if (current.isLoading) return

        val nameError = if (SignupValidator.isValidName(current.name)) null else UiText.Resource(R.string.validation_name_invalid)
        val emailError = when {
            current.email.isBlank() -> UiText.Resource(R.string.validation_email_required)
            !SignupValidator.isValidEmail(current.email) -> UiText.Resource(R.string.validation_email_invalid)
            else -> null
        }
        val birthDate = SignupValidator.parseBirthDate(current.birthDateDigits)
        val birthDateError = when (birthDate) {
            is SignupValidator.BirthDateResult.Valid -> null
            SignupValidator.BirthDateResult.Invalid -> UiText.Resource(R.string.validation_birth_date_invalid)
            SignupValidator.BirthDateResult.OutOfRange -> UiText.Resource(R.string.validation_birth_date_range)
        }
        val passwordError = when {
            SignupValidator.isPasswordTooLong(current.password) -> UiText.Resource(R.string.validation_password_too_long)
            !current.passwordRules.allMet -> UiText.Resource(R.string.validation_password_weak)
            else -> null
        }

        if (nameError != null || emailError != null || birthDateError != null || passwordError != null || birthDate !is SignupValidator.BirthDateResult.Valid) {
            _uiState.update {
                it.copy(nameError = nameError, emailError = emailError, birthDateError = birthDateError, passwordError = passwordError)
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val outcome = authRepository.signup(current.name, current.email, current.password, birthDate.date)
            when (outcome) {
                // Com a sessão iniciada, a navegação sai da tela de cadastro sozinha.
                SignupOutcome.LoggedIn -> _uiState.update { it.copy(isLoading = false) }
                SignupOutcome.AccountCreated -> {
                    sessionManager.postNotice(UiText.Resource(R.string.notice_account_created))
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(SignupEvent.AccountCreatedWithoutLogin)
                }
                is SignupOutcome.Failed -> _uiState.update { applyFailure(it.copy(isLoading = false), outcome.error) }
            }
        }
    }

    /** Traduz o erro da API em mensagens nos campos (409 e erros de validação) ou no aviso geral. */
    private fun applyFailure(state: SignupUiState, error: ApiError): SignupUiState {
        if (error !is ApiError.Http) return state.copy(error = error.toUiText())

        if (error.code == 409) return state.copy(emailError = UiText.Resource(R.string.error_email_taken))

        if (error.code == 400 && error.details.isNotEmpty()) {
            var result = state
            for (detail in error.details) {
                result = when (detail.field.lowercase()) {
                    "name" -> result.copy(nameError = UiText.Resource(R.string.validation_name_invalid))
                    "email" -> result.copy(emailError = UiText.Resource(R.string.validation_email_invalid))
                    "birthdate" -> result.copy(birthDateError = UiText.Resource(R.string.validation_birth_date_range))
                    "password" -> result.copy(passwordError = UiText.Resource(R.string.validation_password_weak))
                    else -> result
                }
            }
            val anyFieldError = result.nameError != null || result.emailError != null ||
                result.birthDateError != null || result.passwordError != null
            return if (anyFieldError) result else state.copy(error = UiText.Resource(R.string.error_signup_invalid))
        }

        return state.copy(error = error.toUiText())
    }

    private companion object {
        const val BIRTH_DATE_DIGITS = 8
    }
}
