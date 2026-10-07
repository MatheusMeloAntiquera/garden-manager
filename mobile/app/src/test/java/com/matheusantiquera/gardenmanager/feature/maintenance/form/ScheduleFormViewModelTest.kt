package com.matheusantiquera.gardenmanager.feature.maintenance.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.ui.MIN_PROGRESS_MILLIS
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceType
import com.matheusantiquera.gardenmanager.data.maintenance.ScheduleInput
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
import kotlinx.coroutines.test.advanceTimeBy
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
class ScheduleFormViewModelTest {

    private val plantRepository = mockk<PlantRepository>()
    private val maintenanceRepository = mockk<MaintenanceRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-10-07T13:20:00Z"), ZoneOffset.UTC)

    private val plant = Plant("p1", "Monstrinha", "Monstrinha", null, true, null, null)
    private val rega = MaintenanceType("t1", "Rega")
    private val poda = MaintenanceType("t2", "Poda")
    private val schedule = MaintenanceSchedule(
        "m1", "p1", "Monstrinha", "t2", "Poda", LocalDateTime.of(2026, 10, 12, 17, 30), overdue = false, notes = "Tesoura limpa",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { plantRepository.get("p1") } returns ApiResult.Success(plant)
        coEvery { maintenanceRepository.types() } returns ApiResult.Success(listOf(rega, poda))
        coEvery { maintenanceRepository.getSchedule("m1") } returns ApiResult.Success(schedule)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(vararg args: Pair<String, Any?>) =
        ScheduleFormViewModel(SavedStateHandle(mapOf(ScheduleFormViewModel.ARG_PLANT_ID to "p1", *args)), plantRepository, maintenanceRepository, clock)

    @Test
    fun `criacao vem com a planta, os tipos e o prazo padrao de amanha as 8 horas`() {
        val state = viewModel().uiState.value

        assertFalse(state.isEditing)
        assertFalse(state.isLoading)
        assertEquals("Monstrinha", state.plantName)
        assertEquals(listOf(rega, poda), state.types)
        assertNull(state.typeId)
        assertEquals(LocalDate.of(2026, 10, 8), state.date)
        assertEquals(LocalTime.of(8, 0), state.time)
    }

    @Test
    fun `edicao carrega tipo, prazo e observacoes do agendamento`() {
        val state = viewModel(ScheduleFormViewModel.ARG_ID to "m1").uiState.value

        assertTrue(state.isEditing)
        assertEquals("t2", state.typeId)
        assertEquals(LocalDate.of(2026, 10, 12), state.date)
        assertEquals(LocalTime.of(17, 30), state.time)
        assertEquals("Tesoura limpa", state.notes)
    }

    @Test
    fun `falha ao carregar mostra o erro e tentar de novo recarrega`() {
        coEvery { maintenanceRepository.types() } returns ApiResult.Failure(ApiError.Network)
        val viewModel = viewModel()
        assertEquals(UiText.Resource(R.string.error_network), viewModel.uiState.value.loadError)

        coEvery { maintenanceRepository.types() } returns ApiResult.Success(listOf(rega))
        viewModel.load()

        assertNull(viewModel.uiState.value.loadError)
        assertEquals(listOf(rega), viewModel.uiState.value.types)
    }

    @Test
    fun `salvar sem tipo mostra erro e nao chama a API`() {
        val viewModel = viewModel()

        viewModel.onSave()

        assertEquals(UiText.Resource(R.string.validation_maintenance_type_required), viewModel.uiState.value.typeError)
        coVerify(exactly = 0) { maintenanceRepository.createSchedule(any()) }
    }

    @Test
    fun `observacoes grandes demais mostram erro`() {
        val viewModel = viewModel()
        viewModel.onTypeChange("t1")
        viewModel.onNotesChange("a".repeat(2001))

        viewModel.onSave()

        assertEquals(UiText.Resource(R.string.validation_notes_too_long), viewModel.uiState.value.notesError)
        coVerify(exactly = 0) { maintenanceRepository.createSchedule(any()) }
    }

    @Test
    fun `escolher o tipo limpa o erro dele`() {
        val viewModel = viewModel()
        viewModel.onSave()

        viewModel.onTypeChange("t1")

        assertNull(viewModel.uiState.value.typeError)
    }

    @Test
    fun `criar envia o prazo escolhido e avisa que agendou`() = runTest {
        coEvery { maintenanceRepository.createSchedule(any()) } returns ApiResult.Success(schedule)
        val viewModel = viewModel()
        viewModel.onTypeChange("t1")
        viewModel.onDateChange(LocalDate.of(2026, 10, 10))
        viewModel.onTimeChange(LocalTime.of(9, 15))
        viewModel.onNotesChange("  Meio litro ")

        viewModel.events.test {
            viewModel.onSave()
            assertEquals(MaintenanceFormEvent.Done(MaintenanceResult.ScheduleCreated), awaitItem())
        }

        coVerify {
            maintenanceRepository.createSchedule(ScheduleInput("p1", "t1", LocalDateTime.of(2026, 10, 10, 9, 15), "Meio litro"))
        }
    }

    @Test
    fun `editar atualiza pelo id e avisa que atualizou`() = runTest {
        coEvery { maintenanceRepository.updateSchedule("m1", any()) } returns ApiResult.Success(schedule)
        val viewModel = viewModel(ScheduleFormViewModel.ARG_ID to "m1")

        viewModel.events.test {
            viewModel.onSave()
            assertEquals(MaintenanceFormEvent.Done(MaintenanceResult.ScheduleUpdated), awaitItem())
        }

        coVerify { maintenanceRepository.updateSchedule("m1", ScheduleInput("p1", "t2", LocalDateTime.of(2026, 10, 12, 17, 30), "Tesoura limpa")) }
    }

    @Test
    fun `salvando fica no minimo 2 segundos`() = runTest {
        coEvery { maintenanceRepository.createSchedule(any()) } returns ApiResult.Success(schedule)
        val viewModel = viewModel()
        viewModel.onTypeChange("t1")

        viewModel.onSave()
        advanceTimeBy(MIN_PROGRESS_MILLIS - 1)
        assertTrue(viewModel.uiState.value.isSaving)

        advanceTimeBy(2)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `falha da API ao salvar mostra o erro e deixa tentar de novo`() = runTest {
        coEvery { maintenanceRepository.createSchedule(any()) } returns ApiResult.Failure(ApiError.Http(422, "planta informada não encontrada"))
        val viewModel = viewModel()
        viewModel.onTypeChange("t1")

        viewModel.onSave()
        advanceTimeBy(MIN_PROGRESS_MILLIS + 1)

        assertEquals(UiText.Dynamic("planta informada não encontrada"), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
