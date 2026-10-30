package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachUsage

sealed interface CoachUsageUiState {
    data object Loading : CoachUsageUiState

    /** The month's usage; zero spent is still Content (the screen shows 0,00 EUR of the limit). */
    data class Content(val usage: CoachUsage) : CoachUsageUiState

    data object Error : CoachUsageUiState

    data object Offline : CoachUsageUiState
}

sealed interface CoachUsageEvent {
    data object Retry : CoachUsageEvent

    /** Dev-only: one `ping` call to the `coach` function (ignored unless the dev flag is on or one is running). */
    data object Ping : CoachUsageEvent
}

/** State of the dev-only ping button; never persisted. */
sealed interface CoachPingUiState {
    data object Idle : CoachPingUiState

    data object Running : CoachPingUiState

    data object Success : CoachPingUiState

    data object Offline : CoachPingUiState

    /** The function refused or failed; [failure] says why (limit, no credit, timeout, other). */
    data class Failed(val failure: CoachFailure) : CoachPingUiState
}
