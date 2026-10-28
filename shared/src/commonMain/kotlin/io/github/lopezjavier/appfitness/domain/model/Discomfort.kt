package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/**
 * A discomfort the user flagged on an exercise (US-67). The user picks the [level]; [severe] is derived from it.
 * [resolvedAt] is set by "Ya no me molesta".
 */
data class Discomfort(
    val id: String,
    val exerciseId: String,
    val loggedAt: Instant,
    val level: DiscomfortLevel,
    val note: String?,
    val resolvedAt: Instant? = null,
) {
    val severe: Boolean get() = level.severe
}
