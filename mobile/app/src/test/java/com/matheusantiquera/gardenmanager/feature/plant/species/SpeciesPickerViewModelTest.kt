package com.matheusantiquera.gardenmanager.feature.plant.species

import androidx.lifecycle.SavedStateHandle
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.data.species.Species
import com.matheusantiquera.gardenmanager.data.species.SpeciesPage
import com.matheusantiquera.gardenmanager.data.species.SpeciesRepository
import com.matheusantiquera.gardenmanager.feature.plant.list.SEARCH_DEBOUNCE_MILLIS
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeciesPickerViewModelTest {

    private val repository = mockk<SpeciesRepository>()

    private val monstera = Species("s1", "Monstera deliciosa", "Araceae", null, "Costela-de-adão")
    private val jiboia = Species("s2", "Epipremnum aureum", "Araceae", null, "Jiboia")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.search(any(), any()) } returns ApiResult.Success(SpeciesPage(listOf(monstera), page = 1, total = 1))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `abre listando o catalogo com a especie atual marcada`() {
        val viewModel = SpeciesPickerViewModel(SavedStateHandle(mapOf(SpeciesPickerViewModel.ARG_SELECTED_ID to "s1")), repository)

        val state = viewModel.uiState.value
        assertEquals("s1", state.selectedId)
        assertEquals(SpeciesResultsState.Loaded(listOf(monstera), page = 1, hasMore = false), state.results)
        coVerify { repository.search("", page = 1) }
    }

    @Test
    fun `a busca espera uma pausa na digitacao`() = runTest {
        val viewModel = SpeciesPickerViewModel(SavedStateHandle(), repository)

        viewModel.onQueryChange("jib")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 1)
        coVerify(exactly = 0) { repository.search("jib", any()) }

        advanceTimeBy(2)
        coVerify(exactly = 1) { repository.search("jib", page = 1) }
    }

    @Test
    fun `carregar mais junta a proxima pagina`() {
        coEvery { repository.search("", page = 1) } returns ApiResult.Success(SpeciesPage(listOf(monstera), page = 1, total = 40))
        coEvery { repository.search("", page = 2) } returns ApiResult.Success(SpeciesPage(listOf(jiboia), page = 2, total = 40))
        val viewModel = SpeciesPickerViewModel(SavedStateHandle(), repository)

        viewModel.loadMore()

        assertEquals(SpeciesResultsState.Loaded(listOf(monstera, jiboia), page = 2, hasMore = false), viewModel.uiState.value.results)
    }
}
