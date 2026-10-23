package io.github.lopezjavier.appfitness.domain.usecase

/** The RPE (rate of perceived exertion) grid of a set: 6 to 10 in half points (US-30, ADR-50). */
object RpeScale {
    const val MIN = 6.0
    const val MAX = 10.0
    const val STEP = 0.5

    val values: List<Double> = generateSequence(MIN) { it + STEP }.takeWhile { it <= MAX }.toList()

    fun isValid(value: Double): Boolean = value in values

    /** "8" or "7,5": decimal comma, no trailing ",0". */
    fun text(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString().replace('.', ',')

    /** Parses "7,5" or "7.5"; null when it is not a grid value. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf(::isValid)
}
