package com.matheusantiquera.gardenmanager.feature.plant.form

/** Regras do formulário de planta, espelhando as da API (`CreateInput` e `UpdateInput` de plantas). */
object PlantFormValidator {
    const val NICKNAME_MAX = 100
    const val NOTES_MAX = 2000

    /** Toda planta precisa de apelido ou de espécie. */
    fun isMissingNicknameAndSpecies(nickname: String, speciesId: String?): Boolean = nickname.isBlank() && speciesId == null

    fun isNicknameTooLong(nickname: String): Boolean = nickname.trim().charCount() > NICKNAME_MAX

    fun isNotesTooLong(notes: String): Boolean = notes.trim().charCount() > NOTES_MAX

    /** Texto em branco vira nulo, como a API grava. */
    fun normalize(text: String): String? = text.trim().ifEmpty { null }

    // A API conta caracteres Unicode, não unidades UTF-16: um emoji conta como um só.
    private fun String.charCount(): Int = codePointCount(0, length)
}
