package com.matheusantiquera.gardenmanager.feature.auth.signup

import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneOffset

/** Regras de validação do cadastro, espelhando as da API (`SignupInput` e `backend/pkg/validator`). */
object SignupValidator {
    const val NAME_MIN = 2
    const val NAME_MAX = 100
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 128
    const val EMAIL_MAX = 255

    val MIN_BIRTH_DATE: LocalDate = LocalDate.of(1900, 1, 1)

    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isValidName(name: String): Boolean = name.trim().length in NAME_MIN..NAME_MAX

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.length <= EMAIL_MAX && emailRegex.matches(trimmed)
    }

    data class PasswordRules(
        val hasMinLength: Boolean,
        val hasDigit: Boolean,
        val hasSpecial: Boolean,
    ) {
        val allMet: Boolean get() = hasMinLength && hasDigit && hasSpecial
    }

    fun passwordRules(password: String): PasswordRules = PasswordRules(
        hasMinLength = password.length >= PASSWORD_MIN,
        hasDigit = password.any { it.isDigit() },
        // Mesmo critério da API: nem letra, nem dígito, nem espaço.
        hasSpecial = password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
    )

    /** A API limita a senha a 128 bytes. */
    fun isPasswordTooLong(password: String): Boolean = password.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX

    sealed interface BirthDateResult {
        data class Valid(val date: LocalDate) : BirthDateResult

        /** Faltam dígitos ou a data não existe (ex.: 31/02/1990). */
        data object Invalid : BirthDateResult

        /** A data existe, mas está antes de 01/01/1900 ou no futuro. */
        data object OutOfRange : BirthDateResult
    }

    /**
     * Interpreta os dígitos digitados (`ddMMaaaa`, sem barras) como data de nascimento.
     * A API compara com a data de hoje em UTC, então [today] também usa UTC por padrão.
     */
    fun parseBirthDate(digits: String, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): BirthDateResult {
        if (digits.length != 8 || !digits.all { it.isDigit() }) return BirthDateResult.Invalid

        val date = try {
            LocalDate.of(digits.substring(4, 8).toInt(), digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt())
        } catch (e: DateTimeException) {
            return BirthDateResult.Invalid
        }

        return if (date.isBefore(MIN_BIRTH_DATE) || date.isAfter(today)) BirthDateResult.OutOfRange else BirthDateResult.Valid(date)
    }
}
