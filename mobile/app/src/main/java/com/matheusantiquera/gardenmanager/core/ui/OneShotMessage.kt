package com.matheusantiquera.gardenmanager.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Mostra [message] uma única vez no [snackbarHostState] e chama [onShown] para que quem guarda a
 * mensagem a limpe, e ela não apareça de novo ao voltar para a tela. Usado pelas listas para a
 * mensagem que o formulário deixa ao fechar ("Ambiente criado", "Planta excluída"…).
 */
@Composable
fun ShowOneShotMessage(message: String?, onShown: () -> Unit, snackbarHostState: SnackbarHostState) {
    // A mensagem roda num escopo próprio: limpar a origem recompõe a tela e cancelaria o LaunchedEffect.
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        onShown()
        scope.launch { snackbarHostState.showSnackbar(text) }
    }
}
