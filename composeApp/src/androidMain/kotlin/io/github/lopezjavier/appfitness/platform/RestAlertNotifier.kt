package io.github.lopezjavier.appfitness.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import io.github.lopezjavier.appfitness.composeapp.R
import io.github.lopezjavier.appfitness.domain.model.RestSound

/**
 * Posts the "Descanso terminado" notification and owns its channels. Android 17 blocks audio played by an app in the
 * background (also from an exact-alarm receiver), so the sound and the vibration come from the notification channel,
 * which the system plays: one high-importance channel per sound candidate, because a channel's sound is frozen once it
 * is created. The notification goes to the channel of the sound in use; T-148 deletes the unused channels.
 */
class RestAlertNotifier(private val context: Context) {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    /** Creates the three channels; safe to call on every start (existing channels are kept as they are). */
    fun ensureChannels() {
        RestSound.entries.forEach { sound ->
            manager.createNotificationChannel(
                NotificationChannel(channelId(sound), context.getString(channelName(sound)), IMPORTANCE_HIGH).apply {
                    setSound(soundUri(sound), attributes)
                    enableVibration(true)
                    vibrationPattern = RestAlertPattern.timings
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
    }

    /** Posts (or replaces) the notification; false when the user turned notifications off. */
    fun post(sound: RestSound): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        ensureChannels()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val content = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = Notification.Builder(context, channelId(sound))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.rest_alert_title))
            .setContentText(context.getString(R.string.rest_alert_body))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun soundUri(sound: RestSound): Uri = when (sound) {
        RestSound.SYSTEM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI

        RestSound.CHIME -> rawUri(R.raw.rest_chime)

        RestSound.DOUBLE_BEEP -> rawUri(R.raw.rest_double_beep)
    }

    private fun rawUri(resId: Int): Uri =
        Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE).authority(context.packageName)
            .appendPath(resId.toString()).build()

    private fun channelName(sound: RestSound): Int = when (sound) {
        RestSound.SYSTEM -> R.string.rest_alert_channel_system
        RestSound.CHIME -> R.string.rest_alert_channel_chime
        RestSound.DOUBLE_BEEP -> R.string.rest_alert_channel_double_beep
    }

    companion object {
        const val NOTIFICATION_ID = 4601
        private const val IMPORTANCE_HIGH = NotificationManager.IMPORTANCE_HIGH
        private val attributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /** Channel id of [sound]. */
        fun channelId(sound: RestSound): String = "rest_alert_${sound.id}"
    }
}
