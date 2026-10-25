package io.github.lopezjavier.appfitness.domain.model

/** A saved meal ("comida"): foods with quantities. [mealSlotId] is only the meal it was saved from (a suggestion). */
data class MealTemplate(val id: String, val name: String, val mealSlotId: String?, val items: List<MealTemplateItem>)

/**
 * One food of a [MealTemplate]; [amount] is in the food's base unit and [portionLabel] only a display snapshot.
 * [cookingMethod] is the method only; the retained oil is computed when the comida is added (ADR-63).
 */
data class MealTemplateItem(
    val food: Food,
    val amount: Double,
    val portionLabel: String?,
    val cookingMethod: CookingMethod = CookingMethod.Raw,
)

/** A food to store in a template, in its final position. */
data class NewTemplateItem(
    val foodId: String,
    val amount: Double,
    val portionLabel: String?,
    val cookingMethod: CookingMethod = CookingMethod.Raw,
)
