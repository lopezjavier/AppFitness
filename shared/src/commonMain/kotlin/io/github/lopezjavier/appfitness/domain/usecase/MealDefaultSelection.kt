package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment

/** Pure helpers over the saved defaults, used to pick weekdays for one meal. */
object MealDefaultSelection {
    /** Weekdays that already have a default for the meal, whichever comida. */
    fun assignedDays(defaults: List<MealDefault>, mealSlotId: String): Set<Int> =
        defaults.filter { it.mealSlotId == mealSlotId }.map { it.weekday }.toSet()

    /** Weekdays the comida can be assigned to in the meal: free or already owned by that comida. */
    fun availableDays(defaults: List<MealDefault>, mealSlotId: String, templateId: String): Set<Int> =
        IsoWeekday.ALL.toSet() - conflictingDays(defaults, mealSlotId, templateId)

    /** Weekdays of the meal assigned to a different comida. */
    fun conflictingDays(defaults: List<MealDefault>, mealSlotId: String, templateId: String): Set<Int> =
        defaults.filter { it.mealSlotId == mealSlotId && it.templateId != templateId }.map { it.weekday }.toSet()

    fun isEveryDayAssigned(defaults: List<MealDefault>, mealSlotId: String): Boolean =
        assignedDays(defaults, mealSlotId).size == IsoWeekday.ALL.size

    /** The meal's defaults grouped by comida, days sorted; order follows the first appearance of each comida. */
    fun defaultsOf(defaults: List<MealDefault>, mealSlotId: String): List<MealDefaultAssignment> =
        toAssignments(defaults.filter { it.mealSlotId == mealSlotId })

    fun toAssignments(defaults: List<MealDefault>): List<MealDefaultAssignment> =
        defaults.groupBy { it.mealSlotId to it.templateId }.map { (key, rows) ->
            MealDefaultAssignment(key.first, key.second, rows.map { it.weekday }.sorted().toSet())
        }
}
