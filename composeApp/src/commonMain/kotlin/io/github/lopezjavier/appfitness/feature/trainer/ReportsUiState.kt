package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ReportItem

sealed interface ReportsUiState {
    data object Loading : ReportsUiState

    /** The stored reports exactly in the order the server returned them (newest first). */
    data class Content(val items: List<ReportItem>) : ReportsUiState

    /** Nothing stored yet: "Aún no hay informes". */
    data object Empty : ReportsUiState

    data object Error : ReportsUiState

    data object Offline : ReportsUiState
}

sealed interface ReportsEvent {
    /** "Reintentar" after a failed load, or "Recargar" when offline. */
    data object Retry : ReportsEvent
}
