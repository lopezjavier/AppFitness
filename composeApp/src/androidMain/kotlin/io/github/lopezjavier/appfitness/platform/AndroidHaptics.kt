package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.lopezjavier.appfitness.domain.port.Haptics

private const val TICK_MILLIS = 15L

/** [Haptics] on the device vibrator: one very short pulse. Does nothing on a device without a vibrator. */
class AndroidHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    override fun tick() {
        val device = vibrator?.takeIf { it.hasVibrator() } ?: return
        device.vibrate(VibrationEffect.createOneShot(TICK_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
