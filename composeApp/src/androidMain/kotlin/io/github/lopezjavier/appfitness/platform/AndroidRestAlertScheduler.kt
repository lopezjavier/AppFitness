package io.github.lopezjavier.appfitness.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.lopezjavier.appfitness.domain.port.RestAlertScheduler
import kotlin.time.Instant

/**
 * [RestAlertScheduler] on an exact alarm that survives Doze and process death (`setExactAndAllowWhileIdle`; the
 * `USE_EXACT_ALARM` permission is granted at install). Without exact-alarm access it falls back to the inexact
 * variant instead of crashing. One stable request code, so a new alarm replaces the previous one.
 */
class AndroidRestAlertScheduler(private val context: Context) : RestAlertScheduler {
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(endsAt: Instant) {
        val millis = endsAt.toEpochMilliseconds()
        val pending = pendingIntent(millis)
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        runCatching {
            if (canBeExact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            }
        }.onFailure { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending) }
    }

    override fun cancel() {
        alarms.cancel(pendingIntent(0L))
    }

    private fun pendingIntent(endsAtEpochMs: Long): PendingIntent {
        val intent = Intent(
            context,
            RestAlertReceiver::class.java,
        ).putExtra(RestAlertReceiver.EXTRA_ENDS_AT, endsAtEpochMs)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val REQUEST_CODE = 4601
    }
}
