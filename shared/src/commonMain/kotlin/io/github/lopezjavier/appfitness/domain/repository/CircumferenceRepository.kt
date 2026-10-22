package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlin.time.Instant

/** The signed-in user's body circumferences. */
interface CircumferenceRepository {
    /** Every site, newest `measured_at` first. */
    suspend fun getAll(): Result<List<BodyCircumference>>

    suspend fun add(site: CircumferenceSite, valueCm: Double, measuredAt: Instant): Result<Unit>

    /** The site of a row never changes. */
    suspend fun update(id: String, valueCm: Double, measuredAt: Instant): Result<Unit>

    suspend fun delete(id: String): Result<Unit>
}
