package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.WeekSummary

sealed interface WeekSummaryUiState {
    data object Loading : WeekSummaryUiState

    /**
     * The summary of the latest finished plan week. [exerciseNames] resolves the exercises of its coach changes
     * (R-54): a change whose name is unknown is not listed.
     */
    data class Content(val summary: WeekSummary, val exerciseNames: Map<String, String>) : WeekSummaryUiState

    /** No finished week with records any more (for example an old notice): "Aún no hay resumen semanal." */
    data object Empty : WeekSummaryUiState

    data object Error : WeekSummaryUiState

    data object Offline : WeekSummaryUiState
}

sealed interface WeekSummaryEvent {
    /** "Reintentar" after a failed load, or "Recargar" when offline. */
    data object Retry : WeekSummaryEvent
}
