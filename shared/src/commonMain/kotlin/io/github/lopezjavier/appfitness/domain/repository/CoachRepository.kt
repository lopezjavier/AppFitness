package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import kotlinx.datetime.LocalDate

interface CoachRepository {
    /** Derived values as of [today] (must be within one day of UTC today, the server rejects others). */
    suspend fun derivedValues(today: LocalDate): Result<CoachDerivedValues>

    /** Confirms every derived value, optionally storing an edited [activityLevel]; returns the recomputed values. */
    suspend fun confirm(today: LocalDate, activityLevel: ActivityLevel?): Result<CoachDerivedValues>
}
