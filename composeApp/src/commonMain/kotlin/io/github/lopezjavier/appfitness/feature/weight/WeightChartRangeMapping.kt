package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.designsystem.TimeRange
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange

/** The chart range as the shared range selector shows it ("1S 1M 3M 6M 1A"). */
internal fun WeightChartRange.toTimeRange(): TimeRange = when (this) {
    WeightChartRange.Week -> TimeRange.Week
    WeightChartRange.Month -> TimeRange.Month
    WeightChartRange.Quarter -> TimeRange.ThreeMonths
    WeightChartRange.HalfYear -> TimeRange.SixMonths
    WeightChartRange.Year -> TimeRange.Year
}

/** Inverse of [toTimeRange]; [TimeRange.All] is not offered by the weight chart and falls back to the widest range. */
internal fun TimeRange.toWeightChartRange(): WeightChartRange = when (this) {
    TimeRange.Week -> WeightChartRange.Week
    TimeRange.Month -> WeightChartRange.Month
    TimeRange.ThreeMonths -> WeightChartRange.Quarter
    TimeRange.SixMonths -> WeightChartRange.HalfYear
    TimeRange.Year, TimeRange.All -> WeightChartRange.Year
}
