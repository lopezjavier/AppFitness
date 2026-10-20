package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import kotlinx.datetime.LocalTime

private const val LUNCH_FROM = 11
private const val SNACK_FROM = 16
private const val DINNER_FROM = 20

/**
 * The meal a food is most likely for at a time: before 11:00 breakfast, 11:00-15:59 lunch, 16:00-19:59 snack, else
 * dinner. When that meal is not enabled, the nearest enabled coded meal after it, else the first enabled one.
 */
class DefaultMealSlot {
    operator fun invoke(now: LocalTime, enabledSlots: List<MealSlot>): MealSlot? {
        val slots = enabledSlots.filter { it.enabled }.sortedBy { it.position }
        val target = codeAt(now)
        return slots.firstOrNull { it.code == target }
            ?: slots.filter { (it.code?.ordinal ?: -1) > target.ordinal }.minByOrNull { it.code?.ordinal ?: 0 }
            ?: slots.firstOrNull()
    }

    private fun codeAt(now: LocalTime): MealCode = when {
        now.hour < LUNCH_FROM -> MealCode.Breakfast
        now.hour < SNACK_FROM -> MealCode.Lunch
        now.hour < DINNER_FROM -> MealCode.Snack
        else -> MealCode.Dinner
    }
}
