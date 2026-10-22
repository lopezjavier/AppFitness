package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The value of one local day (planning/04-business-rules.md §3.1). */
data class DailyWeight(val date: LocalDate, val kg: Double)

/** Groups [BodyMeasurement]s by local day in a time zone; the day's value is the average. Sorted oldest first. */
class DailyWeights {
    operator fun invoke(rows: List<BodyMeasurement>, timeZone: TimeZone): List<DailyWeight> = rows
        .groupBy { it.measuredAt.toLocalDateTime(timeZone).date }
        .map { (date, dayRows) -> DailyWeight(date, dayRows.map { it.weightKg }.average()) }
        .sortedBy { it.date }
}
