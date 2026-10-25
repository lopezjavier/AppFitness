package io.github.lopezjavier.appfitness.domain.model

/** A saved comida assigned to one meal on one ISO weekday (Monday = 1 ... Sunday = 7). */
data class MealDefault(val mealSlotId: String, val templateId: String, val weekday: Int)

/** Grouped form used to save: the comida `templateId` is the default of `mealSlotId` on every weekday of `weekdays`. */
data class MealDefaultAssignment(val mealSlotId: String, val templateId: String, val weekdays: Set<Int>)
