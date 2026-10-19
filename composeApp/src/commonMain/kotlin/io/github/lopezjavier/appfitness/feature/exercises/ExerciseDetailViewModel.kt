package io.github.lopezjavier.appfitness.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    data object Error : ExerciseDetailUiState

    data class Loaded(val detail: ExerciseDetail) : ExerciseDetailUiState
}

/** Loads one exercise for the detail screen. */
class ExerciseDetailViewModel(private val exerciseId: String, private val loadCatalog: LoadExerciseCatalog) :
    ViewModel() {
    private val _uiState = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        if (_uiState.value == ExerciseDetailUiState.Error) load()
    }

    private fun load() {
        _uiState.value = ExerciseDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value =
                loadCatalog.detail(exerciseId).fold(
                    onSuccess = { ExerciseDetailUiState.Loaded(it) },
                    onFailure = { ExerciseDetailUiState.Error },
                )
        }
    }
}
