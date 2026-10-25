package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import kotlin.math.floor

/**
 * Olive oil a cooked food keeps (ADR-60, `04` §2.2): retained grams = oil used x retention factor; it adds
 * 9 kcal and 1 g of fat per gram. The defaults (5/5/50 g, in [FoodPreferences]) and the factors (in
 * [CookingMethod]) are mirrored by the SQL helper
 * `public.cooking_impact` (migration 20261107100000); keep both in sync.
 * Rounded like the server: oil to 2 decimals, kcal and fat to 1.
 */
object CookingImpact {
    private const val KCAL_PER_G = 9.0
    private const val HUNDREDTHS = 100.0
    private const val TENTHS = 10.0
    private const val HALF = 0.5
    private const val EPSILON = 1e-9
    private const val BASE_UNIT_G = "g"

    /** Grams of oil that stay in the food. */
    fun retainedG(method: CookingMethod, prefs: FoodPreferences): Double =
        round(prefs.usedG(method) * method.retentionFactor, HUNDREDTHS)

    fun addedKcal(retainedG: Double): Double = round(retainedG * KCAL_PER_G, TENTHS)

    fun addedFatG(retainedG: Double): Double = round(retainedG, TENTHS)

    /** [base] plus the oil of [retainedG]; protein and carbs are untouched. */
    fun apply(base: NutritionSnapshot, retainedG: Double): NutritionSnapshot = base.copy(
        kcal = round(base.kcal + addedKcal(retainedG), TENTHS),
        fatG = round(base.fatG + addedFatG(retainedG), TENTHS),
    )

    /** [base] cooked with [method] using [prefs]. */
    fun apply(base: NutritionSnapshot, method: CookingMethod, prefs: FoodPreferences): NutritionSnapshot =
        apply(base, retainedG(method, prefs))

    /** Only foods measured in grams can be cooked; drinks (`ml`) are always raw. */
    fun appliesTo(baseUnit: String?): Boolean = baseUnit == BASE_UNIT_G

    /** [method] when it applies to [baseUnit], otherwise [CookingMethod.Raw]. */
    fun effectiveMethod(method: CookingMethod, baseUnit: String?): CookingMethod =
        if (appliesTo(baseUnit)) method else CookingMethod.Raw

    private fun round(value: Double, scale: Double) = floor(value * scale + HALF + EPSILON) / scale
}
