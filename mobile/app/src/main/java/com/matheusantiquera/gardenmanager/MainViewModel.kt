package com.matheusantiquera.gardenmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.core.datastore.PreferencesStore
import com.matheusantiquera.gardenmanager.core.datastore.ThemeMode
import com.matheusantiquera.gardenmanager.core.session.SessionManager
import com.matheusantiquera.gardenmanager.core.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    sessionManager: SessionManager,
    preferencesStore: PreferencesStore,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionManager.state

    /** `null` enquanto a preferência ainda não foi lida; a tela usa o tema do sistema nesse meio-tempo. */
    val themeMode: StateFlow<ThemeMode?> = preferencesStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
