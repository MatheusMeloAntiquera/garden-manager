package com.matheusantiquera.gardenmanager.data.auth

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class User(
    val id: String,
    val name: String,
    val email: String,
    val birthDate: LocalDate?,
) {
    /** Iniciais para o avatar: primeira letra do primeiro e do último nome. */
    val initials: String
        get() {
            val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val letters = when (words.size) {
                0 -> ""
                1 -> words.first().take(1)
                else -> words.first().take(1) + words.last().take(1)
            }
            return letters.uppercase()
        }

    /** Primeiro nome, para a saudação. */
    val firstName: String
        get() = name.trim().split(Regex("\\s+")).first()
}

private val BirthDateDisplayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun LocalDate.formatBirthDate(): String = format(BirthDateDisplayFormat)

fun UserResponse.toUser(): User = User(
    id = id,
    name = name,
    email = email,
    birthDate = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
)
