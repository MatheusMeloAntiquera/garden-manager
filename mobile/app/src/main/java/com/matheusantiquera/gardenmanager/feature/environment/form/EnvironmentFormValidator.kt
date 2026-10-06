package com.matheusantiquera.gardenmanager.feature.environment.form

/** Regras do formulário de ambiente, espelhando as da API (`CreateInput` e `UpdateInput` de ambientes). */
object EnvironmentFormValidator {
    const val NAME_MAX = 100
    const val NOTES_MAX = 2000

    enum class NameError { Required, TooLong }

    /** O nome é obrigatório depois de tirar os espaços das pontas. */
    fun validateName(name: String): NameError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameError.Required
            trimmed.charCount() > NAME_MAX -> NameError.TooLong
            else -> null
        }
    }

    fun isNotesTooLong(notes: String): Boolean = notes.trim().charCount() > NOTES_MAX

    /** Observações em branco viram nulo, como a API grava. */
    fun normalizeNotes(notes: String): String? = notes.trim().ifEmpty { null }

    // A API conta caracteres Unicode, não unidades UTF-16: um emoji conta como um só.
    private fun String.charCount(): Int = codePointCount(0, length)
}
