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
