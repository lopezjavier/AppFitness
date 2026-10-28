package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/**
 * A discomfort the user flagged on an exercise (US-67). [severe] is derived from the note when it is logged and
 * stored; [resolvedAt] is set by "Ya no me molesta".
 */
data class Discomfort(
    val id: String,
    val exerciseId: String,
    val loggedAt: Instant,
    val note: String?,
    val severe: Boolean,
    val resolvedAt: Instant? = null,
)
