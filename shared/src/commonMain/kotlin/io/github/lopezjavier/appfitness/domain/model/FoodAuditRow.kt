package io.github.lopezjavier.appfitness.domain.model

import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import kotlin.math.roundToInt

/**
 * One of the user's own foods whose kcal and macros do not match (`food_energy_audit()`). [kcal] is the declared
 * value, [computedKcal] what the macros add up to and [gap] their difference (`computedKcal - kcal`), all unrounded.
 * [level] is only [EnergyCheckLevel.Mismatch] or [EnergyCheckLevel.ClearlyWrong].
 */
data class FoodAuditRow(
    val foodId: String,
    val name: String,
    val brand: String?,
    val kcal: Double,
    val computedKcal: Double,
    val gap: Double,
    val level: EnergyCheckLevel,
) {
    /** The declared kcal in whole numbers, as shown. */
    val declaredWhole: Int get() = kcal.roundToInt()

    /** The kcal the macros add up to, in whole numbers, as shown. */
    val macrosWhole: Int get() = computedKcal.roundToInt()

    /** The gap derived from the rounded figures, so the three shown numbers always add up. */
    val gapWhole: Int get() = macrosWhole - declaredWhole
}
