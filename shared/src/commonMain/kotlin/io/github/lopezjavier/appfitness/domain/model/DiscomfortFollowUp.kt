package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/** Why the one-time health note is shown. [Days14] wins when both triggers hold (decided by the server). */
enum class DiscomfortNoteReason { Days14, Sessions3 }

/**
 * Follow-up of an unresolved level 4-5 discomfort of one exercise (US-108). The server decides everything
 * (14-day clock, 3-log count, declined and dismissed state): [loggedAt] is the oldest unresolved level 4-5 row,
 * [discomfortId] the newest one. [noteReason] is null when [showNote] is false.
 */
data class DiscomfortFollowUp(
    val exerciseId: String,
    val discomfortId: String,
    val loggedAt: Instant,
    val offerSwap: Boolean,
    val showNote: Boolean,
    val noteReason: DiscomfortNoteReason? = null,
)

/** Replacement offered for a flagged exercise: same movement pattern, current equipment, favourites first. */
data class SwapCandidate(val exerciseId: String, val isFavorite: Boolean)
