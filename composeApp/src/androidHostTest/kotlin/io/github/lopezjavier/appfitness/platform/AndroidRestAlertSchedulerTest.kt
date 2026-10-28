package io.github.lopezjavier.appfitness.platform

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class AndroidRestAlertSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shadow = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val scheduler = AndroidRestAlertScheduler(context)

    @Test
    fun schedule_setsOneExactAlarmAtTheEndOfTheRest() {
        scheduler.schedule(Instant.fromEpochMilliseconds(1_000_000L))

        val alarm = shadow.peekNextScheduledAlarm()
        assertEquals(1_000_000L, alarm!!.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(1, shadow.scheduledAlarms.size)
    }

    @Test
    fun schedule_again_replacesThePreviousAlarm() {
        scheduler.schedule(Instant.fromEpochMilliseconds(1_000_000L))
        scheduler.schedule(Instant.fromEpochMilliseconds(1_030_000L))

        assertEquals(1, shadow.scheduledAlarms.size)
        assertEquals(1_030_000L, shadow.peekNextScheduledAlarm()!!.triggerAtMs)
    }

    @Test
    fun cancel_removesTheAlarm() {
        scheduler.schedule(Instant.fromEpochMilliseconds(1_000_000L))

        scheduler.cancel()

        assertNull(shadow.peekNextScheduledAlarm())
    }

    @Test
    fun cancel_withoutAnAlarm_doesNothing() {
        scheduler.cancel()

        assertNull(shadow.peekNextScheduledAlarm())
    }
}
