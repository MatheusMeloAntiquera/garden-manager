package com.matheusantiquera.gardenmanager.feature.auth.signup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupValidatorTest {

    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun `senha exige 8 caracteres, um numero e um caractere especial`() {
        val cases = mapOf(
            "Sup3r\$ecret" to true,
            "abc1!" to false, // curta
            "abcdefgh!" to false, // sem numero
            "abcdefgh1" to false, // sem especial
            "abcd efg1" to false, // espaco nao conta como especial
            "abcdefg1@" to true,
        )

        cases.forEach { (password, valid) ->
            assertEquals("senha: $password", valid, SignupValidator.passwordRules(password).allMet)
        }
    }

    @Test
    fun `senha de mais de 128 bytes e rejeitada`() {
        assertFalse(SignupValidator.isPasswordTooLong("a".repeat(128)))
        assertTrue(SignupValidator.isPasswordTooLong("a".repeat(129)))
        // 65 caracteres de 2 bytes passam de 128 bytes, como na API.
        assertTrue(SignupValidator.isPasswordTooLong("ç".repeat(65)))
    }

    @Test
    fun `nome precisa ter de 2 a 100 caracteres sem contar espacos nas pontas`() {
        assertFalse(SignupValidator.isValidName("A"))
        assertFalse(SignupValidator.isValidName("  A  "))
        assertTrue(SignupValidator.isValidName("Ana"))
        assertTrue(SignupValidator.isValidName("a".repeat(100)))
        assertFalse(SignupValidator.isValidName("a".repeat(101)))
    }

    @Test
    fun `email precisa ter formato valido`() {
        assertTrue(SignupValidator.isValidEmail("ana@email.com"))
        assertTrue(SignupValidator.isValidEmail("  ana@email.com  "))
        assertFalse(SignupValidator.isValidEmail("ana"))
        assertFalse(SignupValidator.isValidEmail("ana@email"))
        assertFalse(SignupValidator.isValidEmail("ana @email.com"))
        assertFalse(SignupValidator.isValidEmail(""))
    }

    @Test
    fun `data de nascimento valida`() {
        val result = SignupValidator.parseBirthDate("14031994", today)

        assertEquals(SignupValidator.BirthDateResult.Valid(LocalDate.of(1994, 3, 14)), result)
    }

    @Test
    fun `data de nascimento incompleta ou inexistente e invalida`() {
        assertEquals(SignupValidator.BirthDateResult.Invalid, SignupValidator.parseBirthDate("1403199", today))
        assertEquals(SignupValidator.BirthDateResult.Invalid, SignupValidator.parseBirthDate("", today))
        assertEquals(SignupValidator.BirthDateResult.Invalid, SignupValidator.parseBirthDate("31021990", today))
        assertEquals(SignupValidator.BirthDateResult.Invalid, SignupValidator.parseBirthDate("14134194", today))
    }

    @Test
    fun `data de nascimento fora do intervalo da API`() {
        // Antes de 01/01/1900.
        assertEquals(SignupValidator.BirthDateResult.OutOfRange, SignupValidator.parseBirthDate("31121899", today))
        // No futuro.
        assertEquals(SignupValidator.BirthDateResult.OutOfRange, SignupValidator.parseBirthDate("02102026", today))
        // Limites inclusivos.
        assertEquals(SignupValidator.BirthDateResult.Valid(LocalDate.of(1900, 1, 1)), SignupValidator.parseBirthDate("01011900", today))
        assertEquals(SignupValidator.BirthDateResult.Valid(today), SignupValidator.parseBirthDate("01102026", today))
    }
}
