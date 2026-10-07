package com.matheusantiquera.gardenmanager.feature.plant.list

import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.environment.Environment
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentRepository
import com.matheusantiquera.gardenmanager.data.plant.Plant
import com.matheusantiquera.gardenmanager.data.plant.PlantPage
import com.matheusantiquera.gardenmanager.data.plant.PlantRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
class PlantsViewModelTest {

    private val plantRepository = mockk<PlantRepository>()
    private val environmentRepository = mockk<EnvironmentRepository>()
    private lateinit var viewModel: PlantsViewModel

    private val sala = Environment("e1", "Sala", null, true, plantCount = 1)
    private val monstrinha = Plant("p1", "Monstrinha", "Monstrinha", null, true, null, null)
    private val jiboia = Plant("p2", "Jiboia", "Jiboia", null, true, null, null)

    private fun page(vararg plants: Plant, page: Int = 1, total: Int = plants.size) = ApiResult.Success(PlantPage(plants.toList(), page, total))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { environmentRepository.listAll(active = true) } returns ApiResult.Success(listOf(sala))
        coEvery { plantRepository.list(any(), any(), any(), any()) } returns page(monstrinha, jiboia)
        viewModel = PlantsViewModel(plantRepository, environmentRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load traz os ambientes para os chips e as plantas ativas`() {
        viewModel.load()

        val state = viewModel.uiState.value
        assertEquals(listOf(sala), state.environments)
        assertEquals(PlantListState.Loaded(listOf(monstrinha, jiboia), total = 2, page = 1, hasMore = false), state.list)
        coVerify { plantRepository.list(query = "", environmentId = null, active = true, page = 1) }
    }

    @Test
    fun `filtrar por ambiente envia o id e mostra o ambiente na legenda`() {
        viewModel.load()

        viewModel.onFilterSelected(PlantsFilter.ByEnvironment(sala.id))

        coVerify { plantRepository.list(query = "", environmentId = sala.id, active = true, page = 1) }
        assertEquals(sala, viewModel.uiState.value.filteredEnvironment)
    }

    @Test
    fun `arquivadas busca as inativas`() {
        viewModel.load()

        viewModel.onFilterSelected(PlantsFilter.Archived)

        coVerify { plantRepository.list(query = "", environmentId = null, active = false, page = 1) }
    }

    @Test
    fun `a busca espera uma pausa na digitacao`() = runTest {
        viewModel.load()

        viewModel.onQueryChange("mon")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 1)
        coVerify(exactly = 0) { plantRepository.list(query = "mon", any(), any(), any()) }

        advanceTimeBy(2)
        coVerify(exactly = 1) { plantRepository.list(query = "mon", environmentId = null, active = true, page = 1) }
    }

    @Test
    fun `filtro pedido de fora troca o filtro e recarrega, mesmo se ja for o atual`() {
        viewModel.load()

        viewModel.applyEnvironmentFilter(sala.id)
        viewModel.applyEnvironmentFilter(sala.id)

        assertEquals(PlantsFilter.ByEnvironment(sala.id), viewModel.uiState.value.filter)
        coVerify(exactly = 2) { plantRepository.list(query = "", environmentId = sala.id, active = true, page = 1) }
    }

    @Test
    fun `ambiente filtrado que sumiu volta o filtro para Todas`() {
        viewModel.load()
        viewModel.onFilterSelected(PlantsFilter.ByEnvironment(sala.id))
        coEvery { environmentRepository.listAll(active = true) } returns ApiResult.Success(emptyList())

        viewModel.load()

        assertEquals(PlantsFilter.All, viewModel.uiState.value.filter)
        coVerify { plantRepository.list(query = "", environmentId = null, active = true, page = 1) }
    }

    @Test
    fun `carregar mais junta a proxima pagina`() {
        coEvery { plantRepository.list(any(), any(), any(), page = 1) } returns page(monstrinha, total = 25)
        coEvery { plantRepository.list(any(), any(), any(), page = 2) } returns page(jiboia, page = 2, total = 25)
        viewModel.load()

        viewModel.loadMore()

        val list = viewModel.uiState.value.list as PlantListState.Loaded
        assertEquals(listOf(monstrinha, jiboia), list.plants)
        assertEquals(2, list.page)
    }

    @Test
    fun `falha na primeira carga mostra o erro e numa recarga mantem a lista`() {
        coEvery { plantRepository.list(any(), any(), any(), any()) } returns ApiResult.Failure(ApiError.Network)
        viewModel.load()
        assertEquals(PlantListState.Failed(UiText.Resource(R.string.error_network)), viewModel.uiState.value.list)

        coEvery { plantRepository.list(any(), any(), any(), any()) } returns page(monstrinha)
        viewModel.load()
        coEvery { plantRepository.list(any(), any(), any(), any()) } returns ApiResult.Failure(ApiError.Network)
        viewModel.load()

        assertEquals(PlantListState.Loaded(listOf(monstrinha), total = 1, page = 1, hasMore = false), viewModel.uiState.value.list)
    }
}
