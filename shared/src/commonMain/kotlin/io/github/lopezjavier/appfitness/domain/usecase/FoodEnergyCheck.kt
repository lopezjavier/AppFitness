package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

/** Outcome level of the food energy check. */
enum class EnergyCheckLevel { Skipped, Ok, Mismatch, ClearlyWrong }

/** [computed] is the kcal the macros add up to (unrounded); [gap] is `computed - declared kcal`. */
data class EnergyCheckResult(val level: EnergyCheckLevel, val computed: Double, val gap: Double)

/**
 * Deterministic check of a food's declared kcal against its macros (07 section 4 "Food energy check"); the same
 * figures as the SQL `food_energy_check`. All inputs are grams (kcal for [check]'s first argument) per 100 base
 * units; null counts as 0.
 */
object FoodEnergyCheck {
    private const val PROTEIN = 4.0
    private const val CARBS = 4.0
    private const val POLYOLS = 2.4
    private const val FAT = 9.0
    private const val FIBRE = 2.0
    private const val ALCOHOL = 7.0
    private const val SKIP_LIMIT = 13.0
    private const val MISMATCH_RATIO = 0.15
    private const val WRONG_RATIO = 0.30
    private const val BAND_BASE = 5.0

    // The SQL uses exact numeric: round to nano units so 0.15 * 100 + 5 does not flip in doubles.
    private const val SCALE = 1e9

    @Suppress("LongParameterList")
    fun check(
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fibre: Double? = null,
        polyols: Double? = null,
        alcohol: Double? = null,
    ): EnergyCheckResult {
        val p = min(polyols ?: 0.0, carbs)
        val computed = PROTEIN * protein + CARBS * (carbs - p) + POLYOLS * p + FAT * fat +
            FIBRE * (fibre ?: 0.0) + ALCOHOL * (alcohol ?: 0.0)
        val gap = computed - kcal
        val level = when {
            q(kcal) <= q(SKIP_LIMIT) && q(computed) <= q(SKIP_LIMIT) -> EnergyCheckLevel.Skipped
            q(abs(gap)) > q(WRONG_RATIO * kcal + BAND_BASE) -> EnergyCheckLevel.ClearlyWrong
            q(abs(gap)) > q(MISMATCH_RATIO * kcal + BAND_BASE) -> EnergyCheckLevel.Mismatch
            else -> EnergyCheckLevel.Ok
        }
        return EnergyCheckResult(level, computed, gap)
    }

    private fun q(value: Double): Long = round(value * SCALE).toLong()
}
