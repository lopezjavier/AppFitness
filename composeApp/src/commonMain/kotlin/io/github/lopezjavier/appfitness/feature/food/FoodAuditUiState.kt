package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.domain.model.FoodAuditRow

/** The food audit (US-117): the user's own foods whose kcal and macros do not match, worst first. */
sealed interface FoodAuditUiState {
    data object Loading : FoodAuditUiState

    /** [rows] are in the server's order (clearly wrong first, then the largest gap); the order is never changed. */
    data class Content(val rows: List<FoodAuditRow>) : FoodAuditUiState

    data object Empty : FoodAuditUiState

    data object Offline : FoodAuditUiState

    data object Error : FoodAuditUiState
}

sealed interface FoodAuditEvent {
    /** Reload with the skeleton (after [FoodAuditUiState.Error] or [FoodAuditUiState.Offline]). */
    data object Retry : FoodAuditEvent

    /** Reload silently after the editor changed a food: the list stays visible and a failure keeps the old rows. */
    data object Refresh : FoodAuditEvent

    data class OpenFood(val foodId: String) : FoodAuditEvent
}

/** One-off effects of the food audit. */
sealed interface FoodAuditEffect {
    data class OpenEditor(val foodId: String) : FoodAuditEffect
}
