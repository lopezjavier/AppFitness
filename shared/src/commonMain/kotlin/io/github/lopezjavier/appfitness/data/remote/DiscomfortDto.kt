package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Row of `public.exercise_discomforts` (user_id defaults to `auth.uid()` on insert, so it is never sent). */
@Serializable
internal data class DiscomfortDto(
    val id: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("logged_at") val loggedAt: Instant,
    val level: Int,
    val note: String? = null,
    @SerialName("resolved_at") val resolvedAt: Instant? = null,
) {
    /** Throws [IllegalArgumentException] for a level outside 1..5: the repository maps it to a failed result. */
    fun toDomain() = Discomfort(
        id = id,
        exerciseId = exerciseId,
        loggedAt = loggedAt,
        level = requireNotNull(DiscomfortLevel.fromValue(level)) { "Unknown discomfort level $level" },
        note = note,
        resolvedAt = resolvedAt,
    )

    companion object {
        const val COLUMNS = "id,exercise_id,logged_at,level,note,resolved_at"
    }
}

/** Body of the insert: only what the client chooses. */
@Serializable
internal data class DiscomfortInsertDto(
    @SerialName("exercise_id") val exerciseId: String,
    val level: Int,
    val note: String? = null,
)

/** Body of the `PATCH` that resolves. */
@Serializable
internal data class DiscomfortResolveDto(@SerialName("resolved_at") val resolvedAt: Instant)
