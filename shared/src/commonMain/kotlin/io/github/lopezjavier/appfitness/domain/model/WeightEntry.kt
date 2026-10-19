package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/**
 * A manual weight to store with the preferences (ADR-37): [weightKg] measured at [measuredAt], replacing the
 * latest manual row whose `measured_at` falls in the user's local day `[dayStart, dayEnd)`.
 */
data class WeightEntry(val weightKg: Double, val measuredAt: Instant, val dayStart: Instant, val dayEnd: Instant)
