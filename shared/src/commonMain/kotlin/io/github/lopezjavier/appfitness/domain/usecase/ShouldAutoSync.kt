package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.LocalDate

/**
 * Pure rule of the daily automatic sync: due when never synced or the last automatic sync day is strictly before
 * today's local day. Availability, permission and connectivity are checked by [AutoSyncHealth] in its own order.
 */
object ShouldAutoSync {
    fun isDue(today: LocalDate, lastAutoSyncDate: LocalDate?): Boolean =
        lastAutoSyncDate == null || lastAutoSyncDate < today
}
