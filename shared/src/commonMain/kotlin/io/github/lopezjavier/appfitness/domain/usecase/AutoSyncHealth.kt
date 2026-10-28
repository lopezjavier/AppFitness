package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Outcome of one automatic-sync attempt on app start. */
sealed interface AutoSyncResult {
    /** Not due (already synced today): nothing was read. */
    data object Skipped : AutoSyncResult

    /** Due but it could not complete (offline or failed): the next open retries. */
    data object NotPossible : AutoSyncResult

    /** Due but no read permission: shown passively, the permission dialog is never launched. */
    data object PermissionNeeded : AutoSyncResult

    data object Unavailable : AutoSyncResult

    data class Done(val outcome: SyncOutcome.Synced) : AutoSyncResult
}

/**
 * The daily automatic sync (ADR-74): on the first open of a local day runs [SyncHealthData] and, only when every part
 * succeeded, stores today as the last automatic sync day. A failure or partial failure leaves the day untouched so the
 * next open retries.
 */
class AutoSyncHealth(
    private val source: HealthDataSource,
    private val repository: HealthSyncRepository,
    private val syncHealthData: SyncHealthData,
    private val connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    @Suppress("ReturnCount") // Guard clauses in the order skipped, unavailable, permission, offline.
    suspend operator fun invoke(): AutoSyncResult {
        val today = clock.now().toLocalDateTime(zone).date
        val state = repository.getState().getOrElse { return AutoSyncResult.NotPossible }
        if (!ShouldAutoSync.isDue(today, state?.lastAutoSyncDate)) return AutoSyncResult.Skipped
        if (source.availability() != HealthAvailability.Available) return AutoSyncResult.Unavailable
        if (!source.access().hasAnyRead()) return AutoSyncResult.PermissionNeeded
        if (!connectivity.isOnline.first()) return AutoSyncResult.NotPossible

        return when (val outcome = syncHealthData().getOrElse { return AutoSyncResult.NotPossible }) {
            is SyncOutcome.Synced -> {
                if (outcome.failedPart == null) repository.markAutoSynced(today)
                AutoSyncResult.Done(outcome)
            }

            SyncOutcome.PermissionNeeded -> AutoSyncResult.PermissionNeeded

            is SyncOutcome.Unavailable -> AutoSyncResult.Unavailable
        }
    }
}
