package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.usecase.TrendData
import io.github.lopezjavier.appfitness.domain.usecase.TrendPoint
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlinx.datetime.LocalDate

/** The weight trend (US-27): chart points, weekly rate and the selected range. */
sealed interface WeightTrendUiState {
    data object Loading : WeightTrendUiState

    /** The load failed; [WeightTrendEvent.Retry] tries again. */
    data object Error : WeightTrendUiState

    /**
     * [selectedPoint] is the point whose detail is open; null when none is selected or the selected day is not in
     * the current range. [trend] has [TrendData.hasEnoughData] false for the "not enough data" state.
     */
    data class Content(val range: WeightChartRange, val trend: TrendData, val selectedPoint: TrendPoint?) :
        WeightTrendUiState
}

sealed interface WeightTrendEvent {
    /** Picks a filter; it is saved on the device and the trend reloads for its window. */
    data class SelectRange(val range: WeightChartRange) : WeightTrendEvent

    /** Opens the detail of the point on [date], or closes it with null. */
    data class SelectPoint(val date: LocalDate?) : WeightTrendEvent

    /** Reloads without the loading state (after a weight was saved or deleted). */
    data object Refresh : WeightTrendEvent

    /** Reloads after a failed load. */
    data object Retry : WeightTrendEvent
}
