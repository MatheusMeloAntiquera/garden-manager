package com.matheusantiquera.gardenmanager.feature.maintenance.detail

import androidx.lifecycle.SavedStateHandle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.MIN_PROGRESS_MILLIS
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceResult
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogDetailViewModelTest {

    private val repository = mockk<MaintenanceRepository>()
    private val log = MaintenanceLog(
        "l1", "p1", "Monstrinha", "t1", "Rega", LocalDateTime.of(2026, 10, 7, 8, 40), fromSchedule = true, notes = "Meio litro",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.getLog("l1") } returns ApiResult.Success(log)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LogDetailViewModel(SavedStateHandle(mapOf(LogDetailViewModel.ARG_ID to "l1")), repository)

    @Test
    fun `load traz o registro`() {
        val viewModel = viewModel()

        viewModel.load()

        assertEquals(LogDetailUiState.Loaded(log), viewModel.uiState.value)
    }

    @Test
    fun `falha na primeira carga mostra o erro`() {
        coEvery { repository.getLog("l1") } returns ApiResult.Failure(ApiError.Http(404, "execução de manutenção não encontrada"))
        val viewModel = viewModel()

        viewModel.load()

        assertEquals(LogDetailUiState.Failed(UiText.Dynamic("execução de manutenção não encontrada")), viewModel.uiState.value)
    }

    @Test
    fun `falha numa recarga mantem o registro na tela`() {
        val viewModel = viewModel()
        viewModel.load()
        coEvery { repository.getLog("l1") } returns ApiResult.Failure(ApiError.Network)

        viewModel.load()

        assertEquals(LogDetailUiState.Loaded(log), viewModel.uiState.value)
    }

    @Test
    fun `recarregar depois de editar mostra os dados novos`() {
        val viewModel = viewModel()
        viewModel.load()
        val edited = log.copy(notes = "Outra coisa")
        coEvery { repository.getLog("l1") } returns ApiResult.Success(edited)

        viewModel.load()

        assertEquals(LogDetailUiState.Loaded(edited), viewModel.uiState.value)
    }

    @Test
    fun `excluir pede confirmacao do registro carregado`() {
        val viewModel = viewModel()
        viewModel.load()

        viewModel.onDeleteClick()

        assertEquals(MaintenanceTarget.OfLog(log), viewModel.actions.state.value.confirmDelete)
    }

    @Test
    fun `excluir antes de carregar nao faz nada`() {
        coEvery { repository.getLog("l1") } returns ApiResult.Failure(ApiError.Network)
        val viewModel = viewModel()
        viewModel.load()

        viewModel.onDeleteClick()

        assertNull(viewModel.actions.state.value.confirmDelete)
    }

    @Test
    fun `confirmar a exclusao apaga o registro e avisa a tela`() = runTest {
        coEvery { repository.deleteLog("l1") } returns ApiResult.Success(Unit)
        val viewModel = viewModel()
        viewModel.load()
        viewModel.onDeleteClick()

        viewModel.actions.confirmDelete()
        advanceTimeBy(MIN_PROGRESS_MILLIS + 1)

        assertEquals(MaintenanceResult.LogDeleted, viewModel.actions.state.value.deleted)
        coVerify { repository.deleteLog("l1") }
    }

    @Test
    fun `erro de rede ao excluir guarda a mensagem`() = runTest {
        coEvery { repository.deleteLog("l1") } returns ApiResult.Failure(ApiError.Network)
        val viewModel = viewModel()
        viewModel.load()
        viewModel.onDeleteClick()

        viewModel.actions.confirmDelete()
        advanceTimeBy(MIN_PROGRESS_MILLIS + 1)

        assertEquals(UiText.Resource(R.string.error_network), viewModel.actions.state.value.error)
        assertNull(viewModel.actions.state.value.deleted)
    }
}
