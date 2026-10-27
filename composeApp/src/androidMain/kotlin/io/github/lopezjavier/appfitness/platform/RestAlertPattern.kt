package io.github.lopezjavier.appfitness.platform

/** The end-of-rest vibration: three strong pulses over about 1.2 s. Pure constants so a test can assert them. */
internal object RestAlertPattern {
    private const val PULSE_MILLIS = 300L
    private const val GAP_MILLIS = 150L
    private const val PULSE_AMPLITUDE = 255

    /** Alternating off/on durations as `VibrationEffect.createWaveform` reads them (starts with a pause). */
    val timings: LongArray = longArrayOf(0, PULSE_MILLIS, GAP_MILLIS, PULSE_MILLIS, GAP_MILLIS, PULSE_MILLIS)

    /** One amplitude per timing entry; 0 during pauses. */
    val amplitudes: IntArray = intArrayOf(0, PULSE_AMPLITUDE, 0, PULSE_AMPLITUDE, 0, PULSE_AMPLITUDE)

    val pulseCount: Int get() = amplitudes.count { it > 0 }

    val totalMillis: Long get() = timings.sum()
}
