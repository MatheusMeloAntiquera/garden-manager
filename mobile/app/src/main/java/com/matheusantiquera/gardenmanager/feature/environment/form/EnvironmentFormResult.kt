package com.matheusantiquera.gardenmanager.feature.environment.form

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.matheusantiquera.gardenmanager.R
import kotlinx.coroutines.launch

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
    // A mensagem roda num escopo próprio: limpar o resultado recompõe a tela e cancelaria o LaunchedEffect.
    val scope = rememberCoroutineScope()
    val created = stringResource(R.string.environment_created)
    val updated = stringResource(R.string.environment_updated)
    val deleted = stringResource(R.string.environment_deleted)

    LaunchedEffect(result) {
        val message = when (result ?: return@LaunchedEffect) {
            EnvironmentFormResult.Created -> created
            EnvironmentFormResult.Updated -> updated
            EnvironmentFormResult.Deleted -> deleted
        }
        onShown()
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
}
