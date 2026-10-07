package com.matheusantiquera.gardenmanager.core.ui

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tempo mínimo das janelas "Salvando…" e "Excluindo…" dos formulários. A API costuma responder em
 * milissegundos, e sem esse mínimo a janela só pisca na tela.
 */
const val MIN_PROGRESS_MILLIS = 2_000L

/** Executa [block] e só devolve o resultado depois de passados pelo menos [millis], contados em paralelo. */
suspend fun <T> withMinimumDuration(millis: Long = MIN_PROGRESS_MILLIS, block: suspend () -> T): T = coroutineScope {
    val minimum = launch { delay(millis) }
    val result = block()
    minimum.join()
    result
}
