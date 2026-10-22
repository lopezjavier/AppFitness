package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val OLD = Instant.parse("2026-10-01T08:00:00Z")
private val MID = Instant.parse("2026-10-05T08:00:00Z")
private val NEW = Instant.parse("2026-10-09T08:00:00Z")

private fun row(
    at: Instant,
    source: MeasurementSource = MeasurementSource.HealthConnect,
    build: (BodyMeasurement) -> BodyMeasurement,
) = build(BodyMeasurement("id-$at", at, 74.0, source))

class LatestCompositionTest {
    @Test
    fun picksTheNewestNonNullValuePerField() {
        val rows = listOf(
            row(OLD) { it.copy(bodyFatPct = 20.0, leanMassKg = 60.0, bmrKcal = 1700) },
            row(NEW) { it.copy(bodyFatPct = 18.0) },
            row(MID) { it.copy(leanMassKg = 61.0, waterKg = 44.0, boneKg = 3.0) },
        )

        val latest = LatestComposition.from(rows)

        assertEquals(Dated(18.0, NEW), latest.bodyFat)
        assertEquals(Dated(61.0, MID), latest.leanMass)
        assertEquals(Dated(44.0, MID), latest.water)
        assertEquals(Dated(3.0, MID), latest.bone)
        assertEquals(Dated(1700, OLD), latest.bmr)
    }

    @Test
    fun noCompositionValues_giveAllNull() {
        val latest = LatestComposition.from(listOf(row(NEW) { it }))

        assertEquals(LatestComposition(), latest)
        assertNull(latest.bodyFat)
    }

    @Test
    fun emptyInput_givesAllNull() {
        assertEquals(LatestComposition(), LatestComposition.from(emptyList()))
    }

    @Test
    fun hasHealthConnectWeight_isTrueOnlyWithHealthConnectRows() {
        val hc = row(NEW) { it }
        assertTrue(DayMeasurements(null, listOf(hc)).hasHealthConnectWeight)
        assertFalse(DayMeasurements(row(NEW, MeasurementSource.Manual) { it }, emptyList()).hasHealthConnectWeight)
    }
}
