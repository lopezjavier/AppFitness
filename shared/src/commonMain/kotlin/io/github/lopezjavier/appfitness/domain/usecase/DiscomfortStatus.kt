package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Whether a discomfort still shows as "Molestia registrada" (planning/04-business-rules.md §4.12). */
object DiscomfortStatus {
    const val OPEN_DAYS = 7

    /** Open = not resolved and logged less than [OPEN_DAYS] days before [now] (exactly 7 days = expired). */
    fun isOpen(discomfort: Discomfort, now: Instant): Boolean =
        discomfort.resolvedAt == null && now < discomfort.loggedAt + OPEN_DAYS.days

    /** The highest level among the open [discomforts] at [now], or null when none is open. */
    fun highestOpenLevel(discomforts: List<Discomfort>, now: Instant): DiscomfortLevel? =
        discomforts.filter { isOpen(it, now) }.maxOfOrNull { it.level }
}
