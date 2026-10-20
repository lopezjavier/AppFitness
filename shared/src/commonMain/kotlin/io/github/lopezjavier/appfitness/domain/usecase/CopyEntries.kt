package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Entries to create so that [FoodLogEntry]s appear again in meal `mealSlotId` of `date`. The amount, portion label and
 * snapshot are kept as logged (not recalculated from the food's current values), so the copies equal their source.
 */
class CopyEntries {
    operator fun invoke(entries: List<FoodLogEntry>, mealSlotId: String, date: LocalDate): List<NewFoodEntry> =
        entries.map {
            NewFoodEntry(
                date = date,
                mealSlotId = mealSlotId,
                foodId = it.foodId,
                foodName = it.foodName,
                amount = it.amount,
                portionLabel = it.portionLabel,
                snapshot = NutritionSnapshot(it.kcal, it.proteinG, it.carbsG, it.fatG),
            )
        }
}

/** The entries of meal [mealSlotId] among [yesterdayEntries], to log again on [today] in the same meal. */
class RepeatYesterdayMeal(private val copyEntries: CopyEntries = CopyEntries()) {
    operator fun invoke(
        yesterdayEntries: List<FoodLogEntry>,
        mealSlotId: String,
        today: LocalDate,
    ): List<NewFoodEntry> = copyEntries(yesterdayEntries.filter { it.mealSlotId == mealSlotId }, mealSlotId, today)

    companion object {
        fun yesterdayOf(today: LocalDate): LocalDate = today.minus(1, DateTimeUnit.DAY)
    }
}
