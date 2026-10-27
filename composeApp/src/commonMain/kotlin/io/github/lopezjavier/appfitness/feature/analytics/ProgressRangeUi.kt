package io.github.lopezjavier.appfitness.feature.analytics

import io.github.lopezjavier.appfitness.designsystem.TimeRange
import io.github.lopezjavier.appfitness.domain.model.ProgressRange

/** The five ranges of the statistics and exercise progress selectors (R-33 maximum), in display order. */
val ProgressRanges: List<TimeRange> =
    listOf(TimeRange.Month, TimeRange.ThreeMonths, TimeRange.SixMonths, TimeRange.Year, TimeRange.All)

fun ProgressRange.toTimeRange(): TimeRange = when (this) {
    ProgressRange.OneMonth -> TimeRange.Month
    ProgressRange.ThreeMonths -> TimeRange.ThreeMonths
    ProgressRange.SixMonths -> TimeRange.SixMonths
    ProgressRange.OneYear -> TimeRange.Year
    ProgressRange.AllTime -> TimeRange.All
}

/** Inverse of [toTimeRange]; only the ranges in [ProgressRanges] exist as progress ranges. */
fun TimeRange.toProgressRange(): ProgressRange = when (this) {
    TimeRange.Month -> ProgressRange.OneMonth
    TimeRange.ThreeMonths -> ProgressRange.ThreeMonths
    TimeRange.SixMonths -> ProgressRange.SixMonths
    TimeRange.Year -> ProgressRange.OneYear
    TimeRange.All -> ProgressRange.AllTime
    TimeRange.Week -> throw IllegalArgumentException("$this is not a progress range")
}
