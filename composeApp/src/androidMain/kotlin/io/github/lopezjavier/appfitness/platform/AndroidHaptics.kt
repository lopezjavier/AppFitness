package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.lopezjavier.appfitness.domain.port.Haptics

private const val ALERT_MILLIS = 400L

/** [Haptics] on the device vibrator: the end-of-rest buzz. Does nothing on a device without a vibrator. */
class AndroidHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    override fun alert() {
        val device = vibrator?.takeIf { it.hasVibrator() } ?: return
        device.vibrate(VibrationEffect.createOneShot(ALERT_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
