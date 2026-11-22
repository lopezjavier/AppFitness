package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ReportGroup

sealed interface ReportsUiState {
    data object Loading : ReportsUiState

    /** One group per block, newest block first, the block review first in its group (see `groupReportsByBlock`). */
    data class Content(val groups: List<ReportGroup>) : ReportsUiState

    /** Nothing stored yet: "Aún no hay informes". */
    data object Empty : ReportsUiState

    data object Error : ReportsUiState

    data object Offline : ReportsUiState
}

sealed interface ReportsEvent {
    /** "Reintentar" after a failed load, or "Recargar" when offline. */
    data object Retry : ReportsEvent
}
