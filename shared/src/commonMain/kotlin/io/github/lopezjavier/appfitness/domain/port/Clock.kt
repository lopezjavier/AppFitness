package io.github.lopezjavier.appfitness.domain.port

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Source of the current time, injectable so tests control it (planning/02-architecture.md §2). */
interface Clock {
    /** Today's date in the device time zone. */
    fun today(): LocalDate

    /** The current instant. */
    fun now(): Instant
}
