package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import kotlinx.datetime.LocalDate

interface CoachUsageRepository {
    /** Usage of the UTC month of [today]; no row yet means an empty usage. */
    suspend fun currentMonth(today: LocalDate): Result<CoachUsage>
}
