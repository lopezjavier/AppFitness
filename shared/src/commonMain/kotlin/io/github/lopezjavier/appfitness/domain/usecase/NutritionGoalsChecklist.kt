package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlin.math.ceil

/** The four facts checked on the goals in use (planning/07 §4); [GoalCheckKey] names the UI row. */
enum class GoalCheckKey { N1Floor, N2Rate, N3Protein, N4Fat }

/**
 * One pass/fail fact with the numbers its Spanish template needs. Only the fields of the row's [key] are set:
 * N1 [kcal] and [floorKcal]; N2 [kcal], [ratePct] (positive in the direction of the phase; null without an
 * expenditure), [rangeFromPct]/[rangeToPct] and [deficitOverCap]; N3 and N4 [grams] and [minGrams].
 */
data class GoalCheck(
    val key: GoalCheckKey,
    val passed: Boolean,
    val kcal: Int? = null,
    val floorKcal: Int? = null,
    val ratePct: Double? = null,
    val rangeFromPct: Double? = null,
    val rangeToPct: Double? = null,
    val deficitOverCap: Boolean = false,
    val grams: Int? = null,
    val minGrams: Int? = null,
)

/** The daily goals in use. */
data class GoalTargets(val targetKcal: Int, val proteinG: Int, val fatG: Int)

/** The four rows in order; there is no score. */
data class GoalChecklist(val checks: List<GoalCheck>) {
    val allPassed: Boolean get() = checks.all { it.passed }
}

/**
 * N1-N4 on the targets in use (planning/07 §4, shown only after the user edited the goals):
 * N1 kcal >= the sex floor; N2 the implied weekly rate inside the phase range and a deficit of at most 500 kcal;
 * N3 protein >= 1.6 g/kg; N4 fat >= 20 % of the calories.
 */
object NutritionGoalsChecklist {
    const val MAX_DEFICIT_KCAL = 500
    const val MIN_PROTEIN_G_PER_KG = 1.6
    const val MIN_FAT_SHARE = 0.20
    private const val KCAL_PER_G_FAT = 9
    private const val KCAL_PER_PCT_KG = 11.0 // 7700 kcal/kg / 7 days / 100
    private const val ROUNDING_KCAL = 10.0 // plans round the adjustment up to 10 kcal (04 §8.2)
    private const val CUT_MIN = 0.5
    private const val CUT_MAX = 1.0
    private const val GAIN_MIN = 0.25
    private const val GAIN_MAX = 0.5
    private const val MAINTAIN_LIMIT = 0.25

    /** Rate range in percent of body weight per week, in the direction of the phase (04 §8.1). */
    private fun rangeOf(phase: Phase): ClosedFloatingPointRange<Double> = when (phase) {
        Phase.Cut -> CUT_MIN..CUT_MAX
        Phase.Gain -> GAIN_MIN..GAIN_MAX
        Phase.Maintenance, Phase.Recomposition -> -MAINTAIN_LIMIT..MAINTAIN_LIMIT
    }

    fun minFatG(kcal: Int): Int = ceil(MIN_FAT_SHARE * kcal / KCAL_PER_G_FAT - EPS).toInt()

    fun minProteinG(weightKg: Double): Int = ceil(MIN_PROTEIN_G_PER_KG * weightKg - EPS).toInt()

    operator fun invoke(
        targets: GoalTargets,
        weightKg: Double,
        sex: Sex,
        phase: Phase,
        expenditureKcal: Double?,
    ): GoalChecklist {
        val (targetKcal, proteinG, fatG) = targets
        val floor = EnergyStrategyRules.floorKcal(sex)
        val minProtein = minProteinG(weightKg)
        val minFat = minFatG(targetKcal)
        return GoalChecklist(
            listOf(
                GoalCheck(GoalCheckKey.N1Floor, targetKcal >= floor, kcal = targetKcal, floorKcal = floor),
                rateCheck(targetKcal, weightKg, phase, expenditureKcal),
                GoalCheck(
                    GoalCheckKey.N3Protein,
                    proteinG >= minProtein,
                    grams = proteinG,
                    minGrams = minProtein,
                ),
                GoalCheck(GoalCheckKey.N4Fat, fatG >= minFat, grams = fatG, minGrams = minFat),
            ),
        )
    }

    private fun rateCheck(targetKcal: Int, weightKg: Double, phase: Phase, expenditureKcal: Double?): GoalCheck {
        val range = rangeOf(phase)
        if (expenditureKcal == null || weightKg <= 0.0) {
            return GoalCheck(GoalCheckKey.N2Rate, true, kcal = targetKcal)
        }
        // Positive = in the direction of the phase (loss for a cut, gain otherwise).
        val diff = if (phase == Phase.Cut) expenditureKcal - targetKcal else targetKcal - expenditureKcal
        val perPct = weightKg * KCAL_PER_PCT_KG
        val ratePct = diff / perPct
        val insideRange = diff >= range.start * perPct - EPS && diff <= range.endInclusive * perPct + ROUNDING_KCAL
        val overCap = phase == Phase.Cut && diff > MAX_DEFICIT_KCAL
        return GoalCheck(
            key = GoalCheckKey.N2Rate,
            passed = insideRange && !overCap,
            kcal = targetKcal,
            ratePct = ratePct,
            rangeFromPct = range.start,
            rangeToPct = range.endInclusive,
            deficitOverCap = overCap,
        )
    }

    private const val EPS = 1e-9
}
