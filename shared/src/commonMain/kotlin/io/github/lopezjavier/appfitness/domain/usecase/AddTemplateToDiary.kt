package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository
import kotlinx.datetime.LocalDate

/**
 * Logs every food of a template into meal [mealSlotId] of a day in ONE request. The snapshots come from the foods'
 * current values and, for cooked items, the CURRENT olive oil preferences (read at this moment, ADR-63), so already
 * logged entries are never touched. A comida with only raw items does not read the preferences.
 */
class AddTemplateToDiary(
    private val foodDiary: FoodDiaryRepository,
    private val foodPreferences: FoodPreferencesRepository,
    private val buildCookedEntries: BuildCookedEntries,
) {
    suspend operator fun invoke(
        template: MealTemplate,
        mealSlotId: String,
        date: LocalDate,
    ): Result<List<FoodLogEntry>> {
        if (template.items.isEmpty()) return Result.success(emptyList())
        val prefs = if (buildCookedEntries.needsPreferences(template.items)) {
            foodPreferences.get()
        } else {
            Result.success(FoodPreferences())
        }
        return prefs.fold(
            onSuccess = { foodDiary.addEntries(buildCookedEntries(template.items, mealSlotId, date, it)) },
            onFailure = { Result.failure(it) },
        )
    }
}
