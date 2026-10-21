package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import kotlinx.datetime.LocalDate

/**
 * Logs every food of a template into meal [mealSlotId] of a day in ONE request. The snapshots come from the foods'
 * current values, so already logged entries are never touched.
 */
class AddTemplateToDiary(private val foodDiary: FoodDiaryRepository, private val scaleNutrition: ScaleNutrition) {
    suspend operator fun invoke(
        template: MealTemplate,
        mealSlotId: String,
        date: LocalDate,
    ): Result<List<FoodLogEntry>> {
        if (template.items.isEmpty()) return Result.success(emptyList())
        val entries = template.items.map { item ->
            NewFoodEntry(
                date = date,
                mealSlotId = mealSlotId,
                foodId = item.food.id,
                foodName = item.food.name,
                amount = item.amount,
                portionLabel = item.portionLabel,
                snapshot = scaleNutrition(item.food.nutrition, item.amount),
            )
        }
        return foodDiary.addEntries(entries)
    }
}
