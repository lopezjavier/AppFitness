package io.github.lopezjavier.appfitness.designsystem

private const val GROUP_SIZE = 3
private const val MINUS = '−'

/**
 * Number formatting for the Spanish UI (planning/04-business-rules.md §5). CLDR es-ES does not group
 * 4-digit numbers, so this is a minimal formatter that does ("1.840"). Decimals wait for E4.
 */
object AppNumberFormat {
    /** Spanish grouping: "." every 3 digits, also for 4 digits; negatives use U+2212. */
    fun integer(value: Int): String {
        val digits = kotlin.math.abs(value.toLong()).toString()
        val grouped =
            digits
                .reversed()
                .chunked(GROUP_SIZE)
                .joinToString(".")
                .reversed()
        return if (value < 0) "$MINUS$grouped" else grouped
    }

    /** A whole value without decimals ("300"), otherwise the shortest form with a decimal comma ("87,5"). */
    fun decimal(value: Double): String {
        val whole = value.toLong()
        if (whole.toDouble() == value) return integer(whole.toInt())
        return value.toString().replace('.', ',')
    }

    /** Like [integer] but a positive value carries "+" ("+250", "−400", "0"). */
    fun signedInteger(value: Int): String = if (value > 0) "+${integer(value)}" else integer(value)
}
