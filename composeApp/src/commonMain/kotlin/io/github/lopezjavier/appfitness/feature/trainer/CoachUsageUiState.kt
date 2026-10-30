package io.github.lopezjavier.appfitness.feature.trainer

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
}
