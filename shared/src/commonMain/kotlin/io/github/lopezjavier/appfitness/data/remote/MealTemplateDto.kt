package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `meal_template_items` with its food embedded (a food hidden by RLS comes back null). */
@Serializable
internal data class MealTemplateItemDto(
    val amount: Double,
    @SerialName("portion_label") val portionLabel: String? = null,
    val position: Int = 0,
    @SerialName("cooking_method") val cookingMethod: CookingMethodDto = CookingMethodDto.Raw,
    val foods: FoodDto? = null,
)

/** Row of `meal_templates` with its items embedded. */
@Serializable
internal data class MealTemplateDto(
    val id: String,
    val name: String,
    @SerialName("meal_slot_id") val mealSlotId: String? = null,
    @SerialName("meal_template_items") val items: List<MealTemplateItemDto> = emptyList(),
) {
    fun toDomain(): MealTemplate = MealTemplate(
        id = id,
        name = name,
        mealSlotId = mealSlotId,
        items = items.sortedBy { it.position }.mapNotNull { item ->
            item.foods?.let {
                MealTemplateItem(it.toDomain(), item.amount, item.portionLabel, item.cookingMethod.domain)
            }
        },
    )

    companion object {
        /** PostgREST `select` of a template with its items and their foods (always the shared food columns). */
        const val COLUMNS =
            "id,name,meal_slot_id,meal_template_items(food_id,amount,portion_label,position,cooking_method," +
                "foods(${FoodDto.COLUMNS}))"
    }
}

/** One element of `p_items` of `save_meal_template`; the array order is the position. Nulls are always sent. */
@Serializable
internal data class SaveTemplateItemParam(
    @SerialName("food_id") val foodId: String,
    val amount: Double,
    @SerialName("portion_label") val portionLabel: String?,
    @SerialName("cooking_method") val cookingMethod: CookingMethodDto,
) {
    companion object {
        fun from(item: NewTemplateItem) = SaveTemplateItemParam(
            item.foodId,
            item.amount,
            item.portionLabel,
            CookingMethodDto.of(item.cookingMethod),
        )
    }
}

/** Arguments of the `save_meal_template` RPC (a null template id creates). */
@Serializable
internal data class SaveMealTemplateParams(
    @SerialName("p_template_id") val templateId: String?,
    @SerialName("p_name") val name: String,
    @SerialName("p_meal_slot_id") val mealSlotId: String?,
    @SerialName("p_items") val items: List<SaveTemplateItemParam>,
)

/** Arguments of the `save_meal_as_comida` RPC: saves the template and groups the entries [entryIds] (ADR-68). */
@Serializable
internal data class SaveMealAsComidaParams(
    @SerialName("p_log_date") val logDate: String,
    @SerialName("p_meal_slot_id") val mealSlotId: String,
    @SerialName("p_name") val name: String,
    @SerialName("p_items") val items: List<SaveTemplateItemParam>,
    @SerialName("p_entry_ids") val entryIds: List<String>,
)

/** Patch body of `meal_templates` for a rename. */
@Serializable
internal data class RenameTemplateDto(val name: String)
