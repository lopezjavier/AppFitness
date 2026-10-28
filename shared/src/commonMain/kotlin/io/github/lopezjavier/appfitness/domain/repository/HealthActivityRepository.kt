package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.HealthActivityDay
import io.github.lopezjavier.appfitness.domain.model.HealthActivityHistory
import kotlinx.datetime.LocalDate

/** Read side of the watch data of the signed-in user. A failure carries an `AppException`. */
interface HealthActivityRepository {
    /** Stored data of the days in [from] until [toExclusive], ascending by day (sleep by the day it ended). */
    suspend fun history(from: LocalDate, toExclusive: LocalDate): Result<HealthActivityHistory>

    /** The five values of [day] alone, for days outside a loaded window. */
    suspend fun day(day: LocalDate): Result<HealthActivityDay>
}
