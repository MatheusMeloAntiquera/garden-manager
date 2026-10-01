package com.matheusantiquera.gardenmanager.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.core.datastore.PreferencesStore
import com.matheusantiquera.gardenmanager.core.datastore.ThemeMode
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.data.auth.AuthRepository
import com.matheusantiquera.gardenmanager.data.auth.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProfileUserState {
    data object Loading : ProfileUserState
    data class Loaded(val user: User) : ProfileUserState
    data class Failed(val message: UiText) : ProfileUserState
}

data class ProfileUiState(
    val user: ProfileUserState = ProfileUserState.Loading,
    val isLoggingOut: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesStore: PreferencesStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = preferencesStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    init {
        loadUser()
    }

    fun loadUser() {
        _uiState.update { it.copy(user = ProfileUserState.Loading) }
        viewModelScope.launch {
            val result = authRepository.currentUser()
            _uiState.update {
                it.copy(
                    user = when (result) {
                        is ApiResult.Success -> ProfileUserState.Loaded(result.value)
                        is ApiResult.Failure -> ProfileUserState.Failed(result.error.toUiText())
                    },
                )
            }
        }
    }

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { preferencesStore.setThemeMode(mode) }
    }

    /** Apaga a sessão; a navegação volta ao login sozinha quando os tokens somem. */
    fun onLogout() {
        if (_uiState.value.isLoggingOut) return
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(isLoggingOut = false) }
        }
    }
}
