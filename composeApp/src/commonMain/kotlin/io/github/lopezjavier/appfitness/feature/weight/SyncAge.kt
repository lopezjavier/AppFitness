package io.github.lopezjavier.appfitness.feature.weight

import kotlin.time.Instant

/** How long ago the last Health Connect sync ran, in the unit the banner shows (US-26 "Last sync status"). */
sealed interface SyncAge {
    /** Less than a minute. */
    data object Now : SyncAge

    data class Minutes(val value: Int) : SyncAge

    data class Hours(val value: Int) : SyncAge

    data class Days(val value: Int) : SyncAge

    companion object {
        private const val MINUTES_PER_HOUR = 60
        private const val HOURS_PER_DAY = 24

        /** Under a minute is "ahora", then minutes below an hour, hours below a day, then days. */
        fun of(lastSyncedAt: Instant, now: Instant): SyncAge {
            val minutes = (now - lastSyncedAt).inWholeMinutes.coerceAtLeast(0).toInt()
            val hours = minutes / MINUTES_PER_HOUR
            return when {
                minutes < 1 -> Now
                minutes < MINUTES_PER_HOUR -> Minutes(minutes)
                hours < HOURS_PER_DAY -> Hours(hours)
                else -> Days(hours / HOURS_PER_DAY)
            }
        }
    }
}
