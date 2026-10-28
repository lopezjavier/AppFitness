package io.github.lopezjavier.appfitness.domain.port

import kotlin.time.Instant

/**
 * Schedules the end-of-rest alert for when the app is not in the foreground (vibration, sound and a notification).
 * There is at most one alert: a second [schedule] replaces the first.
 */
interface RestAlertScheduler {
    fun schedule(endsAt: Instant)

    fun cancel()
}
