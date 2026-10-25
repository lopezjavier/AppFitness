package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `meal_defaults`. */
@Serializable
internal data class MealDefaultDto(
    @SerialName("meal_slot_id") val mealSlotId: String,
    @SerialName("template_id") val templateId: String,
    val weekday: Int,
) {
    fun toDomain() = MealDefault(mealSlotId, templateId, weekday)

    companion object {
        const val COLUMNS = "meal_slot_id,template_id,weekday"
    }
}

/** One element of `p_defaults` of `save_meal_defaults`. */
@Serializable
internal data class SaveMealDefaultParam(
    @SerialName("meal_slot_id") val mealSlotId: String,
    @SerialName("template_id") val templateId: String,
    val weekdays: List<Int>,
) {
    companion object {
        fun from(assignment: MealDefaultAssignment) = SaveMealDefaultParam(
            assignment.mealSlotId,
            assignment.templateId,
            assignment.weekdays.sorted(),
        )
    }
}

/** Arguments of the `save_meal_defaults` RPC. */
@Serializable
internal data class SaveMealDefaultsParams(@SerialName("p_defaults") val defaults: List<SaveMealDefaultParam>)

/** Arguments of the `apply_meal_defaults` RPC; the date is ISO yyyy-MM-dd. */
@Serializable
internal data class ApplyMealDefaultsParams(@SerialName("p_log_date") val logDate: String)
