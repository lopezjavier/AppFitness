package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PlateResult
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Plates per side for a barbell (`04` §4.9, ADR-52): the fewest plates that make `(target - bar) / 2`, preferring the
 * heavier plate on ties (100 kg with a 20 kg bar -> 25 + 15). When the target cannot be made, the closest lower
 * makeable weight is returned with `exact = false`; a target at or below the bar gives no plates.
 *
 * Plates are whole multiples of 0.25 kg (the catalogue is), so the search runs over quarter-kilo units.
 */
object PlateCalculator {
    private const val UNITS_PER_KG = 4.0
    private const val TWO_SIDES = 2
    private const val EPSILON = 1e-9
    private const val NOT_REACHABLE = Int.MAX_VALUE

    operator fun invoke(targetKg: Double, barKg: Double, plateKg: List<Double>): PlateResult {
        val plates = plateKg.distinct().sortedDescending()
            .filter { it > 0 && isQuarter(it) }
            .map { (it * UNITS_PER_KG).roundToInt() }
        val perSideUnits = floor((targetKg - barKg) / TWO_SIDES * UNITS_PER_KG + EPSILON).toInt()
        if (perSideUnits <= 0 || plates.isEmpty()) {
            return PlateResult(emptyList(), barKg, exact = targetKg - barKg in -EPSILON..EPSILON)
        }
        val fewest = IntArray(perSideUnits + 1) { NOT_REACHABLE }.also { it[0] = 0 }
        for (unit in 1..perSideUnits) {
            for (plate in plates) {
                val rest = unit - plate
                if (rest >= 0 && fewest[rest] != NOT_REACHABLE && fewest[rest] + 1 < fewest[unit]) {
                    fewest[unit] = fewest[rest] + 1
                }
            }
        }
        val best = (perSideUnits downTo 0).first { fewest[it] != NOT_REACHABLE }
        val chosen = mutableListOf<Double>()
        var unit = best
        while (unit > 0) {
            val plate = plates.first { it <= unit && fewest[unit - it] == fewest[unit] - 1 }
            chosen += plate / UNITS_PER_KG
            unit -= plate
        }
        val achieved = barKg + TWO_SIDES * best / UNITS_PER_KG
        return PlateResult(chosen, achieved, exact = targetKg - achieved in -EPSILON..EPSILON)
    }

    private fun isQuarter(kg: Double) = kotlin.math.abs(kg * UNITS_PER_KG - (kg * UNITS_PER_KG).roundToInt()) < EPSILON
}

/** "25 + 15": the plates of one side with a decimal comma ("1,25"); blank when there are none. */
fun PlateResult.perSideText(): String = platesPerSide.joinToString(" + ") { GhostData.weightText(it) }
