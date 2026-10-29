package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The only value the Preferencias hub shows: the chosen training intention on the "Mi entrenamiento" row. It is
 * null while loading, when none is chosen and when the read fails (no placeholder, no error state). It keeps no
 * other state; the screen calls [reload] when it returns from the editor.
 */
class PreferencesHubViewModel(private val trainingPreferences: TrainingPreferencesRepository) : ViewModel() {
    private val _intention = MutableStateFlow<TrainingIntention?>(null)
    val intention: StateFlow<TrainingIntention?> = _intention.asStateFlow()

    private var work: Job? = null

    init {
        reload()
    }

    fun reload() {
        work?.cancel()
        work = viewModelScope.launch {
            // A failed read keeps the last known value: the row never flickers or shows an error.
            trainingPreferences.get().onSuccess { _intention.value = it.intention }
        }
    }
}
