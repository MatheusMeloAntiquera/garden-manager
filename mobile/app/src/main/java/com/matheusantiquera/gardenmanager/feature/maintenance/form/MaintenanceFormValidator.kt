package com.matheusantiquera.gardenmanager.feature.maintenance.form

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Regras dos formulários de agendamento e de registro, espelhando as da API. */
object MaintenanceFormValidator {
    const val NOTES_MAX = 2000

    /** Hora padrão de um novo agendamento: evita o prazo à meia-noite, que já nasceria atrasado no dia. */
    val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(8, 0)

    fun isNotesTooLong(notes: String): Boolean = notes.trim().codePointCount(0, notes.trim().length) > NOTES_MAX

    /** Texto em branco vira nulo, como a API grava. */
    fun normalize(text: String): String? = text.trim().ifEmpty { null }

    /** A API recusa execução com data no futuro; aqui a comparação é por minuto. */
    fun isInFuture(performedAt: LocalDateTime, now: LocalDateTime): Boolean =
        performedAt.truncatedTo(ChronoUnit.MINUTES).isAfter(now.truncatedTo(ChronoUnit.MINUTES))

    /** Prazo padrão de um novo agendamento: amanhã às 08:00. */
    fun defaultDueAt(today: LocalDate): LocalDateTime = today.plusDays(1).atTime(DEFAULT_DUE_TIME)
}
