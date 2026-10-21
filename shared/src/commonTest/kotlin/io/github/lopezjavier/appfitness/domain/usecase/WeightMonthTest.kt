package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val MADRID = TimeZone.of("Europe/Madrid")

class WeightMonthTest {
    private fun row(id: String, at: String, kg: Double) =
        BodyMeasurement(id, Instant.parse(at), kg, MeasurementSource.Manual)

    @Test
    fun bounds_followTheLocalMonthNotUtc() {
        // October in Madrid starts 2026-09-30T22:00Z (UTC+2) and ends when November starts, 23:00Z (UTC+1).
        val (from, toExclusive) = WeightMonth.bounds(YearMonth(2026, 10), MADRID)

        assertEquals(Instant.parse("2026-09-30T22:00:00Z"), from)
        assertEquals(Instant.parse("2026-10-31T23:00:00Z"), toExclusive)
    }

    @Test
    fun bounds_crossingTheYear() {
        val (from, toExclusive) = WeightMonth.bounds(YearMonth(2026, 12), MADRID)

        assertEquals(Instant.parse("2026-11-30T23:00:00Z"), from)
        assertEquals(Instant.parse("2026-12-31T23:00:00Z"), toExclusive)
    }

    @Test
    fun prefill_isTheLatestRowAtOrBeforeTheDay() {
        val rows = listOf(
            row("a", "2026-10-02T08:00:00Z", 75.0),
            row("b", "2026-10-09T08:00:00Z", 74.2),
            row("c", "2026-10-15T08:00:00Z", 73.0),
        )

        assertEquals("b", WeightMonth.prefill(rows, LocalDate(2026, 10, 12), MADRID)?.id)
        assertEquals("b", WeightMonth.prefill(rows, LocalDate(2026, 10, 9), MADRID)?.id)
        assertEquals("c", WeightMonth.prefill(rows, LocalDate(2026, 10, 20), MADRID)?.id)
    }

    @Test
    fun prefill_isNullWhenNothingIsEarlier() {
        val rows = listOf(row("a", "2026-10-09T08:00:00Z", 74.2))

        assertNull(WeightMonth.prefill(rows, LocalDate(2026, 10, 3), MADRID))
    }

    @Test
    fun prefill_usesTheLocalDayOfARowNearMidnight() {
        // 23:30Z on Oct 9 is already Oct 10 in Madrid.
        val rows = listOf(row("a", "2026-10-09T23:30:00Z", 74.2))

        assertNull(WeightMonth.prefill(rows, LocalDate(2026, 10, 9), MADRID))
        assertEquals("a", WeightMonth.prefill(rows, LocalDate(2026, 10, 10), MADRID)?.id)
    }
}
