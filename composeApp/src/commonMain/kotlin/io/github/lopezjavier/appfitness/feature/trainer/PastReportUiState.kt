package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ReportItem

sealed interface PastReportUiState {
    data object Loading : PastReportUiState

    /** The report is unknown, foreign or unreadable: "Ya no está disponible". */
    data object NotFound : PastReportUiState

    data object Error : PastReportUiState

    data object Offline : PastReportUiState

    /** A stored weekly summary, built by the same function as the live screen; [item] carries the period. */
    data class Week(val item: ReportItem, val content: WeekSummaryUiState.Content) : PastReportUiState

    /**
     * A stored block review shown read-only: [content] never has a calorie proposal or a next plan id, and its
     * `review.phaseDecision` is the stored decision (null when none was taken).
     */
    data class Block(val item: ReportItem, val content: BlockReviewUiState.Content) : PastReportUiState
}

sealed interface PastReportEvent {
    /** "Reintentar" after a failed load, or "Recargar" when offline. */
    data object Retry : PastReportEvent
}
