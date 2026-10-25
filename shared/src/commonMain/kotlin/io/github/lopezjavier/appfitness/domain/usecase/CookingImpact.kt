package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import kotlin.math.floor

/**
 * Fat a cooked food gains (ADR-65, `04` §2.2): added fat g = raw weight g x percentage / 100, rounded to 1
 * decimal; added kcal = 9 x that (rounded) fat. The default percentages (1/2/8, in [FoodPreferences]) are mirrored
 * by the SQL helper `public.cooking_impact` (migration 20261109100000); keep both in sync.
 */
object CookingImpact {
    private const val KCAL_PER_G = 9.0
    private const val PERCENT = 100.0
    private const val TENTHS = 10.0
    private const val HALF = 0.5
    private const val EPSILON = 1e-9
    private const val BASE_UNIT_G = "g"

    /** Grams of fat added to [amountG] grams of raw food cooked with [method]. */
    fun addedFatG(method: CookingMethod, amountG: Double, prefs: FoodPreferences): Double =
        round(amountG * prefs.pctFor(method) / PERCENT, TENTHS)

    /** Kcal of [fatG] grams of added fat, computed from the already rounded fat. */
    fun addedKcal(fatG: Double): Double = round(fatG * KCAL_PER_G, TENTHS)

    /** [base] plus [fatG] grams of added fat; protein and carbs are untouched. */
    fun apply(base: NutritionSnapshot, fatG: Double): NutritionSnapshot = base.copy(
        kcal = round(base.kcal + addedKcal(fatG), TENTHS),
        fatG = round(base.fatG + fatG, TENTHS),
    )

    /** [base] cooked with [method] from [amountG] grams of raw food using [prefs]. */
    fun apply(
        base: NutritionSnapshot,
        method: CookingMethod,
        amountG: Double,
        prefs: FoodPreferences,
    ): NutritionSnapshot = apply(base, addedFatG(method, amountG, prefs))

    /** Only foods measured in grams can be cooked; drinks (`ml`) are always raw. */
    fun appliesTo(baseUnit: String?): Boolean = baseUnit == BASE_UNIT_G

    /** [method] when it applies to [baseUnit], otherwise [CookingMethod.Raw]. */
    fun effectiveMethod(method: CookingMethod, baseUnit: String?): CookingMethod =
        if (appliesTo(baseUnit)) method else CookingMethod.Raw

    private fun round(value: Double, scale: Double) = floor(value * scale + HALF + EPSILON) / scale
}
