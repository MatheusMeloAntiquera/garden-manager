package com.matheusantiquera.gardenmanager.feature.maintenance

import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.MIN_PROGRESS_MILLIS
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceActionsTest {

    private val repository = mockk<MaintenanceRepository>()
    private val schedule = MaintenanceSchedule("m1", "p1", "Monstrinha", "t1", "Rega", LocalDateTime.of(2026, 10, 8, 8, 0), overdue = false)
    private val log = MaintenanceLog("l1", "p1", "Monstrinha", "t1", "Rega", LocalDateTime.of(2026, 10, 7, 8, 0))

    private var deletedCalls = 0

    private fun kotlinx.coroutines.test.TestScope.actions() = MaintenanceActions(repository, this, onDeleted = { deletedCalls++ })

    @Test
    fun `abrir a sheet guarda o item e fechar limpa`() = runTest {
        val actions = actions()

        actions.openSheet(schedule)
        assertEquals(schedule, actions.state.value.sheet)

        actions.dismissSheet()
        assertNull(actions.state.value.sheet)
    }

    @Test
    fun `excluir na sheet fecha a sheet e pede a confirmacao do mesmo agendamento`() = runTest {
        val actions = actions()
        actions.openSheet(schedule)

        actions.askDelete()

        assertNull(actions.state.value.sheet)
        assertEquals(MaintenanceTarget.OfSchedule(schedule), actions.state.value.confirmDelete)
    }

    @Test
    fun `excluir um registro pede a confirmacao direto, sem sheet`() = runTest {
        val actions = actions()

        actions.askDelete(MaintenanceTarget.OfLog(log))

        assertNull(actions.state.value.sheet)
        assertEquals(MaintenanceTarget.OfLog(log), actions.state.value.confirmDelete)
    }

    @Test
    fun `cancelar a confirmacao nao exclui`() = runTest {
        val actions = actions()
        actions.openSheet(schedule)
        actions.askDelete()

        actions.dismissDelete()

        assertNull(actions.state.value.confirmDelete)
        coVerify(exactly = 0) { repository.deleteSchedule(any()) }
    }

    @Test
    fun `confirmar exclui o agendamento, fica 2 segundos, avisa e recarrega a lista`() = runTest {
        coEvery { repository.deleteSchedule("m1") } returns ApiResult.Success(Unit)
        val actions = actions()
        actions.openSheet(schedule)
        actions.askDelete()

        actions.confirmDelete()
        advanceTimeBy(MIN_PROGRESS_MILLIS - 1)
        assertTrue(actions.state.value.isDeleting)
        assertEquals(0, deletedCalls)

        advanceTimeBy(2)
        val state = actions.state.value
        assertFalse(state.isDeleting)
        assertNull(state.confirmDelete)
        assertEquals(MaintenanceResult.ScheduleDeleted, state.deleted)
        assertEquals(1, deletedCalls)
        coVerify { repository.deleteSchedule("m1") }
    }

    @Test
    fun `confirmar exclui a execucao pelo endpoint de registros`() = runTest {
        coEvery { repository.deleteLog("l1") } returns ApiResult.Success(Unit)
        val actions = actions()
        actions.askDelete(MaintenanceTarget.OfLog(log))

        actions.confirmDelete()
        advanceTimeBy(MIN_PROGRESS_MILLIS + 1)

        assertEquals(MaintenanceResult.LogDeleted, actions.state.value.deleted)
        coVerify { repository.deleteLog("l1") }
        coVerify(exactly = 0) { repository.deleteSchedule(any()) }
    }

    @Test
    fun `falha ao excluir fecha a confirmacao, guarda o erro e nao recarrega`() = runTest {
        coEvery { repository.deleteSchedule("m1") } returns ApiResult.Failure(ApiError.Network)
        val actions = actions()
        actions.openSheet(schedule)
        actions.askDelete()

        actions.confirmDelete()
        advanceTimeBy(MIN_PROGRESS_MILLIS + 1)

        val state = actions.state.value
        assertFalse(state.isDeleting)
        assertNull(state.confirmDelete)
        assertNull(state.deleted)
        assertEquals(UiText.Resource(R.string.error_network), state.error)
        assertEquals(0, deletedCalls)

        actions.clearError()
        assertNull(actions.state.value.error)
    }

    @Test
    fun `confirmar duas vezes seguidas exclui uma vez so`() = runTest {
        coEvery { repository.deleteSchedule("m1") } returns ApiResult.Success(Unit)
        val actions = actions()
        actions.openSheet(schedule)
        actions.askDelete()

        actions.confirmDelete()
        actions.confirmDelete()
        advanceTimeBy(MIN_PROGRESS_MILLIS + 1)

        coVerify(exactly = 1) { repository.deleteSchedule("m1") }
    }
}
