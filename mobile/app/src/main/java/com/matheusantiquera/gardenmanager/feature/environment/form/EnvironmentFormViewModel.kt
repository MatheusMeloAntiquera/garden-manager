package com.matheusantiquera.gardenmanager.feature.environment.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.core.network.toUiText
import com.matheusantiquera.gardenmanager.core.ui.UiText
import com.matheusantiquera.gardenmanager.core.ui.withMinimumDuration
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnvironmentFormUiState(
    /** Editando um ambiente existente; falso ao criar um novo. */
    val isEditing: Boolean,
    /** Carregando o ambiente que vai ser editado. */
    val isLoading: Boolean = false,
    val loadError: UiText? = null,
    val name: String = "",
    val notes: String = "",
    val active: Boolean = true,
    /** Plantas ativas no ambiente, como veio da API. */
    val plantCount: Int = 0,
    /** Nome salvo, usado no diálogo de exclusão mesmo que o campo tenha sido editado. */
    val savedName: String = "",
    val nameError: UiText? = null,
    val notesError: UiText? = null,
    /** Erro geral ao salvar ou excluir. */
    val error: UiText? = null,
    val isSaving: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
)

sealed interface EnvironmentFormEvent {
    /** O formulário terminou: a tela fecha e a anterior mostra a mensagem do [result]. */
    data class Done(val result: EnvironmentFormResult) : EnvironmentFormEvent
}

@HiltViewModel
class EnvironmentFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EnvironmentRepository,
) : ViewModel() {

    private val environmentId: String? = savedStateHandle[ARG_ENVIRONMENT_ID]

    private val _uiState = MutableStateFlow(EnvironmentFormUiState(isEditing = environmentId != null))
    val uiState: StateFlow<EnvironmentFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<EnvironmentFormEvent>(Channel.BUFFERED)
    val events: Flow<EnvironmentFormEvent> = _events.receiveAsFlow()

    init {
        if (environmentId != null) load()
    }

    fun load() {
        val id = environmentId ?: return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.get(id)) {
                is ApiResult.Success -> {
                    val env = result.value
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = env.name,
                            notes = env.notes.orEmpty(),
                            active = env.active,
                            plantCount = env.plantCount,
                            savedName = env.name,
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, loadError = result.error.toUiText()) }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null, error = null) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value, notesError = null, error = null) }

    fun onActiveChange(value: Boolean) = _uiState.update { it.copy(active = value, error = null) }

    fun onSave() {
        val current = _uiState.value
        if (current.isSaving || current.isLoading) return

        val nameError = when (EnvironmentFormValidator.validateName(current.name)) {
            EnvironmentFormValidator.NameError.Required -> UiText.Resource(R.string.validation_environment_name_required)
            EnvironmentFormValidator.NameError.TooLong -> UiText.Resource(R.string.validation_environment_name_too_long)
            null -> null
        }
        val notesError = if (EnvironmentFormValidator.isNotesTooLong(current.notes)) {
            UiText.Resource(R.string.validation_notes_too_long)
        } else {
            null
        }
        if (nameError != null || notesError != null) {
            _uiState.update { it.copy(nameError = nameError, notesError = notesError) }
            return
        }

        val name = current.name.trim()
        val notes = EnvironmentFormValidator.normalizeNotes(current.notes)
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = withMinimumDuration(MIN_PROGRESS_MILLIS) {
                if (environmentId == null) {
                    repository.create(name = name, notes = notes)
                } else {
                    repository.update(id = environmentId, name = name, notes = notes, active = current.active)
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    val outcome = if (environmentId == null) EnvironmentFormResult.Created else EnvironmentFormResult.Updated
                    _events.send(EnvironmentFormEvent.Done(outcome))
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isSaving = false, error = result.error.toUiText()) }
            }
        }
    }

    fun onDeleteClick() {
        if (environmentId == null || _uiState.value.isLoading) return
        _uiState.update { it.copy(showDeleteConfirmation = true, error = null) }
    }

    fun onDeleteDismiss() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun onDeleteConfirm() {
        val id = environmentId ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = withMinimumDuration(MIN_PROGRESS_MILLIS) { repository.delete(id) }) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isDeleting = false, showDeleteConfirmation = false) }
                    _events.send(EnvironmentFormEvent.Done(EnvironmentFormResult.Deleted))
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isDeleting = false, showDeleteConfirmation = false, error = result.error.toUiText())
                }
            }
        }
    }

    companion object {
        /** Nome do argumento em [com.matheusantiquera.gardenmanager.navigation.EnvironmentFormRoute]. */
        const val ARG_ENVIRONMENT_ID = "id"

        /** Tempo mínimo das janelas "Salvando…" e "Excluindo…" (ver [com.matheusantiquera.gardenmanager.core.ui.MIN_PROGRESS_MILLIS]). */
        const val MIN_PROGRESS_MILLIS = com.matheusantiquera.gardenmanager.core.ui.MIN_PROGRESS_MILLIS
    }
}
