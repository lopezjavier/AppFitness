package io.github.lopezjavier.appfitness.domain.port

import kotlin.time.Instant

/**
 * Schedules the end-of-rest alert for when the app is not in the foreground (vibration, sound and a notification).
 * There is at most one alert: a second [schedule] replaces the first. The [RestAlertKind] only picks the notification
 * copy (the cardio run shares the one timer slot and the one alert path).
 */
interface RestAlertScheduler {
    fun schedule(endsAt: Instant, kind: RestAlertKind = RestAlertKind.Rest)

    fun cancel()
}

/** What the scheduled alert announces. */
enum class RestAlertKind { Rest, Cardio }
