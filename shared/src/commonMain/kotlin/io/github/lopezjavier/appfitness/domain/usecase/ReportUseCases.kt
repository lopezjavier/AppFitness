package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PastReport
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.repository.ReportRepository
import kotlinx.datetime.LocalDate

/** The stored reports, newest first (the order of the server is kept). */
class LoadReports(private val repository: ReportRepository) {
    suspend operator fun invoke(): Result<List<ReportItem>> = repository.list()
}

/** One stored report with its body; null when it no longer exists. */
class LoadPastReport(private val repository: ReportRepository) {
    suspend operator fun invoke(id: String): Result<PastReport?> = repository.get(id)
}

/** Stores the paused week the user is looking at; the caller ignores a failure. */
class RecordPausedWeekReport(private val repository: ReportRepository) {
    suspend operator fun invoke(today: LocalDate): Result<Unit> = repository.recordPausedWeek(today)
}
