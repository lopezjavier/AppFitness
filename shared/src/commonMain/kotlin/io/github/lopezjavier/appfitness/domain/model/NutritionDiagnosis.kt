package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/**
 * The first cause the nutrition diagnosis found when the plan's target rate lies outside the weight trend's interval on
 * two consecutive summaries (07 section 4 (b)/(c)), in check order. Figures are already rounded by the server; the UI
 * only formats them.
 */
sealed interface NutritionDiagnosis {
    /** Fewer than 5 complete food days: [n] in the week starting [weekStart]. */
    data class LoggingFood(val n: Int, val weekStart: LocalDate) : NutritionDiagnosis

    /** Fewer than 3 weigh-in days: [n] in the week starting [weekStart]. */
    data class LoggingWeighIn(val n: Int, val weekStart: LocalDate) : NutritionDiagnosis

    /**
     * The logged average [avgKcal] differs from the [goalKcal] by [diffKcal] (always positive), [above] or below it;
     * [weekend] when Saturday and Sunday carry at least half of the excess.
     */
    data class Calories(
        val avgKcal: Int,
        val goalKcal: Int,
        val diffKcal: Int,
        val above: Boolean,
        val weekend: Boolean,
    ) : NutritionDiagnosis

    /** The trend is flat ([slope] kg per week, interval [low] to [high]) and no cause was found. */
    data class Plateau(val slope: Double, val low: Double, val high: Double) : NutritionDiagnosis

    /** No check fired. */
    data object NoCause : NutritionDiagnosis
}
