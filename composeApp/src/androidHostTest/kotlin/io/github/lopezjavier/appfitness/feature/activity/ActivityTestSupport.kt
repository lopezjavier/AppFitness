package io.github.lopezjavier.appfitness.feature.activity

import io.github.lopezjavier.appfitness.domain.model.HealthActivityDay
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.usecase.ActivityMetric
import io.github.lopezjavier.appfitness.domain.usecase.ActivitySeriesPoint
import io.github.lopezjavier.appfitness.domain.usecase.SleepStageSummary
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal val ActivityToday = LocalDate(2026, 10, 22)

private val StepPoints = (16..22).map { ActivitySeriesPoint(LocalDate(2026, 10, it), 6000.0 + it * 100) }

/** A loaded "Actividad" state for screen tests; every argument is one knob of the Gherkin scenarios. */
@Suppress("LongParameterList") // One knob per scenario.
internal fun activityContent(
    day: LocalDate = ActivityToday,
    sleep: Int? = 435,
    steps: Int? = 8500,
    activeKcal: Double? = 620.0,
    totalKcal: Double? = 2300.0,
    restingHeartRate: Int? = 52,
    metric: ActivityMetric = ActivityMetric.Default,
    range: WeightChartRange = WeightChartRange.Month,
    points: List<ActivitySeriesPoint> = StepPoints,
    sleepStages: SleepStageSummary? = null,
    markedDays: Set<LocalDate> = setOf(ActivityToday),
    dayLoading: Boolean = false,
    dayError: Boolean = false,
) = ActivityUiState.Content(
    day = day,
    isToday = day == ActivityToday,
    summary = HealthActivityDay(
        day,
        steps,
        activeKcal,
        totalKcal,
        sleep?.let {
            SleepSession(
                "s",
                Instant.parse("2026-10-22T06:00:00Z"),
                Instant.parse("2026-10-22T07:00:00Z"),
                it,
                emptyList(),
            )
        },
        restingHeartRate,
    ),
    chart = ActivityChartState(metric, range, points),
    sleepStages = sleepStages,
    markedDays = markedDays,
    today = ActivityToday,
    dayLoading = dayLoading,
    dayError = dayError,
)
