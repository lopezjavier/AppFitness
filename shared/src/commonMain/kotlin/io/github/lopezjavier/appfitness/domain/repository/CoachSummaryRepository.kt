package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import kotlinx.datetime.LocalDate

/** The weekly summary of the accepted plan (`coach_week_summary`, `mark_coach_week_summary_seen`). */
interface CoachSummaryRepository {
    /** The latest finished plan week's summary as of [today]; null when there is none (no fallback to older weeks). */
    suspend fun getLatest(today: LocalDate): Result<WeekSummary?>

    /** Marks the summary of [week] of [planId] as seen; idempotent and shared across devices. */
    suspend fun markSeen(planId: String, week: Int): Result<Unit>
}
