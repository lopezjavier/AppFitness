package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
import io.github.lopezjavier.appfitness.domain.model.SyncPart
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * One manual "Sincronizar": the weight sync ([SyncHealthMeasurements], unchanged) plus the watch data (sleep, steps,
 * calories, resting heart rate). Each part runs only when its permissions are granted and fails on its own; the call
 * fails only when every part that ran failed. The watch window is 90 days until the first automatic sync day is
 * stored (`lastAutoSyncDate == null`, written only by the automatic sync), then the last 7 days, re-read whole by
 * local day; the server replaces a touched day, so repeating a sync adds nothing.
 */
class SyncHealthData(
    private val source: HealthDataSource,
    private val repository: HealthSyncRepository,
    private val weightSync: SyncHealthMeasurements,
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
        val firstSync = repository.getState().getOrNull()?.lastAutoSyncDate == null
        val weight = if (HealthMetric.Weight in access.read) weightSync() else null
        val watch = if (access.watchRead.isNotEmpty()) importWatch(now, firstSync) else null

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

    private suspend fun importWatch(now: Instant, firstSync: Boolean): Result<HealthActivityImportResult> {
        val read = if (firstSync) {
            source.readWatchData(now - FIRST_WINDOW, now, zone).recoverCatching {
                source.readWatchData(now - FALLBACK_WINDOW, now, zone).getOrThrow()
            }
        } else {
            source.readWatchData(now - LATER_WINDOW, now, zone)
        }
        return read.fold({ repository.importActivity(WatchDataSanitizer.sanitize(it)) }, { Result.failure(it) })
    }

    private companion object {
        val FIRST_WINDOW = 90.days
        val FALLBACK_WINDOW = 30.days
        val LATER_WINDOW = 7.days
    }
}
