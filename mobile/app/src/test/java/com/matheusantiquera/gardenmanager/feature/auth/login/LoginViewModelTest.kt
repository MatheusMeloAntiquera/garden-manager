package com.matheusantiquera.gardenmanager.feature.auth.login

import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.session.SessionManager
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.auth.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val repository = mockk<AuthRepository>()
    private val sessionManager = mockk<SessionManager>(relaxed = true) {
        every { notice } returns MutableStateFlow(null)
    }
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LoginViewModel(repository, sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `campos vazios mostram erros e nao chamam a API`() {
        viewModel.onSubmit()

        val state = viewModel.uiState.value
        assertEquals(UiText.Resource(R.string.validation_email_required), state.emailError)
        assertEquals(UiText.Resource(R.string.validation_password_required), state.passwordError)
        assertFalse(state.isLoading)
        coVerify(exactly = 0) { repository.login(any(), any()) }
    }

    @Test
    fun `email invalido mostra erro`() {
        viewModel.onEmailChange("ana")
        viewModel.onPasswordChange("segredo")

        viewModel.onSubmit()

        assertEquals(UiText.Resource(R.string.validation_email_invalid), viewModel.uiState.value.emailError)
    }

    @Test
    fun `login com sucesso limpa o aviso e termina o carregamento`() {
        coEvery { repository.login("ana@email.com", "Sup3r\$ecret") } returns ApiResult.Success(Unit)
        viewModel.onEmailChange("ana@email.com")
        viewModel.onPasswordChange("Sup3r\$ecret")

        viewModel.onSubmit()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        verify { sessionManager.clearNotice() }
    }

    @Test
    fun `credenciais invalidas mostram a mensagem da API`() {
        coEvery { repository.login(any(), any()) } returns
            ApiResult.Failure(ApiError.Http(401, "e-mail ou senha inválidos"))
        viewModel.onEmailChange("ana@email.com")
        viewModel.onPasswordChange("errada")

        viewModel.onSubmit()

        val state = viewModel.uiState.value
        assertEquals(UiText.Dynamic("e-mail ou senha inválidos"), state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `falha de rede mostra a mensagem de conexao`() {
        coEvery { repository.login(any(), any()) } returns ApiResult.Failure(ApiError.Network)
        viewModel.onEmailChange("ana@email.com")
        viewModel.onPasswordChange("segredo")

        viewModel.onSubmit()

        assertEquals(UiText.Resource(R.string.error_network), viewModel.uiState.value.error)
    }
}
