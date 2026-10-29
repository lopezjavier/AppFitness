package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues

sealed interface TrainerUiState {
    data object Loading : TrainerUiState

    /**
     * Derived values. [editSheet] is the activity level picked in the open edit sheet (null when the sheet is closed);
     * [confirming] is true while a confirm or save call is in flight. [allConfirmed] mirrors the values (plan gate).
     */
    data class Content(
        val values: CoachDerivedValues,
        val confirming: Boolean = false,
        val editSheet: ActivityLevel? = null,
    ) : TrainerUiState {
        val allConfirmed: Boolean get() = values.allConfirmed
    }

    data object Error : TrainerUiState

    data object Offline : TrainerUiState
}

sealed interface TrainerEvent {
    data object Load : TrainerEvent

    data object Retry : TrainerEvent

    /** "Confirmar" on the card: accepts the values as they are. */
    data object Confirm : TrainerEvent

    data object OpenEdit : TrainerEvent

    data class PickLevel(val level: ActivityLevel) : TrainerEvent

    /** "Guardar" in the sheet: confirms with the picked level. */
    data object SaveEdit : TrainerEvent

    data object CloseEdit : TrainerEvent
}

/** One-off effects; the UI shows the message and fires the haptics. */
sealed interface TrainerEffect {
    data object Confirmed : TrainerEffect

    /** [fromSheet] distinguishes the save message from the confirm message; [offline] picks the offline hint. */
    data class ConfirmFailed(val fromSheet: Boolean, val offline: Boolean) : TrainerEffect
}
