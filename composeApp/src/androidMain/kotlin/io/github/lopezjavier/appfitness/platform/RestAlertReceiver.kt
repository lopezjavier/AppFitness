package io.github.lopezjavier.appfitness.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/** Decides whether the alarm of a finished rest posts the notification. */
class RestAlertHandler(
    private val notifier: RestAlertNotifier,
    private val isForeground: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** Posts the alert for a rest that ended at [endsAtEpochMs]; false when nothing was posted. */
    suspend fun handle(endsAtEpochMs: Long): Boolean {
        // In the foreground the in-app alert already fired; a very late alarm is no longer useful.
        if (isForeground() || nowMillis() - endsAtEpochMs > MAX_LATE_MILLIS) return false
        return notifier.post()
    }

    companion object {
        const val MAX_LATE_MILLIS = 10 * 60 * 1000L
    }
}

/** Fired by the exact alarm at the end of a rest: hands over to [RestAlertHandler] (notification, sound, vibration). */
class RestAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, 0L)
        val pending = goAsync()
        val handler = KoinPlatform.getKoin().get<RestAlertHandler>()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handler.handle(endsAt)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ENDS_AT = "endsAtEpochMs"
    }
}
