package com.matheusantiquera.gardenmanager.feature.plant.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantFormValidatorTest {

    @Test
    fun `precisa de apelido ou de especie`() {
        assertTrue(PlantFormValidator.isMissingNicknameAndSpecies(nickname = "  ", speciesId = null))
        assertFalse(PlantFormValidator.isMissingNicknameAndSpecies(nickname = "Monstrinha", speciesId = null))
        assertFalse(PlantFormValidator.isMissingNicknameAndSpecies(nickname = "", speciesId = "s1"))
    }

    @Test
    fun `apelido ate 100 caracteres sem contar espacos nas pontas, emoji conta um`() {
        assertFalse(PlantFormValidator.isNicknameTooLong("  " + "a".repeat(100) + " "))
        assertTrue(PlantFormValidator.isNicknameTooLong("a".repeat(101)))
        assertFalse(PlantFormValidator.isNicknameTooLong("🌿".repeat(100)))
        assertTrue(PlantFormValidator.isNicknameTooLong("🌿".repeat(101)))
    }

    @Test
    fun `observacoes ate 2000 caracteres`() {
        assertFalse(PlantFormValidator.isNotesTooLong("a".repeat(2000)))
        assertTrue(PlantFormValidator.isNotesTooLong("a".repeat(2001)))
    }

    @Test
    fun `texto em branco vira nulo e o resto perde os espacos das pontas`() {
        assertNull(PlantFormValidator.normalize("   "))
        assertEquals("Monstrinha", PlantFormValidator.normalize("  Monstrinha "))
    }
}
