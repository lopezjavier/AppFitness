package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.designsystem.NumberInputDefaults

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val TWO_DIGITS = 2

/** Stopwatch text: "05:07" under an hour, "1:02:03" from one hour on. A negative value counts as zero. */
fun formatElapsed(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    val mm = minutes.toString().padStart(TWO_DIGITS, '0')
    val ss = rest.toString().padStart(TWO_DIGITS, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
}

/** The weight as the number field shows it: "60" or "87,5" (never a trailing zero). */
internal fun weightText(weightKg: Double): String {
    if (weightKg == weightKg.toLong().toDouble()) return weightKg.toLong().toString()
    return weightKg.toString().trimEnd('0').trimEnd('.').replace('.', NumberInputDefaults.DECIMAL_SEPARATOR)
}
