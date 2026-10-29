package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import kotlin.math.floor

/**
 * Applies the weight change of a progression suggestion (`04` §4.14). The server returns the base weight; here a raise
 * adds one step and a drop subtracts 5 %, rounded to the nearest step (ties up) and always moving by at least one step.
 * Step = 2 x smallest enabled plate ([CalculatorSettings.roundingKg]); dumbbells use the handle weight.
 */
object ProgressionWeight {
    private const val DROP_FRACTION = 0.05
    private const val EPSILON = 1e-9
    private const val HALF = 0.5

    fun stepKg(equipment: Equipment?, settings: CalculatorSettings): Double =
        if (equipment == Equipment.Dumbbell) settings.dumbbellHandleKg else settings.roundingKg

    /** The weight to show for [suggestion]; `add_rep` keeps the base weight. */
    fun weightKg(suggestion: ProgressionSuggestion, stepKg: Double): Double = when (suggestion.action) {
        ProgressionAction.AddRep -> suggestion.weightKg
        ProgressionAction.RaiseWeight -> suggestion.weightKg + stepKg
        ProgressionAction.LowerWeight -> lowered(suggestion.weightKg, stepKg)
    }

    /** [suggestion] with the weight to show instead of the base weight. */
    fun apply(suggestion: ProgressionSuggestion, equipment: Equipment?, settings: CalculatorSettings) =
        suggestion.copy(weightKg = weightKg(suggestion, stepKg(equipment, settings)))

    private fun lowered(base: Double, step: Double): Double {
        val target = base * (1 - DROP_FRACTION)
        val rounded = if (step <= 0.0) target else floor(target / step + HALF + EPSILON) * step
        val result = if (rounded >= base) base - step else rounded
        return result.coerceAtLeast(0.0)
    }
}
