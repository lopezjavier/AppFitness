package io.github.lopezjavier.appfitness.domain.port

import io.github.lopezjavier.appfitness.domain.model.WatchMetric

/**
 * The watch types whose first 90-day read already succeeded on this device. Local only: a reinstall or a data clear
 * empties it, which just makes the next sync read 90 days again (the server import is idempotent).
 */
interface WatchSeedStore {
    suspend fun seeded(): Set<WatchMetric>

    /** Adds [types] to the seeded set; call it only after their import succeeded. */
    suspend fun markSeeded(types: Set<WatchMetric>)
}
