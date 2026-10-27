package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.lopezjavier.appfitness.domain.port.Haptics

private const val NO_REPEAT = -1

/** [Haptics] on the device vibrator: the end-of-rest three-pulse buzz. Does nothing without a vibrator. */
class AndroidHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    override fun alert() {
        val device = vibrator?.takeIf { it.hasVibrator() } ?: return
        device.vibrate(
            VibrationEffect.createWaveform(RestAlertPattern.timings, RestAlertPattern.amplitudes, NO_REPEAT),
        )
    }
}
