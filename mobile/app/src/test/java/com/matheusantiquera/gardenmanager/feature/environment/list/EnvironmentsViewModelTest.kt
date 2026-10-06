package com.matheusantiquera.gardenmanager.feature.environment.list

import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.auth.AuthRepository
import com.matheusantiquera.gardenmanager.data.auth.User
import com.matheusantiquera.gardenmanager.data.environment.Environment
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnvironmentsViewModelTest {

    private val environmentRepository = mockk<EnvironmentRepository>()
    private val authRepository = mockk<AuthRepository>()
    private lateinit var viewModel: EnvironmentsViewModel

    private val sala = Environment("1", "Sala", null, true, plantCount = 2, overdueCount = 1)
    private val varanda = Environment("2", "Varanda", "Sol da tarde", true, plantCount = 0)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { environmentRepository.listAll(active = true) } returns ApiResult.Success(listOf(sala, varanda))
        coEvery { authRepository.currentUser() } returns ApiResult.Success(User("u1", "Ana Ribeiro", "ana@email.com", null))
        viewModel = EnvironmentsViewModel(environmentRepository, authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `comeca carregando e so busca ao chamar load`() {
        assertEquals(EnvironmentListState.Loading, viewModel.uiState.value.list)
        coVerify(exactly = 0) { environmentRepository.listAll(any()) }
    }

    @Test
    fun `load traz o primeiro nome e so os ambientes ativos, com as contagens`() {
        viewModel.load()

        val state = viewModel.uiState.value
        assertEquals("Ana", state.firstName)
        assertEquals(EnvironmentListState.Loaded(listOf(sala, varanda)), state.list)
        coVerify { environmentRepository.listAll(active = true) }
    }

    @Test
    fun `o usuario e buscado uma vez so, mesmo recarregando`() {
        viewModel.load()
        viewModel.load()

        coVerify(exactly = 1) { authRepository.currentUser() }
        coVerify(exactly = 2) { environmentRepository.listAll(active = true) }
    }

    @Test
    fun `falha na primeira carga mostra o erro`() {
        coEvery { environmentRepository.listAll(active = true) } returns ApiResult.Failure(ApiError.Network)

        viewModel.load()

        assertEquals(EnvironmentListState.Failed(UiText.Resource(R.string.error_network)), viewModel.uiState.value.list)
    }

    @Test
    fun `falha numa recarga mantem a lista que ja estava na tela`() {
        viewModel.load()
        coEvery { environmentRepository.listAll(active = true) } returns ApiResult.Failure(ApiError.Network)

        viewModel.load()

        assertEquals(EnvironmentListState.Loaded(listOf(sala, varanda)), viewModel.uiState.value.list)
    }

    @Test
    fun `recarga atualiza a lista com o que mudou`() {
        viewModel.load()
        val updated = sala.copy(plantCount = 3, overdueCount = 0)
        coEvery { environmentRepository.listAll(active = true) } returns ApiResult.Success(listOf(updated))

        viewModel.load()

        assertEquals(EnvironmentListState.Loaded(listOf(updated)), viewModel.uiState.value.list)
    }

    @Test
    fun `falha ao buscar o usuario nao impede a lista`() {
        coEvery { authRepository.currentUser() } returns ApiResult.Failure(ApiError.Network)

        viewModel.load()

        val state = viewModel.uiState.value
        assertNull(state.firstName)
        assertEquals(EnvironmentListState.Loaded(listOf(sala, varanda)), state.list)
    }
}
