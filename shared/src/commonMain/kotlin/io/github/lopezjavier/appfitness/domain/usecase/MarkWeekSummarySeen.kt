package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.repository.CoachSummaryRepository

/** Marks a weekly summary as seen; the caller then refreshes the reminders so the notice clears. */
class MarkWeekSummarySeen(private val repository: CoachSummaryRepository) {
    suspend operator fun invoke(planId: String, week: Int): Result<Unit> = repository.markSeen(planId, week)
}
