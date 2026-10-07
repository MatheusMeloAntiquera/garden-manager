package com.matheusantiquera.gardenmanager.feature.plant.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.MIN_PROGRESS_MILLIS
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.environment.Environment
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.PlantMaintenance
import com.matheusantiquera.gardenmanager.data.plant.Plant
import com.matheusantiquera.gardenmanager.data.plant.PlantEnvironment
import com.matheusantiquera.gardenmanager.data.plant.PlantInput
import com.matheusantiquera.gardenmanager.data.plant.PlantRepository
import com.matheusantiquera.gardenmanager.data.plant.PlantSpecies
import com.matheusantiquera.gardenmanager.feature.plant.PickedSpecies
import com.matheusantiquera.gardenmanager.feature.plant.PlantFormResult
import com.matheusantiquera.gardenmanager.feature.plant.SpeciesPick
import com.matheusantiquera.gardenmanager.feature.plant.decodeSpeciesPick
import com.matheusantiquera.gardenmanager.feature.plant.encode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PlantFormViewModelTest {

    private val plantRepository = mockk<PlantRepository>()
    private val environmentRepository = mockk<EnvironmentRepository>()
    private val maintenanceRepository = mockk<MaintenanceRepository>()

    private val sala = Environment("e1", "Sala", null, true, plantCount = 1)
    private val monstera = PlantSpecies("s1", "Monstera deliciosa", "Costela-de-adão")
    private val plant = Plant("p1", "Monstrinha", "Monstrinha", "Luz indireta", true, monstera, PlantEnvironment("e1", "Sala"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { environmentRepository.listAll(active = true) } returns ApiResult.Success(listOf(sala))
        coEvery { plantRepository.get("p1") } returns ApiResult.Success(plant)
        coEvery { maintenanceRepository.forPlant("p1") } returns ApiResult.Success(PlantMaintenance(emptyList(), 2, emptyList(), 3))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(vararg args: Pair<String, Any?>, handle: SavedStateHandle = SavedStateHandle(mapOf(*args))) =
        PlantFormViewModel(handle, plantRepository, environmentRepository, maintenanceRepository)

    @Test
    fun `criacao vem com o ambiente do filtro da lista`() {
        val state = viewModel(PlantFormViewModel.ARG_ENVIRONMENT_ID to "e1").uiState.value

        assertFalse(state.isEditing)
        assertFalse(state.isLoading)
        assertEquals("e1", state.environmentId)
        assertEquals(listOf(EnvironmentOption("e1", "Sala")), state.environments)
    }

    @Test
    fun `ambiente do filtro que nao e mais opcao fica vazio`() {
        assertNull(viewModel(PlantFormViewModel.ARG_ENVIRONMENT_ID to "sumiu").uiState.value.environmentId)
    }

    @Test
    fun `edicao carrega a planta e os totais de manutencao`() {
        val state = viewModel(PlantFormViewModel.ARG_PLANT_ID to "p1").uiState.value

        assertTrue(state.isEditing)
        assertEquals("Monstrinha", state.nickname)
        assertEquals(monstera, state.species)
        assertEquals("e1", state.environmentId)
        assertEquals("Luz indireta", state.notes)
        assertEquals(2, state.scheduleTotal)
        assertEquals(3, state.logTotal)
    }

    @Test
    fun `planta em ambiente arquivado mantem o ambiente como opcao`() {
        coEvery { plantRepository.get("p1") } returns ApiResult.Success(plant.copy(environment = PlantEnvironment("e9", "Casa antiga")))

        val state = viewModel(PlantFormViewModel.ARG_PLANT_ID to "p1").uiState.value

        assertEquals(EnvironmentOption("e9", "Casa antiga", archived = true), state.selectedEnvironment)
    }

    @Test
    fun `sem apelido e sem especie mostra o erro e nao chama a API`() {
        val viewModel = viewModel()

        viewModel.onSave()

        assertTrue(viewModel.uiState.value.missingNicknameAndSpecies)
        coVerify(exactly = 0) { plantRepository.create(any()) }
    }

    @Test
    fun `apelido longo demais mostra erro`() {
        val viewModel = viewModel()
        viewModel.onNicknameChange("a".repeat(101))

        viewModel.onSave()

        assertEquals(UiText.Resource(R.string.validation_plant_nickname_too_long), viewModel.uiState.value.nicknameError)
    }

    @Test
    fun `especie escolhida no seletor e aplicada, e Sem especie limpa`() {
        val viewModel = viewModel()

        viewModel.onSpeciesPicked(SpeciesPick(PickedSpecies("s1", "Monstera deliciosa", "Costela-de-adão")))
        assertEquals(monstera, viewModel.uiState.value.species)

        viewModel.onSpeciesPicked(SpeciesPick(species = null))
        assertNull(viewModel.uiState.value.species)
    }

    @Test
    fun `escolha do seletor sobrevive a ida e volta em texto`() {
        val pick = SpeciesPick(PickedSpecies("s1", "Monstera deliciosa", null))

        assertEquals(pick, decodeSpeciesPick(pick.encode()))
        assertNull(decodeSpeciesPick("não é json"))
    }

    @Test
    fun `criar so com especie envia apelido nulo e avisa que criou`() = runTest {
        val input = PlantInput(nickname = null, speciesId = "s1", environmentId = "e1", notes = null)
        coEvery { plantRepository.create(input) } returns ApiResult.Success(plant)
        val viewModel = viewModel(PlantFormViewModel.ARG_ENVIRONMENT_ID to "e1")
        viewModel.onSpeciesChange(monstera)
        viewModel.onNicknameChange("  ")

        viewModel.events.test {
            viewModel.onSave()
            assertEquals(PlantFormEvent.Done(PlantFormResult.Created), awaitItem())
        }
    }

    @Test
    fun `salvando fica no minimo 2 segundos`() = runTest {
        coEvery { plantRepository.update("p1", any(), any()) } returns ApiResult.Success(plant)
        val viewModel = viewModel(PlantFormViewModel.ARG_PLANT_ID to "p1")

        viewModel.onSave()
        advanceTimeBy(MIN_PROGRESS_MILLIS - 1)
        assertTrue(viewModel.uiState.value.isSaving)

        advanceTimeBy(2)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `editar envia o status e sem ambiente limpa o ambiente`() = runTest {
        coEvery { plantRepository.update(any(), any(), any()) } returns ApiResult.Success(plant)
        val viewModel = viewModel(PlantFormViewModel.ARG_PLANT_ID to "p1")
        viewModel.onEnvironmentChange(null)
        viewModel.onActiveChange(false)

        viewModel.events.test {
            viewModel.onSave()
            assertEquals(PlantFormEvent.Done(PlantFormResult.Updated), awaitItem())
        }
        coVerify {
            plantRepository.update("p1", PlantInput("Monstrinha", "s1", environmentId = null, notes = "Luz indireta"), active = false)
        }
    }

    @Test
    fun `erro da API ao salvar aparece no formulario`() = runTest {
        coEvery { plantRepository.update(any(), any(), any()) } returns ApiResult.Failure(ApiError.Http(422, "ambiente não encontrado"))
        val viewModel = viewModel(PlantFormViewModel.ARG_PLANT_ID to "p1")

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(UiText.Dynamic("ambiente não encontrado"), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `excluir pede confirmacao, fica no minimo 2 segundos e avisa que excluiu`() = runTest {
        coEvery { plantRepository.delete("p1") } returns ApiResult.Success(Unit)
        val viewModel = viewModel(PlantFormViewModel.ARG_PLANT_ID to "p1")
        viewModel.onDeleteClick()
        assertTrue(viewModel.uiState.value.showDeleteConfirmation)

        viewModel.events.test {
            viewModel.onDeleteConfirm()
            advanceTimeBy(MIN_PROGRESS_MILLIS - 1)
            assertTrue(viewModel.uiState.value.isDeleting)
            assertEquals(PlantFormEvent.Done(PlantFormResult.Deleted), awaitItem())
        }
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun `criacao nao abre o dialogo de exclusao`() {
        val viewModel = viewModel()

        viewModel.onDeleteClick()

        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `falha ao carregar mostra o erro`() {
        coEvery { environmentRepository.listAll(active = true) } returns ApiResult.Failure(ApiError.Network)

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(UiText.Resource(R.string.error_network), state.loadError)
    }
}
