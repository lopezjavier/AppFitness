package io.github.lopezjavier.appfitness.domain.port

import io.github.lopezjavier.appfitness.domain.model.HistoryProbeResult

/** Temporary diagnostic (T-139): how far back Health Connect lets the app read. Nothing here throws. */
interface HealthHistoryProbe {
    /**
     * Reads steps and weight in the windows [now-90d, now-60d] and [now-30d, now] without filtering by origin.
     * A window whose read fails counts as empty; the result fails only when the client itself fails or all reads fail.
     */
    suspend fun probe(): Result<HistoryProbeResult>
}
