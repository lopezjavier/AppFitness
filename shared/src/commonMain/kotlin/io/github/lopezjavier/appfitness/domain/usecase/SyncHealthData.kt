package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
import io.github.lopezjavier.appfitness.domain.model.SyncPart
import io.github.lopezjavier.appfitness.domain.model.WatchMetric
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.port.WatchSeedStore
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * One manual "Sincronizar": the weight sync ([SyncHealthMeasurements], unchanged) plus the watch data (sleep, steps,
 * calories, resting heart rate). Each part runs only when its permissions are granted and fails on its own; the call
 * fails only when every part that ran failed. The watch window is per type: 90 days until that type has been
 * imported once on this device ([WatchSeedStore], so a type granted later still gets its 90 days), then the last
 * 7 days, re-read whole by local day; the server replaces a touched day, so repeating a sync adds nothing.
 */
class SyncHealthData(
    private val source: HealthDataSource,
    private val repository: HealthSyncRepository,
    private val weightSync: SyncHealthMeasurements,
    private val seedStore: WatchSeedStore,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    @Suppress("ReturnCount") // Guard clauses: unavailable, no permission, then the two parts.
    suspend operator fun invoke(): Result<SyncOutcome> {
        val availability = source.availability()
        if (availability != HealthAvailability.Available) {
            return Result.success(SyncOutcome.Unavailable(availability))
        }
        val access = source.access()
        if (!access.hasAnyRead()) return Result.success(SyncOutcome.PermissionNeeded)

        val now = clock.now()
        val weight = if (HealthMetric.Weight in access.read) weightSync() else null
        val watch = if (access.watchRead.isNotEmpty()) importWatch(now, access.watchRead) else null

        val failures = listOfNotNull(weight?.exceptionOrNull(), watch?.exceptionOrNull())
        val ran = listOfNotNull(weight, watch).size
        if (failures.size == ran) return Result.failure(failures.first())
        val imported = (weight?.getOrNull() as? SyncOutcome.Synced)?.imported ?: 0
        val failedPart = when {
            ran < 2 -> null
            weight?.isFailure == true -> SyncPart.Weight
            watch?.isFailure == true -> SyncPart.Watch
            else -> null
        }
        return Result.success(SyncOutcome.Synced(imported, now, watch?.getOrNull(), failedPart))
    }

    private suspend fun importWatch(now: Instant, granted: Set<WatchMetric>): Result<HealthActivityImportResult> {
        val seeded = runCatching { seedStore.seeded() }.getOrDefault(emptySet())
        val unseeded = granted - seeded
        var fellBack = false
        val read = source.readWatchData(
            plan(granted, unseeded, now, FIRST_WINDOW),
            now,
            zone,
        ).recoverCatching { error ->
            // Seeded types already read only 7 days: a failure there has no shorter window to retry with.
            if (unseeded.isEmpty()) throw error
            fellBack = true
            source.readWatchData(plan(granted, unseeded, now, FALLBACK_WINDOW), now, zone).getOrThrow()
        }
        val imported = read.fold({ repository.importActivity(WatchDataSanitizer.sanitize(it)) }, { Result.failure(it) })
        // Seed only after a full-window import: a 30-day fallback leaves the type to try 90 days again next time.
        if (imported.isSuccess && !fellBack && unseeded.isNotEmpty()) {
            runCatching { seedStore.markSeeded(unseeded) }
        }
        return imported
    }

    /** Types never read in full start [firstWindow] back; the seeded ones only the last [LATER_WINDOW]. */
    private fun plan(
        granted: Set<WatchMetric>,
        unseeded: Set<WatchMetric>,
        now: Instant,
        firstWindow: Duration,
    ): Map<WatchMetric, Instant> = granted.associateWith {
        if (it in unseeded) {
            now - firstWindow
        } else {
            now - LATER_WINDOW
        }
    }

    private companion object {
        val FIRST_WINDOW = 90.days
        val FALLBACK_WINDOW = 30.days
        val LATER_WINDOW = 7.days
    }
}
