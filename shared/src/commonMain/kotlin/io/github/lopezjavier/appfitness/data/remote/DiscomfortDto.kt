package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Discomfort
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Row of `public.exercise_discomforts` (user_id defaults to `auth.uid()` on insert, so it is never sent). */
@Serializable
internal data class DiscomfortDto(
    val id: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("logged_at") val loggedAt: Instant,
    val note: String? = null,
    val severe: Boolean = false,
    @SerialName("resolved_at") val resolvedAt: Instant? = null,
) {
    fun toDomain() = Discomfort(id, exerciseId, loggedAt, note, severe, resolvedAt)

    companion object {
        const val COLUMNS = "id,exercise_id,logged_at,note,severe,resolved_at"
    }
}

/** Body of the insert: only what the client chooses. */
@Serializable
internal data class DiscomfortInsertDto(
    @SerialName("exercise_id") val exerciseId: String,
    val note: String? = null,
    val severe: Boolean = false,
)

/** Body of the `PATCH` that resolves. */
@Serializable
internal data class DiscomfortResolveDto(@SerialName("resolved_at") val resolvedAt: Instant)
