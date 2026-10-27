package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Looper
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidRestAlertTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val requested = mutableListOf<Int>()
    private val played = mutableListOf<String>()
    private val stopped = mutableListOf<String>()

    private fun realRingtone(name: String) = AlertTone(play = { played += name }, stop = { stopped += name })

    private fun sound(alarm: Boolean = true, notification: Boolean = true) = AndroidAlertSound(context) { type ->
        requested += type
        when {
            type == RingtoneManager.TYPE_ALARM && alarm -> realRingtone("alarm")
            type == RingtoneManager.TYPE_NOTIFICATION && notification -> realRingtone("notification")
            else -> null
        }
    }

    @Test
    fun alert_vibratesOnTheDevice() {
        AndroidHaptics(context).alert()

        assertTrue(shadowOf(context.getSystemService(Vibrator::class.java)).isVibrating)
    }

    @Test
    fun sound_withNormalRinger_playsTheAlarmTone() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL

        sound().play()

        assertEquals(listOf(RingtoneManager.TYPE_ALARM), requested)
        assertEquals(listOf("alarm"), played)
    }

    @Test
    fun sound_isCutAfterAboutTwoSeconds() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL

        sound().play()
        assertTrue(stopped.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2100))

        assertEquals(listOf("alarm"), stopped)
    }

    @Test
    fun sound_withoutAlarmTone_fallsBackToTheNotificationTone() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL

        sound(alarm = false).play()

        assertEquals(listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION), requested)
        assertEquals(listOf("notification"), played)
    }

    @Test
    fun sound_withNoToneAtAll_doesNothing() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL

        sound(alarm = false, notification = false).play()

        assertTrue(played.isEmpty())
    }

    @Test
    fun sound_onVibrateRinger_isSilent() {
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE

        sound().play()

        assertTrue(requested.isEmpty())
    }

    @Test
    fun sound_onSilentRinger_isSilent() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT

        sound().play()

        assertFalse(requested.isNotEmpty())
    }
}
