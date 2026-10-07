package com.matheusantiquera.gardenmanager.feature.schedule

import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.MIN_PROGRESS_MILLIS
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenancePage
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private val repository = mockk<MaintenanceRepository>()

    private fun schedule(id: String) =
        MaintenanceSchedule(id, "p1", "Monstrinha", "t1", "Rega", LocalDateTime.of(2026, 10, 8, 8, 0), overdue = false)

    private fun log(id: String) = MaintenanceLog(id, "p1", "Monstrinha", "t1", "Rega", LocalDateTime.of(2026, 10, 7, 8, 0))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.schedules(1) } returns ApiResult.Success(MaintenancePage(listOf(schedule("m1"), schedule("m2")), page = 1, total = 2))
        coEvery { repository.overdueCount() } returns ApiResult.Success(5)
        coEvery { repository.logs(1) } returns ApiResult.Success(MaintenancePage(listOf(log("l1")), page = 1, total = 1))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load traz os pendentes e o total de atrasados`() {
        val viewModel = ScheduleViewModel(repository)

        viewModel.load()

        val pending = viewModel.pending.state.value as PagedState.Loaded
        assertEquals(listOf("m1", "m2"), pending.items.map { it.id })
        assertEquals(5, viewModel.overdueTotal.value)
    }

    @Test
    fun `abre na aba Pendentes e o historico so carrega ao trocar de aba`() {
        val viewModel = ScheduleViewModel(repository)
        viewModel.load()
        assertEquals(AgendaTab.Pending, viewModel.tab.value)
        assertEquals(PagedState.Loading, viewModel.history.state.value)
        coVerify(exactly = 0) { repository.logs(any()) }

        viewModel.onTabSelected(AgendaTab.History)

        assertEquals(AgendaTab.History, viewModel.tab.value)
        assertEquals(listOf("l1"), (viewModel.history.state.value as PagedState.Loaded).items.map { it.id })
    }

    @Test
    fun `falha na primeira carga mostra o erro`() {
        coEvery { repository.schedules(1) } returns ApiResult.Failure(ApiError.Network)
        val viewModel = ScheduleViewModel(repository)

        viewModel.load()

        assertEquals(PagedState.Failed(UiText.Resource(R.string.error_network)), viewModel.pending.state.value)
    }

    @Test
    fun `falha numa recarga mantem a lista na tela`() {
        val viewModel = ScheduleViewModel(repository)
        viewModel.load()
        coEvery { repository.schedules(1) } returns ApiResult.Failure(ApiError.Network)

        viewModel.load()

        assertEquals(listOf("m1", "m2"), (viewModel.pending.state.value as PagedState.Loaded).items.map { it.id })
    }

    @Test
    fun `loadMore acrescenta a proxima pagina e para quando acabam`() {
        coEvery { repository.schedules(1) } returns ApiResult.Success(MaintenancePage(listOf(schedule("m1")), page = 1, total = 21))
        coEvery { repository.schedules(2) } returns ApiResult.Success(MaintenancePage(listOf(schedule("m2")), page = 2, total = 21))
        val viewModel = ScheduleViewModel(repository)
        viewModel.load()
        assertTrue((viewModel.pending.state.value as PagedState.Loaded).hasMore)

        viewModel.loadMore()

        val loaded = viewModel.pending.state.value as PagedState.Loaded
        assertEquals(listOf("m1", "m2"), loaded.items.map { it.id })
        assertEquals(2, loaded.page)
        assertTrue(!loaded.hasMore)

        viewModel.loadMore()
        coVerify(exactly = 0) { repository.schedules(3) }
    }

    @Test
    fun `falha ao carregar mais mantem a lista e deixa tentar de novo`() {
        coEvery { repository.schedules(1) } returns ApiResult.Success(MaintenancePage(listOf(schedule("m1")), page = 1, total = 21))
        coEvery { repository.schedules(2) } returns ApiResult.Failure(ApiError.Network)
        val viewModel = ScheduleViewModel(repository)
        viewModel.load()

        viewModel.loadMore()

        val loaded = viewModel.pending.state.value as PagedState.Loaded
        assertEquals(listOf("m1"), loaded.items.map { it.id })
        assertTrue(!loaded.isLoadingMore)
        assertTrue(loaded.hasMore)
    }

    @Test
    fun `tocar num agendamento abre a sheet dele`() {
        val viewModel = ScheduleViewModel(repository)

        viewModel.onScheduleClick(schedule("m1"))

        assertEquals(schedule("m1"), viewModel.actions.state.value.sheet)
    }

    @Test
    fun `excluir recarrega a aba atual`() = runTest {
        coEvery { repository.deleteSchedule("m1") } returns ApiResult.Success(Unit)
        val viewModel = ScheduleViewModel(repository)
        viewModel.load()
        coEvery { repository.schedules(1) } returns ApiResult.Success(MaintenancePage(listOf(schedule("m2")), page = 1, total = 1))

        viewModel.onScheduleClick(schedule("m1"))
        viewModel.actions.askDelete()
        viewModel.actions.confirmDelete()
        advanceTimeBy(MIN_PROGRESS_MILLIS + 1)

        assertEquals(listOf("m2"), (viewModel.pending.state.value as PagedState.Loaded).items.map { it.id })
    }
}
