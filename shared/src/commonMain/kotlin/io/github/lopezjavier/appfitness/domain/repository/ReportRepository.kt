package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.PastReport
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import kotlinx.datetime.LocalDate

/** The stored weekly and block reports (US-129). */
interface ReportRepository {
    /** Every stored report, newest first (server order). Unknown kinds are left out. */
    suspend fun list(): Result<List<ReportItem>>

    /** One report with its body; null when the id is unknown, foreign or its body cannot be read. */
    suspend fun get(id: String): Result<PastReport?>

    /** Stores the paused week shown today; a no-op on the server when the week is not paused. */
    suspend fun recordPausedWeek(today: LocalDate): Result<Unit>
}
