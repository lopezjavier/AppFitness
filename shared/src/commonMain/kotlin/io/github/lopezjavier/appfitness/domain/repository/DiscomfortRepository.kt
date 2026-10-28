package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import kotlin.time.Instant

/** The signed-in user's discomforts per exercise (US-67). Rows are never deleted: they stay as history. */
interface DiscomfortRepository {
    /** Stores a new discomfort of [exerciseId] and returns the stored row. */
    suspend fun log(exerciseId: String, level: DiscomfortLevel, note: String?): Result<Discomfort>

    /** Sets `resolved_at = at` on every unresolved discomfort of [exerciseId]. */
    suspend fun resolveOpen(exerciseId: String, at: Instant): Result<Unit>

    /** The unresolved discomforts of [exerciseIds], newest first. Expiry is applied by `DiscomfortStatus`. */
    suspend fun unresolved(exerciseIds: Set<String>): Result<List<Discomfort>>
}
