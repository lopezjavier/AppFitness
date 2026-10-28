package io.github.lopezjavier.appfitness.feature.notifications

import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Reminder
import kotlinx.datetime.LocalDate

/** State of the notification center (US-57). */
sealed interface NotificationCenterUiState {
    data object Loading : NotificationCenterUiState

    /** The reminders or the switches could not be read. */
    data object Error : NotificationCenterUiState

    /**
     * [reminders] are the pending ones (a silenced kind is already absent), [today] the device day used for their
     * dates and [isOffline] disables the switches (writes need a connection, R-30).
     */
    data class Content(
        val reminders: List<Reminder>,
        val weightEnabled: Boolean,
        val circumferenceEnabled: Boolean,
        val today: LocalDate,
        val isOffline: Boolean,
    ) : NotificationCenterUiState
}

/** Events of the notification center. */
sealed interface NotificationCenterEvent {
    /** The user flipped the switch of [kind]; it applies at once (R-42). */
    data class SetEnabled(val kind: NotificationKind, val enabled: Boolean) : NotificationCenterEvent

    /** Reads everything again after an error. */
    data object Retry : NotificationCenterEvent

    /** The screen came back to the foreground: recompute quietly. */
    data object Refresh : NotificationCenterEvent
}

/** One-shot effects of the notification center. */
sealed interface NotificationCenterEffect {
    /** A switch could not be saved and went back to its previous position. */
    data object ToggleFailed : NotificationCenterEffect
}
