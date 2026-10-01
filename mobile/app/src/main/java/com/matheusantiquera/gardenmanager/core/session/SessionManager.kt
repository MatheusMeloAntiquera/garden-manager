package com.matheusantiquera.gardenmanager.core.session

import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.datastore.TokenStore
import com.matheusantiquera.gardenmanager.core.di.ApplicationScope
import com.matheusantiquera.gardenmanager.core.ui.UiText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SessionState {
    /** Ainda lendo os tokens guardados. */
    data object Loading : SessionState
    data object LoggedIn : SessionState
    data object LoggedOut : SessionState
}

/** Estado da sessão, derivado dos tokens guardados, e avisos para mostrar na tela de login. */
@Singleton
class SessionManager @Inject constructor(
    tokenStore: TokenStore,
    @ApplicationScope scope: CoroutineScope,
) {
    val state: StateFlow<SessionState> = tokenStore.tokens
        .map { if (it == null) SessionState.LoggedOut else SessionState.LoggedIn }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    private val _notice = MutableStateFlow<UiText?>(null)

    /** Aviso exibido na tela de login (sessão expirada, conta criada). */
    val notice: StateFlow<UiText?> = _notice.asStateFlow()

    fun postNotice(text: UiText) {
        _notice.value = text
    }

    fun clearNotice() {
        _notice.value = null
    }

    /** Chamado quando o refresh token deixou de valer e o usuário precisa entrar de novo. */
    fun onSessionExpired() {
        postNotice(UiText.Resource(R.string.notice_session_expired))
    }
}
