package com.matheusantiquera.gardenmanager.feature.maintenance

import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceSchedule
import com.matheusantiquera.gardenmanager.data.maintenance.formatMaintenanceDateTime
import com.matheusantiquera.gardenmanager.data.maintenance.parseMaintenanceDateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MaintenanceFormatTest {

    private val today = LocalDate.of(2026, 10, 1)

    private fun schedule(dueAt: LocalDateTime, overdue: Boolean = false) =
        MaintenanceSchedule("m1", "p1", "Monstrinha", "t1", "Rega", dueAt, overdue)

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
    fun `le e escreve a data no formato da API`() {
        assertEquals(LocalDateTime.of(2026, 9, 29, 9, 0, 30), parseMaintenanceDateTime("2026-09-29 09:00:30"))
        assertEquals("2026-10-08 08:00:00", formatMaintenanceDateTime(LocalDateTime.of(2026, 10, 8, 8, 0)))
    }

    @Test
    fun `atrasada conta os dias desde o vencimento`() {
        assertEquals(AgendaDue.Overdue(0, today.atTime(8, 0)), schedule(today.atTime(8, 0), overdue = true).agendaDue(today))
        assertEquals(AgendaDue.Overdue(1, today.minusDays(1).atTime(9, 0)), schedule(today.minusDays(1).atTime(9, 0), overdue = true).agendaDue(today))
        assertEquals(AgendaDue.Overdue(2, today.minusDays(2).atTime(9, 0)), schedule(today.minusDays(2).atTime(9, 0), overdue = true).agendaDue(today))
    }

    @Test
    fun `agendamento no futuro conta os dias a partir de hoje`() {
        assertEquals(AgendaDue.Upcoming(0, today.atTime(22, 0)), schedule(today.atTime(22, 0)).agendaDue(today))
        assertEquals(AgendaDue.Upcoming(1, today.plusDays(1).atTime(8, 0)), schedule(today.plusDays(1).atTime(8, 0)).agendaDue(today))
    }

    @Test
    fun `grupos da Agenda separam atrasadas, ate 7 dias e mais tarde`() {
        assertEquals(AgendaGroup.Overdue, schedule(today.minusDays(3).atTime(8, 0), overdue = true).agendaDue(today).group)
        assertEquals(AgendaGroup.ThisWeek, schedule(today.atTime(22, 0)).agendaDue(today).group)
        assertEquals(AgendaGroup.ThisWeek, schedule(today.plusDays(7).atTime(8, 0)).agendaDue(today).group)
        assertEquals(AgendaGroup.Later, schedule(today.plusDays(8).atTime(8, 0)).agendaDue(today).group)
    }

    @Test
    fun `dia da semana e data como no canvas`() {
        assertEquals("Sex, 03/10", formatWeekdayDate(LocalDateTime.of(2025, 10, 3, 9, 0)))
        assertEquals("Seg, 13/10", formatWeekdayDate(LocalDateTime.of(2025, 10, 13, 0, 0)))
        assertEquals("Dom, 12/10", formatWeekdayDate(LocalDateTime.of(2025, 10, 12, 0, 0)))
    }

    @Test
    fun `data e hora dos formularios`() {
        assertEquals("08:00", formatTime(LocalTime.of(8, 0)))
        assertEquals("03/10/2026", formatDate(LocalDate.of(2026, 10, 3)))
    }
}
