package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
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
        val from = watchWindowStart(now)
        val weight = if (HealthMetric.Weight in access.read) weightSync() else null
        val watch = if (access.watchRead.isNotEmpty()) importWatch(now, from) else null

        val failures = listOfNotNull(weight?.exceptionOrNull(), watch?.exceptionOrNull())
        val ran = listOfNotNull(weight, watch).size
        if (failures.size == ran) return Result.failure(failures.first())
        val imported = (weight?.getOrNull() as? SyncOutcome.Synced)?.imported ?: 0
        return Result.success(SyncOutcome.Synced(imported, now, watch?.getOrNull()))
    }

    private suspend fun watchWindowStart(now: Instant): Instant {
        val firstSync = repository.getState().getOrNull()?.lastAutoSyncDate == null
        return now - (if (firstSync) FIRST_WINDOW else LATER_WINDOW)
    }

    private suspend fun importWatch(now: Instant, from: Instant): Result<HealthActivityImportResult> {
        val batch = source.readWatchData(from, now, zone).getOrElse { return Result.failure(it) }
        return repository.importActivity(WatchDataSanitizer.sanitize(batch))
    }

    private companion object {
        val FIRST_WINDOW = 90.days
        val LATER_WINDOW = 7.days
    }
}
