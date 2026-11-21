package io.github.lopezjavier.appfitness.feature.notifications

import io.github.lopezjavier.appfitness.domain.model.Reminder
import kotlinx.datetime.LocalDate

/** State of the notification center (US-57). */
sealed interface NotificationCenterUiState {
    data object Loading : NotificationCenterUiState

    /** The reminders or the switches could not be read. */
    data object Error : NotificationCenterUiState

    /**
     * [reminders] are the pending ones (a silenced kind is already absent), [today] the device day used for their
     * dates.
     */
    data class Content(val reminders: List<Reminder>, val today: LocalDate) : NotificationCenterUiState
}

/** Events of the notification center. */
sealed interface NotificationCenterEvent {
    /** Reads everything again after an error. */
    data object Retry : NotificationCenterEvent

    /** The screen came back to the foreground: recompute quietly. */
    data object Refresh : NotificationCenterEvent
}
