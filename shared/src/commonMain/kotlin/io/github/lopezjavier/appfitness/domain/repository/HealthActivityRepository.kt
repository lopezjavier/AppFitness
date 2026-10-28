package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.HealthActivitySummary

/** Read side of the watch data of the signed-in user. A failure carries an `AppException`. */
interface HealthActivityRepository {
    /** The latest sleep, the last seven stored days (newest first) and the latest resting heart rate. */
    suspend fun latestSummary(): Result<HealthActivitySummary>
}
