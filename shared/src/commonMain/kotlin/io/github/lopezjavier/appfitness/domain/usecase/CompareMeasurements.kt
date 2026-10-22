package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement

/** Deltas are `latest - older`, 1 decimal; [bodyFatDeltaPct] (percentage points) needs fat on both sides. */
data class Comparison(val weightDeltaKg: Double, val bodyFatDeltaPct: Double?)

/** Compares an older measurement against the latest one. */
class CompareMeasurements {
    operator fun invoke(older: BodyMeasurement, latest: BodyMeasurement): Comparison {
        val olderFat = older.bodyFatPct
        val latestFat = latest.bodyFatPct
        return Comparison(
            weightDeltaKg = roundToTenths(latest.weightKg - older.weightKg),
            bodyFatDeltaPct = if (olderFat != null && latestFat != null) roundToTenths(latestFat - olderFat) else null,
        )
    }
}
