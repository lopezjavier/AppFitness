package io.github.lopezjavier.appfitness.designsystem

import kotlin.math.abs
import kotlin.math.floor

private const val GROUP_SIZE = 3
private const val MINUS = '−'
private const val TEN = 10L
private const val HALF = 0.5
private const val MAX_SCALED = 9.0e18
private const val HUGE = "999.999.999.999.999"

/**
 * Number formatting for the Spanish UI (planning/04-business-rules.md §5). CLDR es-ES does not group
 * 4-digit numbers, so this is a minimal formatter that does ("1.840").
 */
object AppNumberFormat {
    /** Spanish grouping: "." every 3 digits, also for 4 digits; negatives use U+2212. */
    fun integer(value: Int): String {
        val grouped = group(kotlin.math.abs(value.toLong()).toString())
        return if (value < 0) "$MINUS$grouped" else grouped
    }

    private fun group(digits: String): String = digits.reversed().chunked(GROUP_SIZE).joinToString(".").reversed()

    /**
     * [value] rounded half-up to at most [maxDecimals] decimals (default 1) with trailing zeros dropped: "7" for
     * 7.0000000001, "7,3" for 7.2999999, "1.234,6" for 1234.56, "−0,5". Never `Double.toString()`, so float noise
     * cannot reach the screen. [grouping] false skips the thousands dots (text a user edits and we parse back).
     * NaN and infinities give "0".
     */
    fun decimal(value: Double, maxDecimals: Int = 1, grouping: Boolean = true): String {
        var scale = 1L
        repeat(maxDecimals) { scale *= TEN }
        val scaled = abs(value) * scale
        val rounded = if (value.isFinite()) floor(minOf(scaled, MAX_SCALED) + HALF).toLong() else 0L
        val text = when {
            rounded == 0L -> return "0"

            scaled >= MAX_SCALED -> HUGE

            else -> {
                val whole = (rounded / scale).toString()
                val fraction = (rounded % scale).toString().padStart(maxDecimals, '0').trimEnd('0')
                val integerPart = if (grouping) group(whole) else whole
                if (fraction.isEmpty()) integerPart else "$integerPart,$fraction"
            }
        }
        return if (value < 0) "$MINUS$text" else text
    }

    /** Like [integer] but a positive value carries "+" ("+250", "−400", "0"). */
    fun signedInteger(value: Int): String = if (value > 0) "+${integer(value)}" else integer(value)
}
