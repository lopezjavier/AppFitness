package io.github.lopezjavier.appfitness.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RestAlertNotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val shadow = shadowOf(manager)
    private val notifier = RestAlertNotifier(context)
    private var foreground = false
    private var now = 1_000_000L
    private val handler = RestAlertHandler(notifier, { foreground }, { now })

    private fun lastNotification(): Notification = shadow.allNotifications.single()

    @Test
    fun channels_onlyTheChimeChannelExists_highImportanceWithVibrationAndASound() {
        notifier.createChannels()

        val channel = manager.getNotificationChannel(RestAlertNotifier.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals("rest_alert_chime", channel.id)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(channel.shouldVibrate())
        assertContentEquals(RestAlertPattern.timings, channel.vibrationPattern)
        assertNotNull(channel.sound)
        assertEquals(listOf("rest_alert_chime"), manager.notificationChannels.map { it.id })
    }

    @Test
    fun channels_theChannelsOfTheRemovedSoundsAreDeleted() {
        listOf("rest_alert_system", "rest_alert_double_beep").forEach {
            manager.createNotificationChannel(NotificationChannel(it, it, NotificationManager.IMPORTANCE_HIGH))
        }

        notifier.createChannels()

        assertNull(manager.getNotificationChannel("rest_alert_system"))
        assertNull(manager.getNotificationChannel("rest_alert_double_beep"))
    }

    @Test
    fun handle_inTheBackground_postsDescansoTerminadoOnTheChimeChannel() = runBlocking {
        val posted = handler.handle(endsAtEpochMs = now)

        assertTrue(posted)
        val notification = lastNotification()
        assertEquals("rest_alert_chime", notification.channelId)
        assertEquals("Descanso terminado", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
    }

    @Test
    fun handle_inTheForeground_postsNothing() = runBlocking {
        foreground = true

        assertFalse(handler.handle(endsAtEpochMs = now))

        assertTrue(shadow.allNotifications.isEmpty())
    }

    @Test
    fun handle_withNotificationsDisabled_postsNothing() = runBlocking {
        shadow.setNotificationsEnabled(false)

        assertFalse(handler.handle(endsAtEpochMs = now))

        assertTrue(shadow.allNotifications.isEmpty())
    }

    @Test
    fun handle_moreThanTenMinutesLate_isDropped() = runBlocking {
        assertFalse(handler.handle(endsAtEpochMs = now - RestAlertHandler.MAX_LATE_MILLIS - 1))

        assertTrue(shadow.allNotifications.isEmpty())
    }

    @Test
    fun handle_twice_replacesTheNotification() = runBlocking {
        handler.handle(endsAtEpochMs = now)
        handler.handle(endsAtEpochMs = now)

        assertEquals(1, shadow.allNotifications.size)
    }
}
