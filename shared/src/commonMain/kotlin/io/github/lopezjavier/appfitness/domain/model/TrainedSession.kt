package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/** Light summary of a completed workout (no sets): enough to mark and open it from the training calendar. */
data class TrainedSession(val id: String, val name: String, val startedAt: Instant)
