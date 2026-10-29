package io.github.lopezjavier.appfitness.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import io.github.lopezjavier.appfitness.composeapp.R

/**
 * Posts the "Descanso terminado" notification and owns its channel. Android 17 blocks audio played by an app in the
 * background (also from an exact-alarm receiver), so the sound and the vibration come from the notification channel,
 * which the system plays. There is one high-importance channel with the bundled "Campana suave"; a channel's sound is
 * frozen once created, and this id already carries the chime on the devices that used it.
 */
class RestAlertNotifier(private val context: Context) {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    /**
     * Creates the channel (an existing one is kept as it is) and deletes the channels of the removed sound
     * candidates; safe to call on every start.
     */
    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.rest_alert_channel_chime), IMPORTANCE_HIGH)
                .apply {
                    setSound(rawUri(R.raw.rest_chime), attributes)
                    enableVibration(true)
                    vibrationPattern = RestAlertPattern.timings
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
        )
        REMOVED_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
    }

    /** Posts (or replaces) the notification; false when the user turned notifications off. */
    fun post(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        createChannels()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val content = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
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

    private fun rawUri(resId: Int): Uri =
        Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE).authority(context.packageName)
            .appendPath(resId.toString()).build()

    companion object {
        const val NOTIFICATION_ID = 4601
        const val CHANNEL_ID = "rest_alert_chime"
        private val REMOVED_CHANNEL_IDS = listOf("rest_alert_system", "rest_alert_double_beep")
        private const val IMPORTANCE_HIGH = NotificationManager.IMPORTANCE_HIGH
        private val attributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
