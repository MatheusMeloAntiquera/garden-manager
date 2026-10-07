package com.matheusantiquera.gardenmanager.feature.plant.detail

import androidx.lifecycle.SavedStateHandle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.PlantMaintenance
import com.matheusantiquera.gardenmanager.data.plant.Plant
import com.matheusantiquera.gardenmanager.data.plant.PlantRepository
import com.matheusantiquera.gardenmanager.data.plant.PlantSpecies
import com.matheusantiquera.gardenmanager.data.species.Species
import com.matheusantiquera.gardenmanager.data.species.SpeciesCategory
import com.matheusantiquera.gardenmanager.data.species.SpeciesRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlantDetailViewModelTest {

    private val plantRepository = mockk<PlantRepository>()
    private val speciesRepository = mockk<SpeciesRepository>()
    private val maintenanceRepository = mockk<MaintenanceRepository>()
    private lateinit var viewModel: PlantDetailViewModel

    private val ref = PlantSpecies("s1", "Monstera deliciosa", "Costela-de-adão")
    private val plant = Plant("p1", "Monstrinha", "Monstrinha", null, true, ref, null)
    private val species = Species("s1", "Monstera deliciosa", "Araceae", SpeciesCategory.Foliage, "Costela-de-adão")
    private val maintenance = PlantMaintenance(emptyList(), 0, emptyList(), 0)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { plantRepository.get("p1") } returns ApiResult.Success(plant)
        coEvery { speciesRepository.get("s1") } returns ApiResult.Success(species)
        coEvery { maintenanceRepository.forPlant("p1") } returns ApiResult.Success(maintenance)
        viewModel = PlantDetailViewModel(SavedStateHandle(mapOf(PlantDetailViewModel.ARG_PLANT_ID to "p1")), plantRepository, speciesRepository, maintenanceRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load traz a planta, a especie com familia e categoria e as manutencoes`() {
        viewModel.load()

        assertEquals(PlantDetailUiState.Loaded(plant, species, maintenance), viewModel.uiState.value)
    }

    @Test
    fun `planta sem especie nao busca o catalogo`() {
        coEvery { plantRepository.get("p1") } returns ApiResult.Success(plant.copy(species = null))

        viewModel.load()

        assertEquals(PlantDetailUiState.Loaded(plant.copy(species = null), null, maintenance), viewModel.uiState.value)
        coVerify(exactly = 0) { speciesRepository.get(any()) }
    }

    @Test
    fun `falha so na especie ainda mostra o detalhe, sem familia e categoria`() {
        coEvery { speciesRepository.get("s1") } returns ApiResult.Failure(ApiError.Network)

        viewModel.load()

        assertEquals(PlantDetailUiState.Loaded(plant, null, maintenance), viewModel.uiState.value)
    }

    @Test
    fun `falha na planta ou nas manutencoes mostra o erro`() {
        coEvery { plantRepository.get("p1") } returns ApiResult.Failure(ApiError.Http(404, "planta não encontrada"))
        viewModel.load()
        assertEquals(PlantDetailUiState.Failed(UiText.Dynamic("planta não encontrada")), viewModel.uiState.value)

        coEvery { plantRepository.get("p1") } returns ApiResult.Success(plant)
        coEvery { maintenanceRepository.forPlant("p1") } returns ApiResult.Failure(ApiError.Network)
        viewModel.load()
        assertEquals(PlantDetailUiState.Failed(UiText.Resource(R.string.error_network)), viewModel.uiState.value)
    }

    @Test
    fun `falha numa recarga mantem o detalhe na tela`() {
        viewModel.load()
        coEvery { plantRepository.get("p1") } returns ApiResult.Failure(ApiError.Network)

        viewModel.load()

        assertEquals(PlantDetailUiState.Loaded(plant, species, maintenance), viewModel.uiState.value)
    }
}
