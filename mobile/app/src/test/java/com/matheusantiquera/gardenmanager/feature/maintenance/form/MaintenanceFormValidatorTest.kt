package com.matheusantiquera.gardenmanager.feature.maintenance.form

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceFormValidatorTest {

    @Test
    fun `observacoes passam de 2000 caracteres so depois do trim`() {
        assertFalse(MaintenanceFormValidator.isNotesTooLong("a".repeat(2000)))
        assertFalse(MaintenanceFormValidator.isNotesTooLong("  " + "a".repeat(2000) + "  "))
        assertTrue(MaintenanceFormValidator.isNotesTooLong("a".repeat(2001)))
    }

    @Test
    fun `emoji conta como um caractere, como na API`() {
        assertFalse(MaintenanceFormValidator.isNotesTooLong("🌱".repeat(2000)))
        assertTrue(MaintenanceFormValidator.isNotesTooLong("🌱".repeat(2001)))
    }

    @Test
    fun `texto em branco vira nulo e o resto vem sem espacos nas pontas`() {
        assertNull(MaintenanceFormValidator.normalize("   "))
        assertEquals("Luz", MaintenanceFormValidator.normalize("  Luz "))
    }

    @Test
    fun `data no futuro e comparada por minuto`() {
        val now = LocalDateTime.of(2026, 10, 7, 8, 40, 30)

        assertFalse(MaintenanceFormValidator.isInFuture(LocalDateTime.of(2026, 10, 7, 8, 40, 59), now))
        assertFalse(MaintenanceFormValidator.isInFuture(LocalDateTime.of(2026, 10, 7, 8, 39), now))
        assertTrue(MaintenanceFormValidator.isInFuture(LocalDateTime.of(2026, 10, 7, 8, 41), now))
        assertTrue(MaintenanceFormValidator.isInFuture(LocalDateTime.of(2026, 10, 8, 0, 0), now))
    }

    @Test
    fun `prazo padrao e amanha as 8 horas`() {
        assertEquals(LocalDateTime.of(2026, 10, 8, 8, 0), MaintenanceFormValidator.defaultDueAt(LocalDate.of(2026, 10, 7)))
    }
}
