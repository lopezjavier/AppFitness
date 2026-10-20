package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DiaryDay
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealSection
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import kotlinx.datetime.LocalDate

/** Groups a day's entries into one section per enabled meal, ordered by position, with the day totals. */
class BuildDiaryDay {
    operator fun invoke(
        date: LocalDate,
        slots: List<MealSlot>,
        entries: List<FoodLogEntry>,
        goals: NutritionGoals?,
    ): DiaryDay {
        val meals = slots.filter { it.enabled }.sortedBy { it.position }.map { slot ->
            val own = entries.filter { it.mealSlotId == slot.id }
            MealSection(slot, own, DiaryTotals.sum(own))
        }
        return DiaryDay(date, goals?.let(::diaryGoals), meals, DiaryTotals.sum(entries))
    }

    private fun diaryGoals(goals: NutritionGoals): DiaryGoals {
        val targets = CalculateMacroTargets(goals.targetKcal, goals.split)
        return DiaryGoals(goals.targetKcal, targets.proteinG, targets.carbsG, targets.fatG)
    }
}
