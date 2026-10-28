package io.github.lopezjavier.appfitness.feature.checkin

import io.github.lopezjavier.appfitness.domain.model.CHECK_IN_NOTES_MAX
import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.WeeklySummary

/** Energy range of the check-in (1 = very low, 5 = very high). */
val CHECK_IN_ENERGY_RANGE: IntRange = 1..5

/** The week figures above the form; independent of the form, so a failure never blocks answering. */
sealed interface CheckInSummaryState {
    data object Loading : CheckInSummaryState

    /** The figures could not be read; "Reintentar" sends [CheckInEvent.RetrySummary]. */
    data object Failed : CheckInSummaryState

    data class Loaded(val summary: WeeklySummary) : CheckInSummaryState
}

/** State of the weekly check-in screen (US-59). */
sealed interface CheckInUiState {
    data object Loading : CheckInUiState

    /** The open check-in could not be determined (nothing is due, or the read failed); [CheckInEvent.Retry] retries. */
    data object LoadFailed : CheckInUiState

    /**
     * [window] is the check-in being answered ([CheckInDay.day] its day, [CheckInDay.weekStart] the Monday of the
     * summary week) and holds the saved answer when it is reopened. [energy] and [notes] are the draft (the saved
     * answer until edited). The week closes when the next check-in day arrives; saving updates the same week.
     */
    data class Ready(
        val window: CheckInDay,
        val energy: Int?,
        val notes: String,
        val summary: CheckInSummaryState,
        val isOffline: Boolean = false,
        val isSaving: Boolean = false,
        val exerciseNames: Map<String, String> = emptyMap(),
    ) : CheckInUiState {
        /** The check-in already has an answer: the form is prefilled and saving edits it. */
        val isEditing: Boolean get() = window.answer != null

        /** The draft differs from what is stored (a new check-in with an energy chosen counts as a change). */
        val isDirty: Boolean
            get() {
                val answer = window.answer
                return if (answer == null) energy != null else energy != answer.energy || notes != answer.notes
            }

        /** Over the 1000 characters the database accepts: shows "Máximo 1000 caracteres" and blocks saving. */
        val notesTooLong: Boolean get() = notes.length > CHECK_IN_NOTES_MAX

        /** "Guardar" needs an energy, valid notes, a change, the network and no save running (R-30). */
        val canSave: Boolean get() = energy != null && !notesTooLong && isDirty && !isOffline && !isSaving
    }
}

/** Events of the check-in screen. */
sealed interface CheckInEvent {
    /** One of [CHECK_IN_ENERGY_RANGE]; anything else is ignored. */
    data class SelectEnergy(val energy: Int) : CheckInEvent

    data class SetNotes(val notes: String) : CheckInEvent

    data object Save : CheckInEvent

    /** Reads the window again after [CheckInUiState.LoadFailed]. */
    data object Retry : CheckInEvent

    /** Reloads only the summary ("Reintentar" inside its block). */
    data object RetrySummary : CheckInEvent
}

/** One-off results of the check-in screen. */
sealed interface CheckInEffect {
    /** Saved (and the center refreshed): the UI performs the Confirm haptic and closes. */
    data object Saved : CheckInEffect

    /** The save failed and the draft stays; [offline] is true when the cause was the network. */
    data class SaveFailed(val offline: Boolean) : CheckInEffect
}
