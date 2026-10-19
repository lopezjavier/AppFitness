package io.github.lopezjavier.appfitness.feature.workout

/** One completed session in the history list. */
data class HistoryRow(
    val id: String,
    val label: DateLabel,
    val name: String,
    val durationSeconds: Long,
    val volumeKg: Double,
    val completedSets: Int,
)

sealed interface HistoryUiState {
    /** Loading the first page. */
    data object Loading : HistoryUiState

    /** The first page failed; [HistoryEvent.Retry] tries again. */
    data object Error : HistoryUiState

    /** No completed session yet. */
    data object Empty : HistoryUiState

    /**
     * Loaded rows, newest first. [loadingMore] shows the end-of-list spinner, [pageError] the end-of-list retry
     * ([HistoryEvent.RetryLoadMore]) and [endReached] means the last page was loaded.
     */
    data class Content(
        val rows: List<HistoryRow>,
        val loadingMore: Boolean = false,
        val pageError: Boolean = false,
        val endReached: Boolean = false,
    ) : HistoryUiState
}

sealed interface HistoryEvent {
    data object Retry : HistoryEvent

    /** The last row became visible. */
    data object LoadMore : HistoryEvent

    data object RetryLoadMore : HistoryEvent

    /** Reload from the first page without the loading state (the screen came back, e.g. after a deletion). */
    data object Refresh : HistoryEvent

    data class OpenSession(val id: String) : HistoryEvent
}

sealed interface HistoryEffect {
    data class OpenDetail(val sessionId: String) : HistoryEffect
}
