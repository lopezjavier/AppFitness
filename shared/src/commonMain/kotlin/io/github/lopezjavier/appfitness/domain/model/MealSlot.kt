package io.github.lopezjavier.appfitness.domain.model

/** Default meal kinds of `public.meal_type`; custom slots have no code. */
enum class MealCode { Breakfast, Lunch, Snack, Dinner }

/** One meal of the day (`meal_slots`). [position] orders the sections; disabled slots are never listed. */
data class MealSlot(
    val id: String,
    val code: MealCode?,
    val label: String,
    val position: Int,
    val enabled: Boolean = true,
)

/** Limits of a meal label (matches the `meal_slots.label` check). */
object MealSlotBounds {
    const val LABEL_MAX_LENGTH = 50
}

/** One row of the meals editor; the list order is the final position. A null [id] is a new custom meal. */
data class MealSlotDraft(val id: String?, val code: MealCode?, val label: String)
