package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.usecase.TrendData
import io.github.lopezjavier.appfitness.domain.usecase.TrendPoint
import io.github.lopezjavier.appfitness.domain.usecase.WeeklyRate
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlinx.datetime.LocalDate

/** The weight trend (US-27): chart points, weekly rate and the selected range. */
sealed interface WeightTrendUiState {
    data object Loading : WeightTrendUiState

    /** The load failed; [WeightTrendEvent.Retry] tries again. */
    data object Error : WeightTrendUiState

    /**
     * [selectedPoint] is the point whose detail is open; null when none is selected or the selected day is not in
     * the current range. [trend] has [TrendData.hasEnoughData] false for the "not enough data" state. [targetKg] is
     * the goal weight for the dashed target line (null when none is set or it could not be read); [startKg] is the
     * onboarding weight for the goal overlay. [trend] is the series of [metric]; [weeklyRate] is always the weight's,
     * and [targetKg] and [startKg] are null unless [metric] is Weight.
     */
    data class Content(
        val range: WeightChartRange,
        val metric: BodyMetric,
        val trend: TrendData,
        val weeklyRate: WeeklyRate,
        val selectedPoint: TrendPoint?,
        val targetKg: Double? = null,
        val startKg: Double? = null,
    ) : WeightTrendUiState {
        /**
         * Whether the chart is drawn instead of the "not enough data" hint. Weight needs an MA7 somewhere; the other
         * metrics are often logged sparsely (waist, hip), so their daily dots show whenever there is any value and
         * the average line only where it can be computed.
         */
        val hasPlot: Boolean
            get() = if (metric == BodyMetric.Weight) trend.hasEnoughData else trend.points.isNotEmpty()
    }
}

sealed interface WeightTrendEvent {
    /** Picks a filter; it is saved on the device and the trend reloads for its window. */
    data class SelectRange(val range: WeightChartRange) : WeightTrendEvent

    /** Picks the charted metric; it is saved on the device and closes the open point. */
    data class SelectMetric(val metric: BodyMetric) : WeightTrendEvent

    /** Opens the detail of the point on [date], or closes it with null. */
    data class SelectPoint(val date: LocalDate?) : WeightTrendEvent

    /** Reloads without the loading state (after a weight was saved or deleted). */
    data object Refresh : WeightTrendEvent

    /** Reloads after a failed load. */
    data object Retry : WeightTrendEvent
}
