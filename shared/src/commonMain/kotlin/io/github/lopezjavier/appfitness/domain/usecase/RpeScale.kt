package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.math.floor

/** The RPE (rate of perceived exertion) scale of a set: whole numbers 6 to 10 (US-30, ADR-55/56). */
object RpeScale {
    const val MIN = 6.0
    const val MAX = 10.0
    const val STEP = 1.0
    private const val HALF = 0.5

    val values: List<Double> = generateSequence(MIN) { it + STEP }.takeWhile { it <= MAX }.toList()

    fun isValid(value: Double): Boolean = value in values

    private fun roundHalfUp(value: Double): Double = floor(value + HALF)

    /** Rounds half up (stored 7.5 reads as 8); a stored value below [MIN] reads as [MIN]. */
    fun round(value: Double): Double = roundHalfUp(value).coerceAtLeast(MIN)

    /** The whole number as text, e.g. "8"; no decimals. */
    fun text(value: Double): String = round(value).toLong().toString()

    /** Parses "8", "7,5" or "7.5" (rounded half up); null when the result is outside 6..10. */
    fun parse(text: String): Double? {
        val number = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val rounded = roundHalfUp(number)
        return rounded.takeIf(::isValid)
    }
}
