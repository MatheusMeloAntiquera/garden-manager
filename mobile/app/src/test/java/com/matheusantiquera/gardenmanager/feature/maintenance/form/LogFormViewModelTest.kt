package com.matheusantiquera.gardenmanager.feature.maintenance.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.maintenance.LogInput
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceLog
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceType
import com.matheusantiquera.gardenmanager.data.plant.Plant
import com.matheusantiquera.gardenmanager.data.plant.PlantRepository
import com.matheusantiquera.gardenmanager.feature.maintenance.MaintenanceResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class LogFormViewModelTest {

    private val plantRepository = mockk<PlantRepository>()
    private val maintenanceRepository = mockk<MaintenanceRepository>()

    // 13:20:45 UTC: o padrão "agora" tem que vir sem os segundos.
    private val clock = Clock.fixed(Instant.parse("2026-10-07T13:20:45Z"), ZoneOffset.UTC)

    private val plant = Plant("p1", "Monstrinha", "Monstrinha", null, true, null, null)
    private val rega = MaintenanceType("t1", "Rega")
    private val poda = MaintenanceType("t2", "Poda")
    private val schedule = MaintenanceSchedule(
        "m1", "p1", "Monstrinha", "t1", "Rega", LocalDateTime.of(2026, 10, 5, 8, 0), overdue = true, notes = "Meio litro",
    )
    private val log = MaintenanceLog(
        "l1", "p1", "Monstrinha", "t2", "Poda", LocalDateTime.of(2026, 10, 1, 17, 15), fromSchedule = true, notes = "Podei",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { plantRepository.get("p1") } returns ApiResult.Success(plant)
        coEvery { maintenanceRepository.types() } returns ApiResult.Success(listOf(rega, poda))
        coEvery { maintenanceRepository.getSchedule("m1") } returns ApiResult.Success(schedule)
        coEvery { maintenanceRepository.getLog("l1") } returns ApiResult.Success(log)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(vararg args: Pair<String, Any?>) =
        LogFormViewModel(SavedStateHandle(mapOf(LogFormViewModel.ARG_PLANT_ID to "p1", *args)), plantRepository, maintenanceRepository, clock)

    @Test
    fun `registro avulso comeca agora, sem os segundos e sem tipo`() {
        val state = viewModel().uiState.value

        assertFalse(state.isEditing)
        assertFalse(state.isFromSchedule)
        assertEquals("Monstrinha", state.plantName)
        assertNull(state.typeId)
        assertEquals(LocalDate.of(2026, 10, 7), state.date)
        assertEquals(LocalTime.of(13, 20), state.time)
    }

    @Test
    fun `concluir herda tipo e observacoes do agendamento e usa a data de agora`() {
        val state = viewModel(LogFormViewModel.ARG_SCHEDULE_ID to "m1").uiState.value

        assertTrue(state.isFromSchedule)
        assertEquals("t1", state.typeId)
        assertEquals("Meio litro", state.notes)
        assertEquals(LocalTime.of(13, 20), state.time)
    }

    @Test
    fun `editar carrega a execucao e se ela nasceu de agendamento`() {
        val state = viewModel(LogFormViewModel.ARG_ID to "l1").uiState.value

        assertTrue(state.isEditing)
        assertTrue(state.createdFromSchedule)
        assertEquals("t2", state.typeId)
        assertEquals(LocalDate.of(2026, 10, 1), state.date)
        assertEquals(LocalTime.of(17, 15), state.time)
        assertEquals("Podei", state.notes)
    }

    @Test
    fun `falha ao carregar o agendamento mostra o erro`() {
        coEvery { maintenanceRepository.getSchedule("m1") } returns ApiResult.Failure(ApiError.Http(404, "agendamento de manutenção não encontrado"))

        val state = viewModel(LogFormViewModel.ARG_SCHEDULE_ID to "m1").uiState.value

        assertEquals(UiText.Dynamic("agendamento de manutenção não encontrado"), state.loadError)
    }

    @Test
    fun `salvar sem tipo mostra erro`() {
        val viewModel = viewModel()

        viewModel.onSave()

        assertEquals(UiText.Resource(R.string.validation_maintenance_type_required), viewModel.uiState.value.typeError)
        coVerify(exactly = 0) { maintenanceRepository.createLog(any()) }
    }

    @Test
    fun `data no futuro mostra erro sem chamar a API`() {
        val viewModel = viewModel()
        viewModel.onTypeChange("t1")
        viewModel.onTimeChange(LocalTime.of(13, 21))

        viewModel.onSave()

        assertEquals(UiText.Resource(R.string.validation_maintenance_performed_in_future), viewModel.uiState.value.dateTimeError)
        coVerify(exactly = 0) { maintenanceRepository.createLog(any()) }
    }

    @Test
    fun `mudar a data limpa o erro de futuro`() {
        val viewModel = viewModel()
        viewModel.onTypeChange("t1")
        viewModel.onDateChange(LocalDate.of(2026, 10, 8))
        viewModel.onSave()

        viewModel.onDateChange(LocalDate.of(2026, 10, 6))

        assertNull(viewModel.uiState.value.dateTimeError)
    }

    @Test
    fun `agora exato passa na validacao`() = runTest {
        coEvery { maintenanceRepository.createLog(any()) } returns ApiResult.Success(log)
        val viewModel = viewModel()
        viewModel.onTypeChange("t1")

        viewModel.events.test {
            viewModel.onSave()
            assertEquals(MaintenanceFormEvent.Done(MaintenanceResult.LogCreated), awaitItem())
        }
    }

    @Test
    fun `concluir envia o schedule_id e avisa que registrou`() = runTest {
        coEvery { maintenanceRepository.createLog(any()) } returns ApiResult.Success(log)
        val viewModel = viewModel(LogFormViewModel.ARG_SCHEDULE_ID to "m1")

        viewModel.events.test {
            viewModel.onSave()
            assertEquals(MaintenanceFormEvent.Done(MaintenanceResult.LogCreated), awaitItem())
        }

        coVerify {
            maintenanceRepository.createLog(LogInput("p1", "t1", LocalDateTime.of(2026, 10, 7, 13, 20), "Meio litro", scheduleId = "m1"))
        }
    }

    @Test
    fun `editar atualiza pelo id e nao manda schedule_id`() = runTest {
        coEvery { maintenanceRepository.updateLog("l1", any()) } returns ApiResult.Success(log)
        val viewModel = viewModel(LogFormViewModel.ARG_ID to "l1")
        viewModel.onNotesChange("")

        viewModel.events.test {
            viewModel.onSave()
            assertEquals(MaintenanceFormEvent.Done(MaintenanceResult.LogUpdated), awaitItem())
        }

        coVerify { maintenanceRepository.updateLog("l1", LogInput("p1", "t2", LocalDateTime.of(2026, 10, 1, 17, 15), null, scheduleId = null)) }
    }

    @Test
    fun `falha da API ao salvar mostra o erro`() = runTest {
        coEvery { maintenanceRepository.createLog(any()) } returns ApiResult.Failure(ApiError.Http(422, "agendamento informado não encontrado"))
        val viewModel = viewModel(LogFormViewModel.ARG_SCHEDULE_ID to "m1")

        viewModel.onSave()
        testScheduler.advanceUntilIdle()

        assertEquals(UiText.Dynamic("agendamento informado não encontrado"), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
