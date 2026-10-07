package com.matheusantiquera.gardenmanager.feature.plant.detail

import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import com.matheusantiquera.gardenmanager.data.maintenance.parseMaintenanceDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MaintenanceFormatTest {

    private val today = LocalDate.of(2026, 10, 1)

    private fun schedule(dueAt: LocalDateTime, overdue: Boolean = false) = MaintenanceSchedule("m1", "Rega", dueAt, overdue)

    @Test
    fun `vencido pela API e Atrasada, mesmo que seja hoje`() {
        assertEquals(DueStatus.Overdue, schedule(today.atTime(8, 0), overdue = true).dueStatus(today))
    }

    @Test
    fun `ainda hoje e Hoje, amanha e Amanha, depois conta os dias`() {
        assertEquals(DueStatus.Today, schedule(today.atTime(22, 0)).dueStatus(today))
        assertEquals(DueStatus.Tomorrow, schedule(today.plusDays(1).atStartOfDay()).dueStatus(today))
        assertEquals(DueStatus.InDays(12), schedule(today.plusDays(12).atTime(9, 0)).dueStatus(today))
    }

    @Test
    fun `prazo mostra a hora, menos quando e meia-noite`() {
        assertEquals("29/09, 09:00", formatDueAt(LocalDateTime.of(2026, 9, 29, 9, 0)))
        assertEquals("13/10", formatDueAt(LocalDateTime.of(2026, 10, 13, 0, 0)))
    }

    @Test
    fun `historico mostra data completa e hora`() {
        assertEquals("22/09/2026 08:40", formatPerformedAt(LocalDateTime.of(2026, 9, 22, 8, 40)))
        assertEquals("01/10/2026 00:00", formatPerformedAt(LocalDateTime.of(2026, 10, 1, 0, 0)))
    }

    @Test
    fun `le a data no formato da API`() {
        assertEquals(LocalDateTime.of(2026, 9, 29, 9, 0, 30), parseMaintenanceDateTime("2026-09-29 09:00:30"))
    }
}
