package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CompareMeasurementsTest {
    private val compare = CompareMeasurements()

    private fun row(weight: Double, fat: Double? = null) = BodyMeasurement(
        id = "m",
        measuredAt = Instant.parse("2026-10-20T08:00:00Z"),
        weightKg = weight,
        source = MeasurementSource.Manual,
        bodyFatPct = fat,
    )

    @Test
    fun weightDelta_isLatestMinusOlder() {
        assertEquals(-1.4, compare(row(75.6), row(74.2)).weightDeltaKg)
    }

    @Test
    fun bodyFatDelta_whenBothHaveFat() {
        assertEquals(-0.8, compare(row(75.0, 20.5), row(74.0, 19.7)).bodyFatDeltaPct)
    }

    @Test
    fun bodyFatDelta_isNullWhenEitherSideHasNone() {
        assertNull(compare(row(75.0), row(74.0, 19.7)).bodyFatDeltaPct)
        assertNull(compare(row(75.0, 20.5), row(74.0)).bodyFatDeltaPct)
    }
}
