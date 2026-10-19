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

/** A session volume in whole kilos with Spanish grouping ("4.260"). */
internal fun formatVolume(volumeKg: Double): String = AppNumberFormat.integer(volumeKg.roundToInt())
