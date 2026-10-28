package io.github.lopezjavier.appfitness.platform

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.lopezjavier.appfitness.domain.model.RestSound
import io.github.lopezjavier.appfitness.domain.port.RestAlertPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RestAlertNotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val shadow = shadowOf(manager)
    private val notifier = RestAlertNotifier(context)
    private val sound = MutableStateFlow(RestSound.Default)
    private val preferences = object : RestAlertPreferences {
        override val sound: Flow<RestSound> = this@RestAlertNotifierTest.sound

        override suspend fun setSound(sound: RestSound) {
            this@RestAlertNotifierTest.sound.value = sound
        }

        override suspend fun explanationShown() = true

        override suspend fun markExplanationShown() = Unit
    }
    private var foreground = false
    private var now = 1_000_000L
    private val handler = RestAlertHandler(notifier, preferences, { foreground }, { now })

    private fun lastNotification(): Notification = shadow.allNotifications.single()

    @Test
    fun channels_oneHighImportanceChannelPerSound_withVibrationAndASound() {
        notifier.ensureChannels()

        RestSound.entries.forEach { candidate ->
            val channel = manager.getNotificationChannel(RestAlertNotifier.channelId(candidate))
            assertNotNull(channel, candidate.id)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
            assertTrue(channel.shouldVibrate())
            assertContentEquals(RestAlertPattern.timings, channel.vibrationPattern)
            assertNotNull(channel.sound, candidate.id)
        }
        assertEquals(3, RestSound.entries.map { RestAlertNotifier.channelId(it) }.toSet().size)
    }

    @Test
    fun handle_inTheBackground_postsDescansoTerminadoOnTheChannelOfTheCurrentSound() = runBlocking {
        sound.value = RestSound.CHIME

        val posted = handler.handle(endsAtEpochMs = now)

        assertTrue(posted)
        val notification = lastNotification()
        assertEquals(RestAlertNotifier.channelId(RestSound.CHIME), notification.channelId)
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
