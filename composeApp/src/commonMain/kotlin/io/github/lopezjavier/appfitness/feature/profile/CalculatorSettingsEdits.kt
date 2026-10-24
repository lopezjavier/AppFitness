package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings

/** Practical range of the dumbbell handle stepper (the database allows up to 25 kg). */
internal const val MIN_ROUNDING_KG = 0.5
internal const val MAX_ROUNDING_KG = 10.0

internal fun CalculatorSettings.withDumbbellHandle(kg: Double) =
    copy(dumbbellHandleKg = kg.coerceIn(MIN_ROUNDING_KG, MAX_ROUNDING_KG))

/** Only the bars of the catalogue are accepted; anything else leaves the settings unchanged. */
internal fun CalculatorSettings.withBar(kg: Double) =
    if (kg in CalculatorSettings.BARS_KG) copy(barWeightKg = kg) else this

/**
 * Switches [kg] on or off, keeping the plates heaviest first. Switching off the last plate is refused (null), because
 * at least one plate must stay available; unknown plate weights are ignored.
 */
internal fun CalculatorSettings.withPlate(kg: Double, enabled: Boolean): CalculatorSettings? {
    val plates = when {
        kg !in CalculatorSettings.PLATE_CATALOGUE -> plateKg
        enabled -> (plateKg + kg).distinct().sortedDescending()
        else -> plateKg - kg
    }
    return if (plates.isEmpty()) null else copy(plateKg = plates)
}
