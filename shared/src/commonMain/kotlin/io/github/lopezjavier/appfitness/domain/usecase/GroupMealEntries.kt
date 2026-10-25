package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealRow

/**
 * Turns the entries of a meal (in diary order) into rows: entries sharing a `groupId` within the same meal slot form
 * one [MealRow.Group] placed at its first entry; the rest are [MealRow.Single]. Pure; totals never change.
 */
class GroupMealEntries {
    operator fun invoke(entries: List<FoodLogEntry>): List<MealRow> {
        val byGroup = entries.filter { it.groupId != null }.groupBy { it.mealSlotId to it.groupId }
        val emitted = mutableSetOf<Pair<String, String?>>()
        return entries.mapNotNull { entry ->
            val groupId = entry.groupId
            val key = entry.mealSlotId to groupId
            when {
                groupId == null -> MealRow.Single(entry)

                !emitted.add(key) -> null

                else -> byGroup.getValue(key).let { members ->
                    MealRow.Group(
                        groupId = groupId,
                        name = members.firstNotNullOfOrNull { it.groupName }.orEmpty(),
                        entries = members,
                        totals = DiaryTotals.sum(members),
                    )
                }
            }
        }
    }
}
