package io.github.lopezjavier.appfitness.data

import io.github.lopezjavier.appfitness.domain.port.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** [Clock] backed by the system clock and the device's current time zone. */
class SystemClock : Clock {
    override fun today(): LocalDate = now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    override fun now(): Instant = kotlin.time.Clock.System.now()
}
