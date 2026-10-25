package io.github.lopezjavier.appfitness.feature.analytics

import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TENTHS = 10
private const val MINUS = '−'

/** Kilos with one decimal and a comma ("113,5", "1.250,5"); a negative value uses U+2212. */
internal fun formatKilos(value: Double): String {
    val tenths = (abs(value) * TENTHS).roundToInt()
    val text = "${AppNumberFormat.integer(tenths / TENTHS)},${tenths % TENTHS}"
    return if (value < 0 && tenths != 0) "$MINUS$text" else text
}

/** Like [formatKilos] but a positive value carries "+" ("+5,0", "−2,5"). */
internal fun formatSignedKilos(value: Double): String =
    if (value > 0 && (value * TENTHS).roundToInt() != 0) "+${formatKilos(value)}" else formatKilos(value)

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
private const val METRES_PER_KM = 1_000
private const val HUNDREDTHS = 100

/** A duration: "m:ss" below one hour ("1:30"), "h:mm" from one hour on ("1:05"). */
internal fun formatDuration(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    return if (total >= SECONDS_PER_HOUR) {
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        "${total / SECONDS_PER_HOUR}:${minutes.toString().padStart(2, '0')}"
    } else {
        "${total / SECONDS_PER_MINUTE}:${(total % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
    }
}

/** A distance given in metres: "800 m" below a kilometre, otherwise km with a decimal comma ("5,2 km", "10 km"). */
internal fun formatDistance(metres: Double): String {
    val value = metres.roundToInt().coerceAtLeast(0)
    if (value < METRES_PER_KM) return "$value m"
    val hundredths = (value.toDouble() / METRES_PER_KM * HUNDREDTHS).roundToInt()
    val whole = AppNumberFormat.integer(hundredths / HUNDREDTHS)
    val fraction = (hundredths % HUNDREDTHS).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole km" else "$whole,$fraction km"
}

/** A session volume in whole kilos with Spanish grouping ("4.260"). */
internal fun formatVolume(volumeKg: Double): String = AppNumberFormat.integer(volumeKg.roundToInt())
