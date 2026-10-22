package io.github.lopezjavier.appfitness.feature.weight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SyncAgeTest {
    private val now = Instant.parse("2026-10-22T10:00:00Z")

    private fun ageAfter(elapsed: Duration) = SyncAge.of(now - elapsed, now)

    @Test
    fun underAMinute_isNow() = assertEquals(SyncAge.Now, ageAfter(59.seconds))

    @Test
    fun twelveMinutes_isMinutes() = assertEquals(SyncAge.Minutes(12), ageAfter(12.minutes))

    @Test
    fun fiftyNineMinutes_isStillMinutes() = assertEquals(SyncAge.Minutes(59), ageAfter(59.minutes))

    @Test
    fun sixtyMinutes_isOneHour() = assertEquals(SyncAge.Hours(1), ageAfter(60.minutes))

    @Test
    fun twentyThreeHours_isHours() = assertEquals(SyncAge.Hours(23), ageAfter(23.hours + 59.minutes))

    @Test
    fun twentyFourHours_isOneDay() = assertEquals(SyncAge.Days(1), ageAfter(24.hours))

    @Test
    fun threeDays_isDays() = assertEquals(SyncAge.Days(3), ageAfter(3.days + 5.hours))

    @Test
    fun aFutureSync_isNow() = assertEquals(SyncAge.Now, ageAfter((-5).minutes))
}
