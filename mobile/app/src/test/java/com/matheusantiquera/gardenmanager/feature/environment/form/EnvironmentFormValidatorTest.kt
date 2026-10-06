package com.matheusantiquera.gardenmanager.feature.environment.form

import com.matheusantiquera.gardenmanager.feature.environment.form.EnvironmentFormValidator.NameError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentFormValidatorTest {

    @Test
    fun `nome vazio ou so com espacos e obrigatorio`() {
        assertEquals(NameError.Required, EnvironmentFormValidator.validateName(""))
        assertEquals(NameError.Required, EnvironmentFormValidator.validateName("   "))
    }

    @Test
    fun `nome valido ate 100 caracteres sem contar espacos nas pontas`() {
        assertNull(EnvironmentFormValidator.validateName("Sala"))
        assertNull(EnvironmentFormValidator.validateName("  " + "a".repeat(100) + "  "))
        assertEquals(NameError.TooLong, EnvironmentFormValidator.validateName("a".repeat(101)))
    }

    @Test
    fun `emoji conta como um caractere, como na API`() {
        // Cada emoji ocupa duas unidades UTF-16, mas a API conta um caractere.
        assertNull(EnvironmentFormValidator.validateName("🌿".repeat(100)))
        assertEquals(NameError.TooLong, EnvironmentFormValidator.validateName("🌿".repeat(101)))
    }

    @Test
    fun `observacoes ate 2000 caracteres`() {
        assertFalse(EnvironmentFormValidator.isNotesTooLong("a".repeat(2000)))
        assertTrue(EnvironmentFormValidator.isNotesTooLong("a".repeat(2001)))
    }

    @Test
    fun `observacoes em branco viram nulo e as demais perdem os espacos das pontas`() {
        assertNull(EnvironmentFormValidator.normalizeNotes("   "))
        assertEquals("Luz da manhã", EnvironmentFormValidator.normalizeNotes("  Luz da manhã \n"))
    }
}
