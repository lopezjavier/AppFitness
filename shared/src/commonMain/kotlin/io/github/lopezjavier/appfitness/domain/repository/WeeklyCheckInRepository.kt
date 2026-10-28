package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.CheckIn
import kotlinx.datetime.LocalDate

/** The signed-in user's weekly check-ins, one per Monday-Sunday week. */
interface WeeklyCheckInRepository {
    /** The check-in of the week starting on [weekStart] (a Monday), or null when not answered. */
    suspend fun get(weekStart: LocalDate): Result<CheckIn?>

    /** Creates or replaces the check-in of its week. An invalid value fails with `AppError.Validation`. */
    suspend fun save(checkIn: CheckIn): Result<Unit>
}
