package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val MADRID = TimeZone.of("Europe/Madrid")

private fun row(id: String, at: String, source: MeasurementSource = MeasurementSource.Manual, kg: Double = 70.0) =
    BodyMeasurement(id, Instant.parse(at), kg, source)

class MeasurementsByDayTest {
    private val byDay = MeasurementsByDay()

    @Test
    fun emptyList_givesEmptyMap() {
        assertEquals(emptyMap(), byDay(emptyList(), MADRID))
    }

    @Test
    fun manualAndHealthConnectRowsOfADay_areSplit() {
        val manual = row("m", "2026-10-14T08:00:00Z")
        val hcEarly = row("h1", "2026-10-14T06:00:00Z", MeasurementSource.HealthConnect)
        val hcLate = row("h2", "2026-10-14T10:00:00Z", MeasurementSource.HealthConnect)

        val day = byDay(listOf(hcEarly, manual, hcLate), MADRID).getValue(LocalDate(2026, 10, 14))

        assertEquals(manual, day.manual)
        assertEquals(listOf(hcLate, hcEarly), day.healthConnect)
    }

    @Test
    fun healthConnectOnlyDay_hasNoManual() {
        val hc = row("h", "2026-10-14T06:00:00Z", MeasurementSource.HealthConnect)

        val day = byDay(listOf(hc), MADRID).getValue(LocalDate(2026, 10, 14))

        assertNull(day.manual)
        assertEquals(listOf(hc), day.healthConnect)
    }

    @Test
    fun twoDays_areListedNewestFirst() {
        val rows = listOf(row("a", "2026-10-12T08:00:00Z"), row("b", "2026-10-14T08:00:00Z"))

        assertEquals(
            listOf(LocalDate(2026, 10, 14), LocalDate(2026, 10, 12)),
            byDay(rows, MADRID).keys.toList(),
        )
    }

    @Test
    fun localMidnightEdges_fallOnTheirLocalDay() {
        // Madrid is UTC+2 on 2026-10-14: 21:59:59Z is 23:59:59 local, 22:00Z is 00:00 the next day.
        val lastOfDay = row("late", "2026-10-14T21:59:59Z")
        val firstOfNext = row("early", "2026-10-14T22:00:00Z")

        val grouped = byDay(listOf(lastOfDay, firstOfNext), MADRID)

        assertEquals(lastOfDay, grouped.getValue(LocalDate(2026, 10, 14)).manual)
        assertEquals(firstOfNext, grouped.getValue(LocalDate(2026, 10, 15)).manual)
    }

    @Test
    fun severalManualRowsOfADay_theLatestIsManual() {
        val old = row("old", "2026-10-14T06:00:00Z")
        val latest = row("new", "2026-10-14T09:00:00Z")

        val day = byDay(listOf(old, latest), MADRID).getValue(LocalDate(2026, 10, 14))

        assertEquals(latest, day.manual)
    }
}

class CurrentWeightTest {
    private val current = CurrentWeight()

    @Test
    fun noRows_isNull() {
        assertNull(current(emptyList()))
    }

    @Test
    fun latestByMeasuredAt_winsAcrossSources() {
        val manual = row("m", "2026-10-14T08:00:00Z")
        val hc = row("h", "2026-10-15T08:00:00Z", MeasurementSource.HealthConnect)

        assertEquals(hc, current(listOf(hc, manual)))
    }

    @Test
    fun sameInstant_tiesBreakByLargerId() {
        val a = row("a", "2026-10-14T08:00:00Z")
        val b = row("b", "2026-10-14T08:00:00Z", MeasurementSource.HealthConnect)

        assertEquals(b, current(listOf(b, a)))
        assertEquals(b, current(listOf(a, b)))
    }
}
