package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.LocalDate

/**
 * Pure rule of the daily automatic sync: due when never synced or the last automatic sync day is strictly before
 * today's local day, and the sync can actually run (Health Connect available, online, some read granted).
 */
object ShouldAutoSync {
    operator fun invoke(
        today: LocalDate,
        lastAutoSyncDate: LocalDate?,
        available: Boolean,
        online: Boolean,
        canRead: Boolean,
    ): Boolean = available && online && canRead && isDue(today, lastAutoSyncDate)

    fun isDue(today: LocalDate, lastAutoSyncDate: LocalDate?): Boolean =
        lastAutoSyncDate == null || lastAutoSyncDate < today
}
