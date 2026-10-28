package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.RestSound
import io.github.lopezjavier.appfitness.domain.port.AlertSound
import io.github.lopezjavier.appfitness.domain.port.RestAlertPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The candidates and the one in use. */
data class RestSoundChooserUiState(
    val sounds: List<RestSound> = RestSound.entries.toList(),
    val selected: RestSound = RestSound.Default,
)

/**
 * TEMPORARY - removed by T-148. Lets the user compare the end-of-rest sound candidates at Checkpoint E12-A: choosing
 * one plays it and saves it at once (the alert then uses it).
 */
class RestSoundChooserViewModel(private val preferences: RestAlertPreferences, private val alertSound: AlertSound) :
    ViewModel() {
    val uiState: StateFlow<RestSoundChooserUiState> = preferences.sound
        .map { RestSoundChooserUiState(selected = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RestSoundChooserUiState())

    fun select(sound: RestSound) {
        alertSound.preview(sound)
        viewModelScope.launch { preferences.setSound(sound) }
    }
}
