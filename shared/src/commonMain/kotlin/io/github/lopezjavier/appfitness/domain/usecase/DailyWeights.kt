package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The value of one local day (planning/04-business-rules.md §3.1). */
data class DailyWeight(val date: LocalDate, val kg: Double)

/**
 * Groups [BodyMeasurement]s by local day in a time zone; the day's value is the average of its Health Connect rows
 * when it has any (they take precedence, ADR-47), else of its manual rows. Sorted oldest first.
 */
class DailyWeights {
    operator fun invoke(rows: List<BodyMeasurement>, timeZone: TimeZone): List<DailyWeight> = rows
        .groupBy { it.measuredAt.toLocalDateTime(timeZone).date }
        .map { (date, dayRows) ->
            val preferred = dayRows.filter { it.source == MeasurementSource.HealthConnect }.ifEmpty { dayRows }
            DailyWeight(date, preferred.map { it.weightKg }.average())
        }
        .sortedBy { it.date }
}
