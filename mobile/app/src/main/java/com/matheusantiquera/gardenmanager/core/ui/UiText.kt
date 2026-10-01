package com.matheusantiquera.gardenmanager.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Texto exibido na tela: vem de um recurso de string ou de uma mensagem pronta (ex.: da API). */
sealed interface UiText {
    data class Resource(@StringRes val id: Int) : UiText
    data class Dynamic(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id)
    is UiText.Dynamic -> value
}
