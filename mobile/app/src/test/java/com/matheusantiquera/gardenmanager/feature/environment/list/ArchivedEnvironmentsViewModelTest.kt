package com.matheusantiquera.gardenmanager.feature.environment.list

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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedEnvironmentsViewModelTest {

    private val repository = mockk<EnvironmentRepository>()
    private lateinit var viewModel: ArchivedEnvironmentsViewModel

    private val antiga = Environment("1", "Casa antiga", null, false, 0)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ArchivedEnvironmentsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load busca so os ambientes inativos`() {
        coEvery { repository.listAll(active = false) } returns ApiResult.Success(listOf(antiga))

        viewModel.load()

        assertEquals(EnvironmentListState.Loaded(listOf(antiga)), viewModel.uiState.value)
        coVerify { repository.listAll(active = false) }
    }

    @Test
    fun `reativado sai da lista na recarga`() {
        coEvery { repository.listAll(active = false) } returns ApiResult.Success(listOf(antiga))
        viewModel.load()
        coEvery { repository.listAll(active = false) } returns ApiResult.Success(emptyList())

        viewModel.load()

        assertEquals(EnvironmentListState.Loaded(emptyList()), viewModel.uiState.value)
    }

    @Test
    fun `falha na primeira carga mostra o erro e numa recarga mantem a lista`() {
        coEvery { repository.listAll(active = false) } returns ApiResult.Failure(ApiError.Network)
        viewModel.load()
        assertEquals(EnvironmentListState.Failed(UiText.Resource(R.string.error_network)), viewModel.uiState.value)

        coEvery { repository.listAll(active = false) } returns ApiResult.Success(listOf(antiga))
        viewModel.load()
        coEvery { repository.listAll(active = false) } returns ApiResult.Failure(ApiError.Network)
        viewModel.load()

        assertEquals(EnvironmentListState.Loaded(listOf(antiga)), viewModel.uiState.value)
    }
}
