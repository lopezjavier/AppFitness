package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Pure helpers of the weight screen: the instants of a local month and the weight prefilled for a day. */
object WeightMonth {
    /** Start (inclusive) and end (exclusive) instants of [month] in [timeZone], for `getMeasurements`. */
    fun bounds(month: YearMonth, timeZone: TimeZone): Pair<Instant, Instant> {
        val first = month.firstDay
        return first.atStartOfDayIn(timeZone) to first.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(timeZone)
    }

    /** The latest of [rows] whose local day is [day] or earlier, or null when none is; any source. */
    fun prefill(rows: List<BodyMeasurement>, day: LocalDate, timeZone: TimeZone): BodyMeasurement? = rows
        .filter { it.measuredAt.toLocalDateTime(timeZone).date <= day }
        .maxWithOrNull(compareBy<BodyMeasurement> { it.measuredAt }.thenBy { it.id })
}
