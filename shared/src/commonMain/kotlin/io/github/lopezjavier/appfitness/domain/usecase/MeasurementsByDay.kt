package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.DayMeasurements
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val NEWEST_FIRST: Comparator<BodyMeasurement> =
    compareByDescending<BodyMeasurement> { it.measuredAt }.thenByDescending { it.id }

/**
 * Groups [rows] by local day in [timeZone]. Inside a day the rows are newest first; when legacy data holds several
 * manual rows the newest is `manual` and the older ones are left out of the day (the screen shows one per day).
 */
class MeasurementsByDay {
    operator fun invoke(rows: List<BodyMeasurement>, timeZone: TimeZone): Map<LocalDate, DayMeasurements> = rows
        .groupBy { it.measuredAt.toLocalDateTime(timeZone).date }
        .entries
        .sortedByDescending { it.key }
        .associate { (day, dayRows) ->
            val sorted = dayRows.sortedWith(NEWEST_FIRST)
            day to DayMeasurements(
                manual = sorted.firstOrNull { it.source == MeasurementSource.Manual },
                healthConnect = sorted.filter { it.source == MeasurementSource.HealthConnect },
            )
        }
}

/** The latest row by measurement time, any source; ties break by the larger id. Null when there are no rows. */
class CurrentWeight {
    operator fun invoke(rows: List<BodyMeasurement>): BodyMeasurement? = rows.minWithOrNull(NEWEST_FIRST)
}
