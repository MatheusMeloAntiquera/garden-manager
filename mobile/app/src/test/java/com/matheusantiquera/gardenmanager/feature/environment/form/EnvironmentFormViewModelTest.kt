package com.matheusantiquera.gardenmanager.feature.environment.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.environment.Environment
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnvironmentFormViewModelTest {

    private val repository = mockk<EnvironmentRepository>()
    private val sala = Environment(id = "env-1", name = "Sala", notes = "Luz da manhã", active = true, plantCount = 5)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMode() = EnvironmentFormViewModel(SavedStateHandle(), repository)

    private fun editMode(id: String = sala.id) =
        EnvironmentFormViewModel(SavedStateHandle(mapOf(EnvironmentFormViewModel.ARG_ENVIRONMENT_ID to id)), repository)

    @Test
    fun `sem id abre em modo criacao sem carregar nada`() {
        val viewModel = createMode()

        val state = viewModel.uiState.value
        assertFalse(state.isEditing)
        assertFalse(state.isLoading)
        coVerify(exactly = 0) { repository.get(any()) }
    }

    @Test
    fun `nome vazio mostra erro e nao chama a API`() {
        val viewModel = createMode()
        viewModel.onNameChange("   ")

        viewModel.onSave()

        assertEquals(UiText.Resource(R.string.validation_environment_name_required), viewModel.uiState.value.nameError)
        coVerify(exactly = 0) { repository.create(any(), any()) }
    }

    @Test
    fun `observacoes longas demais mostram erro`() {
        val viewModel = createMode()
        viewModel.onNameChange("Sala")
        viewModel.onNotesChange("a".repeat(2001))

        viewModel.onSave()

        assertEquals(UiText.Resource(R.string.validation_notes_too_long), viewModel.uiState.value.notesError)
        coVerify(exactly = 0) { repository.create(any(), any()) }
    }

    @Test
    fun `criar envia nome sem espacos e observacao em branco como nula e avisa que criou`() = runTest {
        coEvery { repository.create("Varanda", null) } returns ApiResult.Success(sala.copy(name = "Varanda"))
        val viewModel = createMode()
        viewModel.onNameChange("  Varanda ")
        viewModel.onNotesChange("   ")

        viewModel.events.test {
            viewModel.onSave()
            assertEquals(EnvironmentFormEvent.Done(EnvironmentFormResult.Created), awaitItem())
        }
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `editar carrega o ambiente nos campos`() {
        coEvery { repository.get(sala.id) } returns ApiResult.Success(sala)

        val state = editMode().uiState.value

        assertTrue(state.isEditing)
        assertFalse(state.isLoading)
        assertEquals("Sala", state.name)
        assertEquals("Luz da manhã", state.notes)
        assertTrue(state.active)
        assertEquals(5, state.plantCount)
        assertEquals("Sala", state.savedName)
    }

    @Test
    fun `falha ao carregar mostra o erro e tentar de novo recarrega`() {
        coEvery { repository.get(sala.id) } returns ApiResult.Failure(ApiError.Network)
        val viewModel = editMode()

        assertEquals(UiText.Resource(R.string.error_network), viewModel.uiState.value.loadError)

        coEvery { repository.get(sala.id) } returns ApiResult.Success(sala)
        viewModel.load()

        assertNull(viewModel.uiState.value.loadError)
        assertEquals("Sala", viewModel.uiState.value.name)
    }

    @Test
    fun `salvar edicao envia o status e mantem o nome salvo no dialogo`() = runTest {
        coEvery { repository.get(sala.id) } returns ApiResult.Success(sala)
        coEvery { repository.update(sala.id, "Sala de estar", "Luz da manhã", false) } returns
            ApiResult.Success(sala.copy(name = "Sala de estar", active = false))
        val viewModel = editMode()
        viewModel.onNameChange("Sala de estar")
        viewModel.onActiveChange(false)

        // O nome do diálogo de exclusão é o salvo, não o que está sendo digitado.
        assertEquals("Sala", viewModel.uiState.value.savedName)

        viewModel.events.test {
            viewModel.onSave()
            assertEquals(EnvironmentFormEvent.Done(EnvironmentFormResult.Updated), awaitItem())
        }
    }

    @Test
    fun `salvando fica no minimo 2 segundos mesmo com a API respondendo na hora`() = runTest {
        coEvery { repository.create(any(), any()) } returns ApiResult.Success(sala)
        val viewModel = createMode()
        viewModel.onNameChange("Sala")

        viewModel.onSave()
        advanceTimeBy(EnvironmentFormViewModel.MIN_PROGRESS_MILLIS - 1)
        assertTrue(viewModel.uiState.value.isSaving)

        advanceTimeBy(2)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `API lenta nao soma os 2 segundos ao tempo dela`() = runTest {
        coEvery { repository.create(any(), any()) } coAnswers {
            delay(5_000)
            ApiResult.Success(sala)
        }
        val viewModel = createMode()
        viewModel.onNameChange("Sala")

        viewModel.onSave()
        advanceTimeBy(4_999)
        assertTrue(viewModel.uiState.value.isSaving)

        advanceTimeBy(2)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `erro ao salvar mostra a mensagem da API e libera o botao`() = runTest {
        coEvery { repository.get(sala.id) } returns ApiResult.Success(sala)
        coEvery { repository.update(any(), any(), any(), any()) } returns
            ApiResult.Failure(ApiError.Http(404, "ambiente não encontrado"))
        val viewModel = editMode()

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(UiText.Dynamic("ambiente não encontrado"), state.error)
        assertFalse(state.isSaving)
    }

    @Test
    fun `excluir pede confirmacao e cancelar nao chama a API`() {
        coEvery { repository.get(sala.id) } returns ApiResult.Success(sala)
        val viewModel = editMode()

        viewModel.onDeleteClick()
        assertTrue(viewModel.uiState.value.showDeleteConfirmation)

        viewModel.onDeleteDismiss()
        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `confirmar exclusao apaga e avisa que excluiu`() = runTest {
        coEvery { repository.get(sala.id) } returns ApiResult.Success(sala)
        coEvery { repository.delete(sala.id) } returns ApiResult.Success(Unit)
        val viewModel = editMode()
        viewModel.onDeleteClick()

        viewModel.events.test {
            viewModel.onDeleteConfirm()
            assertEquals(EnvironmentFormEvent.Done(EnvironmentFormResult.Deleted), awaitItem())
        }
        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `excluindo tambem fica no minimo 2 segundos`() = runTest {
        coEvery { repository.get(sala.id) } returns ApiResult.Success(sala)
        coEvery { repository.delete(sala.id) } returns ApiResult.Success(Unit)
        val viewModel = editMode()
        viewModel.onDeleteClick()

        viewModel.onDeleteConfirm()
        advanceTimeBy(EnvironmentFormViewModel.MIN_PROGRESS_MILLIS - 1)
        assertTrue(viewModel.uiState.value.isDeleting)

        advanceTimeBy(2)
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun `falha ao excluir fecha o dialogo e mostra o erro`() = runTest {
        coEvery { repository.get(sala.id) } returns ApiResult.Success(sala)
        coEvery { repository.delete(sala.id) } returns ApiResult.Failure(ApiError.Network)
        val viewModel = editMode()
        viewModel.onDeleteClick()

        viewModel.onDeleteConfirm()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showDeleteConfirmation)
        assertFalse(state.isDeleting)
        assertEquals(UiText.Resource(R.string.error_network), state.error)
    }

    @Test
    fun `modo criacao nao abre o dialogo de exclusao`() {
        val viewModel = createMode()

        viewModel.onDeleteClick()

        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
    }
}
