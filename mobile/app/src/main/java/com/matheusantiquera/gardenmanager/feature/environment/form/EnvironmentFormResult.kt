package com.matheusantiquera.gardenmanager.feature.environment.form

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.ui.ShowOneShotMessage

/** Como o formulário de ambiente terminou. A tela anterior mostra a mensagem correspondente. */
enum class EnvironmentFormResult { Created, Updated, Deleted }

/** Chave do `savedStateHandle` da tela anterior onde o formulário deixa o [EnvironmentFormResult]. */
const val ENVIRONMENT_FORM_RESULT_KEY = "environment_form_result"

/**
 * Mostra no [snackbarHostState] a mensagem do resultado que o formulário deixou e chama [onShown]
 * para limpar o resultado, que assim não aparece de novo ao voltar para a tela.
 */
@Composable
internal fun ShowEnvironmentFormResult(
    result: EnvironmentFormResult?,
    onShown: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val message = when (result) {
        EnvironmentFormResult.Created -> stringResource(R.string.environment_created)
        EnvironmentFormResult.Updated -> stringResource(R.string.environment_updated)
        EnvironmentFormResult.Deleted -> stringResource(R.string.environment_deleted)
        null -> null
    }
    ShowOneShotMessage(message = message, onShown = onShown, snackbarHostState = snackbarHostState)
}
