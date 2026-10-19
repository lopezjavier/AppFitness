package io.github.lopezjavier.appfitness.feature.analytics

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleVolumeHistory

sealed interface MuscleVolumeUiState {
    data object Loading : MuscleVolumeUiState

    /** No muscle group has volume in the last 8 weeks. */
    data object Empty : MuscleVolumeUiState

    /** The data could not be read (also when offline); [MuscleVolumeEvent.Retry] tries again. */
    data object Error : MuscleVolumeUiState

    /** Eight weeks per group in [history]; [selected] is the group shown alone, or null for all groups. */
    data class Content(val history: MuscleVolumeHistory, val selected: MuscleGroup?) : MuscleVolumeUiState
}

sealed interface MuscleVolumeEvent {
    data object Retry : MuscleVolumeEvent

    data class SelectGroup(val group: MuscleGroup?) : MuscleVolumeEvent
}
